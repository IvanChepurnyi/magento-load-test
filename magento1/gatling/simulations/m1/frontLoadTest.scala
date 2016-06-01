package m1

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.collection.mutable.StringBuilder

class frontendLoadTest extends Simulation
{
  val httpProtocol = http
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip,deflate,sdch")
    .acceptLanguageHeader("en-US,en;q=0.8")
    .disableFollowRedirect

  val dataDir                 = System.getProperty("dataDir", "m1ce").toString
  val nbUsers                 = System.getProperty("users", "20").toInt
  val nbRamp                  = System.getProperty("ramp", "30").toInt
  val nbDuring                = System.getProperty("during", "10").toInt
  val domain                  = System.getProperty("domain", "m1ce.magecore.com").toString
  val useSecure               = System.getProperty("useSecure", "0").toInt
  val projectName             = System.getProperty("project", "Magento CE 1.9.2.4").toString
  val scenarioSuffix          = " (" + nbUsers.toString + " users over " + nbRamp.toString + " sec during " + nbDuring.toString + " min)"

  val feedAddress             = csv(dataDir + "/address.csv").random
  val feedCustomer            = csv(dataDir + "/customer.csv").circular
  val feedCategory            = csv(dataDir + "/category.csv").random
  val feedLayer               = csv(dataDir + "/layer.csv").random
  val feedProductSimple       = csv(dataDir + "/product_simple.csv").random
  val feedProductGrouped      = csv(dataDir + "/product_grouped.csv").random
  val feedProductConfigurable = csv(dataDir + "/product_configurable.csv").random

  val random                  = new java.util.Random

  /**
    * Initializes new customer session
    */
  val initSession = exec(flushCookieJar)
    .exec(session => session.set("domain", domain))
    .exec(session => session.set("secure", if (useSecure == 1) { "https" } else { "http" }))
    .exec(session => session.set("suffix", ""))
    .exec(session => session.set("rnd", random.nextInt))
    .exec(session => session.set("is_customer", false))

  /**
    * AJAX queries
    */
  object ajax {
    def statusRequest(securePage: Boolean, page: String) = {
      exec(session => {
        session.set("protocol", if (securePage) { session("secure").as[String] } else { "http" })
          .set("rnd", random.nextInt)
      })
      .exec(
        http("AJAX: Status Request")
          .get("${protocol}://${domain}/ajax/status/index/page/"+page+"/?_=${rnd}")
          .header("X-Requested-With", "XMLHttpRequest")
          .check(status.is(200))
          .check(jsonPath("$.customer"))
          .check(jsonPath("$.form_key").saveAs("form_key"))
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
        .exec(ajax.statusRequest(false, "catalog_product/product/${product_id}"))
      }
      def addSimple = {
        exec(viewSimple)
        .exec(
          http("Add Product to Cart: Simple")
            .post("http://${domain}/ajax/cart/add/")
            .header("X-Requested-With", "XMLHttpRequest")
            .formParam( """product""", "${product_id}")
            .formParam( """form_key""", "${form_key}")
            .formParam( """qty""", "1")
            .check(status.is(200))
            .check(jsonPath("$.minicart_message"))
          )
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
        .exec(ajax.statusRequest(false, "catalog_product/product/${product_id}"))
      }
      def addGrouped = {
        exec(viewGrouped)
        .exec(
          http("Add Product to Cart: Grouped")
            .post("http://${domain}/ajax/cart/add/")
            .header("X-Requested-With", "XMLHttpRequest")
            .formParam("""product""", "${product_id}")
            .formParam("""form_key""", "${form_key}")
            .formParam("""qty""", "1")
            .formParamMap(session => {
              val children = session("children").as[String].split(",")
              val childId  = children(random.nextInt(children.length))
              val keys     = children.map(k => "super_group[" + k + "]")
              val values   = children.map(v => if (v == childId) 1 else 0)
              val result   = (keys zip values).toMap
              result
            })
            .check(status.is(200))
            .check(jsonPath("$.minicart_message"))
        )
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
        .exec(ajax.statusRequest(false, "catalog_product/product/${product_id}"))
      }

      def addConfigurable = {
        exec(viewConfigurable)
        .exec(
          http("Add Product to Cart: Configurable")
            .post("http://${domain}/ajax/cart/add/")
            .header("X-Requested-With", "XMLHttpRequest")
            .formParam( """product""", "${product_id}")
            .formParam( """form_key""", "${form_key}")
            .formParam( """qty""", "1")
            .formParamMap(session => {
              val keys = session("options").as[String].split("&").map(k => "super_attribute[" + k.split("=")(0) + "]")
              val values = session("options").as[String].split("&").map(v => v.split("=")(1))
              val result = (keys zip values).toMap
              result
            })
            .check(status.is(200))
            .check(jsonPath("$.minicart_message"))
        )
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
        )
        .exec(ajax.statusRequest(false, "catalog_category/category/${category_id}"))
      }

