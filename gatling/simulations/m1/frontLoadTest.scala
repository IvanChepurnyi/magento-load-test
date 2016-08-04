package m1

import org.asynchttpclient.util.Base64
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.collection.mutable.StringBuilder

import io.gatling.http.cookie._
import org.asynchttpclient.uri.Uri

class frontendLoadTest extends Simulation {
  val httpProtocol = http
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip,deflate,sdch")
    .acceptLanguageHeader("en-US,en;q=0.8")
    .disableFollowRedirect

  val dataDir = System.getProperty("dataDir", "m1ce").toString
  val nbUsers = System.getProperty("users", "20").toInt
  val nbRamp = System.getProperty("ramp", "30").toInt
  val nbDuring = System.getProperty("during", "10").toInt
  val domain = System.getProperty("domain", "m1ce.magecore.com").toString
  val useSecure = System.getProperty("useSecure", "0").toInt
  val projectName = System.getProperty("project", "Magento CE 1.9.2.4").toString
  val simpleProductCsv = System.getProperty("simpleProductCsv", "product_simple").toString
  val scenarioSuffix = " (" + nbUsers.toString + " users over " + nbRamp.toString + " sec during " + nbDuring.toString + " min)"

  val feedAddress = csv(dataDir + "/address.csv").random
  val feedCustomer = csv(dataDir + "/customer.csv").circular
  val feedCategory = csv(dataDir + "/category.csv").random
  val feedLayer = csv(dataDir + "/layer.csv").random
  val feedProductSimple = csv(dataDir + "/" + simpleProductCsv + ".csv").random
  val feedProductGrouped = csv(dataDir + "/product_grouped.csv").random
  val feedProductConfigurable = csv(dataDir + "/product_configurable.csv").random

  val random = new java.util.Random


  val majorityCartPercent = simpleProductCsv match {
    case "product_simple_original" => 38d // In original set it is very likely that customer visits simple product
    case "product_simple_large" => 25d // In large database chance of finding simple is lower
    case _ => 20d // In default it is 50% chance to find configurable and simple products
  }

  val minorityCartPercent = simpleProductCsv match {
    case "product_simple_original" => 2d // In original set it is very unlikely that customer visits configurable product
    case "product_simple_large" => 15d // In large database chance of finding configurable is larger
    case _ => 20d // In default it is 50% chance to find configurable and simple products
  }

  val majorityCheckoutPercent = simpleProductCsv match {
    case "product_simple_original" => 9.5d // In original set it is very likely that customer checkouts simple product
    case "product_simple_large" => 6d // In large database checking out simple is lower
    case _ => 5d // In default it is 50% checkout chance of configurable and simple products
  }

  val minorityCheckoutPercent = simpleProductCsv match {
    case "product_simple_original" => 0.5d // In original set it is very unlikely that customer checkouts configurable product
    case "product_simple_large" => 4d // In large database chance of checking out configurable is larger
    case _ => 5d // In default it is 50% checkout chance of configurable and simple products
  }

  val minPause = 100 milliseconds
  val maxPause = 500 milliseconds

  /**
    * Initializes new customer session
    */
  val initSession = exec(flushCookieJar)
    .exec(session => session.set("domain", domain))
    .exec(session => session.set("secure", if (useSecure == 1) {
      "https"
    } else {
      "http"
    }))
    .exec(session => session.set("suffix", ""))
    .exec(session => session.set("rnd", random.nextInt))
    .exec(session => session.set("is_customer", false))
    .exec(session => session.set("form_key", ""))

  val setBackUrl = exec(session => {
    val url = session("url").as[String]
    session.set("uenc", Base64.encode(s"http://${domain}/${url}".toCharArray.map(_.toByte)))
  })


  /**
    * AJAX queries
    */
  object ajax {
    def statusRequest(securePage: Boolean, page: String) = {
      exec(session => {
        session.set("protocol", if (securePage) {
          session("secure").as[String]
        } else {
          "http"
        })
          .set("rnd", random.nextInt)
      })
        .exec(
          http("AJAX: Status Request")
            .get("${protocol}://${domain}/ajax/status/index/page/" + page + "/?_=${rnd}")
            .header("X-Requested-With", "XMLHttpRequest")
            .check(status.is(200))
            .check(jsonPath("$.customer"))
            .check(jsonPath("$.form_key").saveAs("form_key"))
        )
    }