      def layer = {
        feed(feedLayer)
        .exec(
          http("Category Page (Filtered)")
            .get("http://${domain}/${url}?${attribute}=${option}")
            .check(status.is(200))
            .check(regex(""">Remove This Item</""").find(0).exists)
        )
        .exec(ajax.statusRequest(false, "catalog_category/category/${category_id}"))
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
        .exec(ajax.statusRequest(false, "checkout"))
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
        exec(
          http("Checkout: Save Checkout Method")
            .post("${secure}://${domain}/checkout/onepage/saveMethod/")
            .formParam("""method""", method)
            .check(status.is(200))
        )
      }

      def saveBillingAddressAsShipping = {
        feed(feedAddress)
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
        exec(
          http("Checkout: Save Shipping Method")
            .post("${secure}://${domain}/checkout/onepage/saveShippingMethod/")
            .formParam("""shipping_method""", method)
            .check(status.is(200))
        )
      }

      def savePayment(method: String) = {
        exec(
          http("Checkout: Save Payment Method")
            .post("${secure}://${domain}/checkout/onepage/savePayment/")
            .formParam("""payment[method]""", method)
            .formParam("""form_key""", "${form_key}")
            .check(status.is(200))
            .check(regex(""""goto_section":"review""""))
        )
      }

      def placeOrder(paymentMethod: String) = {
        exec(
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
        .exec(ajax.statusRequest(true, "checkout"))
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
        .exec(ajax.statusRequest(true, "checkout"))
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

  /**
    * Customer behaviors
    */
  object group {
    def minPause = 100 milliseconds
    def maxPause = 500 milliseconds

    def abandonedCart = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
        .pause(minPause, maxPause)
        .exec(catalog.product.addConfigurable)
        .pause(minPause, maxPause)
        .exec(checkout.cart.view)
    }

    def browseCatalog = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewSimple)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewConfigurable)
    }

    def browseLayer = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
        .pause(minPause, maxPause)
        .exec(catalog.category.layer)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewSimple)
        .pause(minPause, maxPause)
        .exec(catalog.product.viewConfigurable)
    }

    def checkoutGuest = {
      exec(initSession)
      .exec(cms.homepage)
      .pause(minPause, maxPause)
      .exec(catalog.category.view)
      .pause(minPause, maxPause)
      .exec(catalog.product.addSimple)
      .pause(minPause, maxPause)
      .exec(catalog.product.addConfigurable)
      .pause(minPause, maxPause)
      .exec(checkout.cart.view)
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
          40d -> exec(group.abandonedCart),
          25d -> exec(group.browseCatalog),
          25d -> exec(group.browseLayer),
          10d -> exec(group.checkoutGuest)
        )
      }
  }
}

class defaultFrontTest extends frontendLoadTest
{
  setUp(scenarios.default
    .inject(rampUsers(nbUsers) over (nbRamp seconds))
    .protocols(httpProtocol))
}