    /**
      * Reloads block by its name
      *
      * @param securePage
      * @param block
      */
    def reloadBlockRequest(securePage: Boolean, block: String) = {
      exec(session => {
        session.set("protocol", if (securePage) {
          session("secure").as[String]
        } else {
          "http"
        })
      })
        .exec(
          http("AJAX: Reload Dynamic Block")
            .post("${protocol}://${domain}/varnish/ajax/reload/")
            .header("X-Requested-With", "XMLHttpRequest")
            .formParam("blocks", block)
            .check(status.is(200))
            .check(jsonPath("$." + block))
        )
    }

    /**
      * Sets a form key cookie and session variable
      *
      * @param securePage
      */
    def formkeyRequest(securePage: Boolean) = {
      doIf(session => session("form_key").as[String] == "") {
        exec(session => {
          session.set("protocol", if (securePage) {
            session("secure").as[String]
          } else {
            "http"
          })
        })
          .exec(
            http("AJAX: Init Form Key")
              .get("${protocol}://${domain}/varnish/ajax/token/")
              .header("X-Requested-With", "XMLHttpRequest")
              .check(status.is(200))
          )
      }
        .exec(extractFormKey)
    }

    // EcomDev_Varnish extension is using cookie based CSRF protection with signing request
    def extractFormKey() = {
      exec(session => {
        session.set(
          "form_key",
          session("gatling.http.cookies").as[CookieJar]
            .get(Uri.create("http://" + session("domain").as[String] + "/"))
            .find(_.getName == "varnish_token")
            .getOrElse(null).getValue
        )
      })
    }

    def messagesRequest(securePage: Boolean, storage: String) = {
      exec(session => {
        session.set("protocol", if (securePage) {
          session("secure").as[String]
        } else {
          "http"
        })
      })
        .exec(
          http("AJAX: Session Messages")
            .get("${protocol}://${domain}/varnish/ajax/message/")
            .header("X-Requested-With", "XMLHttpRequest")
            .check(status.is(200))
        )
    }
  }

  /**
    * CMS pages
    */
  object cms {
    def homepage = {
      exec(
        http("Home Page")
          .get("http://${domain}/")
          .check(status.is(200))
          .check(regex( """<title>Home page</title>"""))
      )
    }
  }

  /**
    * Catalog pages
    */
  object catalog {

    object product {
      /**
        * Simple Product
        */
      def viewSimple = {
        feed(feedProductSimple)
          .exec(
            http("Product Page: Simple")
              .get("http://${domain}/${url}")
              .check(status.is(200))
              .check(regex("<div class=\"product-name\">"))
          )
          .pause(minPause, maxPause)
          .exec(setBackUrl)
      }

      def addSimple = {
        exec(viewSimple)
          .exec(ajax.formkeyRequest(false))
          .exec(
            http("Add Product to Cart: Simple")
              .post("http://${domain}/checkout/cart/add/uenc/${uenc}")
              .formParam( """product""", "${product_id}")
              .formParam( """form_key""", "${form_key}")
              .formParam( """qty""", "1")
              .check(status.is(302))
              .check(header("Location").is("http://${domain}/checkout/cart/"))
          )
          // After adding to the shopping cart
          // We are redirected to shopping cart page
          .exec(checkout.cart.view)
          .pause(minPause, maxPause)
          .exec(ajax.messagesRequest(false, "checkout"))
          .exec(ajax.reloadBlockRequest(false, "minicart_head"))
      }

      /**
        * Grouped Product
        */
      def viewGrouped = {
        feed(feedProductGrouped)
          .exec(
            http("Product Page: Grouped")
              .get("http://${domain}/${url}")
              .check(status.is(200))
              .check(regex("<div class=\"product-name\">"))
          )
          .pause(minPause, maxPause)
          .exec(setBackUrl)
      }

      def addGrouped = {
        exec(viewGrouped)
          .exec(ajax.formkeyRequest(false))
          .exec(
            http("Add Product to Cart: Grouped")
              .post("http://${domain}/checkout/cart/add/uenc/${uenc}/")
              .formParam( """product""", "${product_id}")
              .formParam( """form_key""", "${form_key}")
              .formParam( """qty""", "1")
              .formParamMap(session => {
                val children = session("children").as[String].split(",")
                val childId = children(random.nextInt(children.length))
                val keys = children.map(k => "super_group[" + k + "]")
                val values = children.map(v => if (v == childId) 1 else 0)
                val result = (keys zip values).toMap
                result
              })
              .check(status.is(302))
              .check(header("Location").is("http://${domain}/checkout/cart/"))
          )
          // After adding to the shopping cart
          // We are redirected to shopping cart page
          .exec(checkout.cart.view)
          .pause(minPause, maxPause)
          .exec(ajax.messagesRequest(false, "checkout"))
          .exec(ajax.reloadBlockRequest(false, "minicart_head"))
      }

      /**
        * Configurable Product
        */
      def viewConfigurable = {
        feed(feedProductConfigurable)
          .exec(
            http("Product Page: Configurable")
              .get("http://${domain}/${url}")
              .check(status.is(200))
              .check(regex("<div class=\"product-name\">"))
          )
          .pause(minPause, maxPause)
          .exec(setBackUrl)
      }

      def addConfigurable = {
        exec(viewConfigurable)
          .exec(ajax.formkeyRequest(false))
          .exec(
            http("Add Product to Cart: Configurable")
              .post("http://${domain}/checkout/cart/add/uenc/${uenc}/")
              .formParam( """product""", "${product_id}")
              .formParam( """form_key""", "${form_key}")
              .formParam( """qty""", "1")
              .formParamMap(session => {
                val keys = session("options").as[String].split("&").map(k => "super_attribute[" + k.split("=")(0) + "]")
                val values = session("options").as[String].split("&").map(v => v.split("=")(1))
                val result = (keys zip values).toMap
                result
              })
              .check(status.is(302))
              .check(header("Location").is("http://${domain}/checkout/cart/"))
          )
          // After adding to the shopping cart
          // We are redirected to shopping cart page
          .exec(checkout.cart.view)
          .pause(minPause, maxPause)
          .exec(ajax.messagesRequest(false, "checkout"))
          .exec(ajax.reloadBlockRequest(false, "minicart_head"))
      }
    }

    object category {
      def view = {
        feed(feedCategory)
          .exec(
            http("Category Page")
              .get("http://${domain}/${url}")
              .check(status.is(200))
              .check(regex("""page-title category-title"""))
              .check(currentLocation.saveAs("categoryUrl"))
          )
      }

      def back = {
        exec(
          http("Category Page")
            .get("${categoryUrl}")
            .check(status.is(200))
            .check(regex("""page-title category-title"""))
        )
      }

      def layer = {
        feed(feedLayer)
          .exec(
            http("Category Page (Filtered)")
              .get("http://${domain}/${url}?${attribute}=${option}")
              .check(status.is(200))
              .check(regex(""">Remove This Item</""").find(0).exists)
              .check(currentLocation.saveAs("categoryUrl"))
          )
      }
    }

  }

  /**
    * Customer
    */
  object customer {
    def login = {
      feed(feedCustomer)
        .exec(
          http("Customer: Login or Create an Account Form")
            .get("http://${domain}/customer/account/login/")
            .check(status.is(200))
            .check(regex("""name="form_key".*value="(.{16})"""").saveAs("form_key"))
        )
        .exitBlockOnFail(
          exec(
            http("Customer: Login action")
              .post("http://${domain}/customer/account/loginPost/")
              .formParam("""form_key""", "${form_key}")
              .formParam("""login[username]""", "${email}")
              .formParam("""login[password]""", "${password}")
              .formParam("""send""", "")
              .check(status.is(302))
              .check(headerRegex("Location", """customer/account"""))
          )
        )
        .exec(session => session.set("is_customer", "1"))
    }

    def logout = {
      doIf(session => session("is_customer").as[Boolean]) {
        exec(
          http("Customer: Logout")
            .get("http://${domain}/customer/account/logout/")
            .check(status.is(302))
            .check(headerRegex("Location", "logoutSuccess"))
        )
          .exec(
            http("Customer: Logout Page")
              .get("${secure}://${domain}/customer/account/logoutSuccess/")
              .check(status.is(200))
          )
      }
    }
  }

  object checkout {

    /**
      * Shopping Cart
      */
    object cart {
      def view = {
        exec(
          http("Shopping Cart Page")
            .get("http://${domain}/checkout/cart/")
            .check(status.is(200))
            .check(css( """#shopping-cart-table input[name^="cart"]""", "value").findAll.saveAs("cart_qty_values"))
            .check(css( """#shopping-cart-table input[name^="cart"]""", "name").findAll.saveAs("cart_qty_name"))
        )
      }
    }

    /**
      * Onepage checkout steps
      */
    object onepage {
      def progress(toStep: String) = {
        exec(
          http("Checkout: Progress")
            .get("http://${domain}/checkout/onepage/progress/")
            .queryParam("toStep", toStep)
            .check(status.is(200))
        )
      }

      def setCheckoutMethod(method: String) = {
        exec(ajax.formkeyRequest(false))
          .exec(
            http("Checkout: Save Checkout Method")
              .post("${secure}://${domain}/checkout/onepage/saveMethod/")
              .formParam("""method""", method)
              .check(status.is(200))
          )
      }

      def saveBillingAddressAsShipping = {
        feed(feedAddress)
          .exec(ajax.formkeyRequest(false))
          .exec(
            http("Checkout: Save Billing")
              .post("${secure}://${domain}/checkout/onepage/saveBilling/")
              .formParam("""billing[firstname]""", "${firstname}")
              .formParam("""billing[lastname]""", "${lastname}")
              .formParam("""billing[company]""", "")
              .formParam("""billing[email]""", "${uuid}@example.com")
              .formParam("""billing[street][]""", "${street}")
              .formParam("""billing[street][]""", "")
              .formParam("""billing[city]""", "${city}")
              .formParam("""billing[region_id]""", "${region_id}")
              .formParam("""billing[region]""", "${region}")
              .formParam("""billing[postcode]""", "${postcode}")
              .formParam("""billing[country_id]""", "US")
              .formParam("""billing[telephone]""", "${telephone}")
              .formParam("""billing[fax]""", "")
              .formParam("""billing[customer_password]""", "")
              .formParam("""billing[confirm_password]""", "")
              .formParam("""billing[use_for_shipping]""", "1")
              .formParam("""billing[save_in_address_book]""", "1")
              .check(status.is(200))
          )
      }

      def saveShippingMethod(method: String) = {
        exec(ajax.formkeyRequest(false))
          .exec(
            http("Checkout: Save Shipping Method")
              .post("${secure}://${domain}/checkout/onepage/saveShippingMethod/")
              .formParam("""shipping_method""", method)
              .check(status.is(200))
          )
      }

      def savePayment(method: String) = {
        exec(ajax.formkeyRequest(false))
          .exec(
            http("Checkout: Save Payment Method")
              .post("${secure}://${domain}/checkout/onepage/savePayment/")
              .formParam("""payment[method]""", method)
              .formParam("""form_key""", "${form_key}")
              .check(status.is(200))
              .check(regex(""""goto_section":"review""""))
          )
      }

      def placeOrder(paymentMethod: String) = {
        exec(ajax.formkeyRequest(false))
          .exec(
            http("Checkout: Place Order")
              .post("${secure}://${domain}/checkout/onepage/saveOrder/")
              .formParam("""payment[method]""", paymentMethod)
              .formParam("""form_key""", "${form_key}")
              .check(status.is(200))
          )
      }

      def success = {
        exec(
          http("Checkout: Success")
            .get("${secure}://${domain}/checkout/onepage/success/")
            .check(status.is(200))
        )
          .pause(minPause, maxPause)
          .exec(ajax.messagesRequest(false, "checkout"))
          .exec(ajax.reloadBlockRequest(false, "minicart_head"))
      }

      /**
        * Checkout as Guest
        */
      def asGuest(minPause: Duration, maxPause: Duration) = {
        exec(session => {
          session.set("uuid", java.util.UUID.randomUUID.toString)
        })
          .exec(
            http("Onepage Checkout")
              .get("${secure}://${domain}/checkout/onepage/")
              .check(status.is(200))
          )
          .pause(minPause, maxPause)
          .exec(setCheckoutMethod("guest"))
          .exec(progress("billing"))
          .pause(minPause, maxPause)
          .exec(saveBillingAddressAsShipping)
          .exec(progress("shipping_method"))
          .pause(minPause, maxPause)
          .exec(saveShippingMethod("flatrate_flatrate"))
          .exec(progress("payment"))
          .pause(minPause, maxPause)
          .exec(savePayment("checkmo"))
          .exec(progress("review"))
          .pause(minPause, maxPause)
          .exitBlockOnFail(exec(placeOrder("checkmo")))
          .exec(success)
      }
    }

  }

  object catalogBehaviour {
    def browseCategory = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
        .exec(ajax.formkeyRequest(false))
    }

    def browseCatalog = {
      exec(browseCategory)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewConfigurable)
        .pause(minPause, maxPause)
        .exec(catalog.category.back)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewSimple)
    }

    def browseLayer = {
      exec(browseCategory)
        .pause(minPause, maxPause)
        .exec(catalog.category.layer)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewConfigurable)
        .pause(minPause, maxPause)
        .exec(catalog.category.back)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewSimple)
    }
  }

  /**
    * Customer behaviors
    */
  object checkoutBehaviour {

    def abandonedCartSimpleAndConfigurable = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
        .exec(ajax.formkeyRequest(false))
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
        // In order to add second product
        // a user must go to a category page,
        // as he stays in shopping cart
        .pause(minPause, maxPause)
        .exec(catalog.category.back)
        .pause(minPause, maxPause)
        .exec(catalog.product.addConfigurable)
    }

    def abandonedCartTwoSimples = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
        .exec(ajax.formkeyRequest(false))
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
        // In order to add second product
        // a user must go to a category page,
        // as he stays in shopping cart
        .pause(minPause, maxPause)
        .exec(catalog.category.back)
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
    }


    def abandonedCartMajority = {
      abandonedCartTwoSimples
    }

    def abandonedCartMinority = {
      abandonedCartSimpleAndConfigurable
    }

    def checkoutGuestMajority = {
      exec(abandonedCartMajority)
        .pause(minPause, maxPause)
        .exec(checkout.onepage.asGuest(minPause, maxPause))
    }

    def checkoutGuestMinority = {
      exec(abandonedCartMinority)
        .pause(minPause, maxPause)
        .exec(checkout.onepage.asGuest(minPause, maxPause))
    }
  }

  /**
    * Scenarios
    */
  object scenarios {
    def default = scenario(projectName + " Load Test" + scenarioSuffix)
      .during(nbDuring minutes) {
        randomSwitch(
          minorityCartPercent -> exec(checkoutBehaviour.abandonedCartMinority),
          majorityCartPercent -> exec(checkoutBehaviour.abandonedCartMajority),
          25d -> exec(catalogBehaviour.browseCatalog),
          25d -> exec(catalogBehaviour.browseLayer),
          minorityCheckoutPercent -> exec(checkoutBehaviour.checkoutGuestMinority),
          majorityCheckoutPercent -> exec(checkoutBehaviour.checkoutGuestMajority)
        )
      }
  }

}

class defaultFrontTest extends frontendLoadTest {
  setUp(scenarios.default
    .inject(rampUsers(nbUsers) over (nbRamp seconds))
    .protocols(httpProtocol))
}
