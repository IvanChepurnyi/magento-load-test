package m2

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.collection.mutable.StringBuilder

class frontendLoadTest extends Simulation {
  val httpProtocol = http
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip,deflate,sdch")
    .acceptLanguageHeader("en-US,en;q=0.8")
    .disableFollowRedirect

  val dataDir = System.getProperty("dataDir", "m2ce").toString
  val nbUsers = System.getProperty("users", "20").toInt
  val nbRamp = System.getProperty("ramp", "30").toInt
  val nbDuring = System.getProperty("during", "10").toInt
  val domain = System.getProperty("domain", "m2ce.magecore.com").toString
  val useSecure = System.getProperty("useSecure", "0").toInt
  val magentoVersion = System.getProperty("magentoVersion", "2.0.7").toString

  val isAjaxReviewRandomParam = magentoVersion match {
    case "2.0.0" => true
    case "2.0.1" => true
    case "2.0.2" => true
    case "2.0.3" => true
    case "2.0.4" => true
    case "2.0.5" => true
    case "2.0.6" => true
    case "2.0.7" => true
    case _ => false
  }

  val simpleProductCsv = System.getProperty("simpleProductCsv", "product_simple").toString
  val projectName = System.getProperty("project", "Magento " + magentoVersion + " CE " + simpleProductCsv).toString
  val executeAjaxReview = System.getProperty("ajaxReview", "1").toInt
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
    * Generates Magento form_key
    *
    * @return
    */
  def generateFormKey: String = {
    val count = 16
    val word = new StringBuilder
    val pattern = """0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ""".toList
    val pLength = pattern.length

    for (i <- 0 to count - 1) {
      word.append(pattern(random.nextInt(pLength)))
    }

    word.toString
  }

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
    .exec(session => session.set("firstRequest", true))
    .exec(session => {
      val form_key = generateFormKey
      session.set("form_key", form_key)
    })
    .exec(addCookie(Cookie("form_key", "${form_key}").withDomain(domain)))

  /**
    * AJAX requests
    */
  object ajax {
    def firstRequest = {
      doIf(session => session("firstRequest").as[Boolean]) {
        exec(
          http("AJAX: Init Request")
            .get("http://${domain}/customer/section/load/?sections=&update_section_id=false&_=${rnd}")
            .header("X-Requested-With", "XMLHttpRequest")
            .check(status.is(200))
            .check(jsonPath("$.directory-data"))
        )
          .exec(session => {
            session.set("firstRequest", false)
          })
      }
    }

    def loadSections(sections: String, securePage: Boolean) = {
      exec(session => {
        session.set("protocol", if (securePage) {
          session("secure").as[String]
        } else {
          "http"
        })
      })
        .exec(session => session.set("rnd", random.nextInt))
        .exec(
          http("AJAX: Load Sections")
            .get("${protocol}://${domain}/customer/section/load/?sections=" + sections + "&update_section_id=false&_=${rnd}")
            .header("X-Requested-With", "XMLHttpRequest")
            .check(status.is(200))
        )
    }

    def quoteJsonValue(value: String): String = {
      var result = ""
      if (value == "") {
        result = "null"
      } else {
        result = "\"" + value.replace("\"", "\\\"") + "\""
      }

      result
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
      def reviewAjax(productId: String) = {
        doIf(session => executeAjaxReview == 1) {
          exec(session => session.set("rnd", random.nextInt))
            .exec(
              http("Product Page: Review AJAX")
                .get(
                  "http://${domain}/review/product/listAjax/id/" + productId
                    + "/" + (if (isAjaxReviewRandomParam) {
                    "?_=${rnd}"
                  } else {
                    ""
                  })
                )
                .header("X-Requested-With", "XMLHttpRequest")
                .check(status.is(200)
                )
            )
        }
      }

      /**
        * Simple Product
        */
      def viewSimple = {
        feed(feedProductSimple)
          .exec(
            http("Product Page: Simple")
              .get("http://${domain}/${url}")
              .check(status.is(200))
              .check(regex("<span.*?itemprop=\"name\".*?>.*?</span>"))
          )
          .exec(reviewAjax("${product_id}"))
      }

      def addSimple = {
        exec(viewSimple)
          .exec(
            http("Add Product to Cart: Simple")
              .post("http://${domain}/checkout/cart/add/product/${product_id}/")
              .header("X-Requested-With", "XMLHttpRequest")
              .formParam( """product""", "${product_id}")
              .formParam( """form_key""", "${form_key}")
              .formParam( """qty""", "1")
              .formParam( """selected_configurable_option""", "")
              .formParam( """related_product""", "")
              .check(status.is(200))
              .check(regex("""\[\]"""))
          )
          .exec(ajax.loadSections("cart%2Cmessages", false))
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
              .check(regex("<span.*?itemprop=\"name\".*?>.*?</span>"))
          )
          .exec(reviewAjax("${product_id}"))
      }

      def addGrouped = {
        exec(viewGrouped)
          .exec(
            http("Add Product to Cart: Grouped")
              .post("http://${domain}/checkout/cart/add/")
              .header("X-Requested-With", "XMLHttpRequest")
              .formParam("""product""", "${product_id}")
              .formParam("""form_key""", "${form_key}")
              .formParam("""qty""", "1")
              .formParamMap(session => {
                val children = session("children").as[String].split(",")
                val childId = children(random.nextInt(children.length))
                val keys = children.map(k => "super_group[" + k + "]")
                val values = children.map(v => if (v == childId) 1 else 0)
                val result = (keys zip values).toMap
                result
              })
              .check(status.is(200))
              .check(regex("""\[\]"""))
          )
          .exec(ajax.loadSections("cart%2Cmessages", false))
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
              .check(regex("<span.*?itemprop=\"name\".*?>.*?</span>"))
          )
          .exec(reviewAjax("${product_id}"))
      }

      def addConfigurable = {
        exec(viewConfigurable)
          .exec(
            http("Add Product to Cart: Configurable")
              .post("http://${domain}/checkout/cart/add/")
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
              .check(regex("""\[\]"""))
          )
          .exec(ajax.loadSections("cart%2Cmessages", false))
      }
    }

    object category {
      def view = {
        feed(feedCategory)
          .exitBlockOnFail(
            exec(
              http("Category Page")
                .get("http://${domain}/${url}")
                .check(status.is(200))
                .check(currentLocation.saveAs("categoryUrl"))
            )
          )
      }

      def back = {
        exec(
          http("Category Page")
            .get("${categoryUrl}")
            .check(status.is(200))
        )
      }

      def layer = {
        feed(feedLayer)
          .exec(
            http("Category Page (Filtered)")
              .get("http://${domain}/${url}?${attribute}=${option}")
              .check(status.is(200))
              .check(regex("""<span>Remove This Item</span>""").find(0).exists)
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
            .get("${secure}://${domain}/customer/account/login/")
            .check(status.is(200))
        )
        .exec(ajax.loadSections("", true))
        .exitBlockOnFail(
          exec(
            http("Customer: Login action")
              .post("${secure}://${domain}/customer/account/loginPost/")
              .formParam("""form_key""", "${form_key}")
              .formParam("""login[username]""", "${email}")
              .formParam("""login[password]""", "${password}")
              .formParam("""send""", "")
              .check(status.is(302))
              .check(headerRegex("Location", """customer/account"""))
          )
        )
        .exec(session => session.set("is_customer", true))
    }

    def logout = {
      doIf(session => session("is_customer").as[Boolean]) {
        exec(session => session.set("is_customer", false))
          .exec(
            http("Customer: Logout Action")
              .get("${secure}://${domain}/customer/account/logout/")
              .check(status.is(302))
              .check(headerRegex("Location", "logoutSuccess"))
          )
          .exec(
            http("Customer: Logout Page")
              .get("${secure}://${domain}/customer/account/logoutSuccess/")
              .check(status.is(200))
          )
          .exec(ajax.loadSections("", true))
      }
    }
  }

  object checkout {

    /**
      * Shopping cart
      */
    object cart {
      def view = {
        exec(
          http("Shopping Cart Page")
            .get("${secure}://${domain}/checkout/cart/")
            .check(status.is(200))
            .check(css("""#shopping-cart-table input[name^="cart"]""", "value").findAll.saveAs("cart_qty_values"))
            .check(css("""#shopping-cart-table input[name^="cart"]""", "name").findAll.saveAs("cart_qty_name"))
            .check(regex(""""quoteData":\{"entity_id":"([^"]+)",""").saveAs("quoteEntityId"))
        )
          .exec(ajax.loadSections("directory-data", false))
      }

      def estimateShippingMethods(postcode: String, region: String, regionId: String) = {
        exec(session => {
          val payload = "{\"address\":{\"country_id\":\"US\",\"postcode\":" +
            ajax.quoteJsonValue(postcode) + ",\"region\":" + ajax.quoteJsonValue(region) +
            ",\"region_id\":" + ajax.quoteJsonValue(regionId) + "}}"
          session.set("payload", payload)
        })
          .exec(
            http("Shopping Cart: Estimate Shipping")
              .post("${secure}://${domain}/rest/default/V1/guest-carts/${quoteEntityId}/estimate-shipping-methods")
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Content-Type", "application/json")
              .body(StringBody("""${payload}""")).asJSON
              .check(status.is(200))
              .check(jsonPath("$..carrier_code"))
          )
      }

      def totalsInformation(postcode: String, region: String, regionId: String) = {
        exec(session => {
          val payload = "{\"addressInformation\":{\"address\":{\"country_id\":\"US\",\"postcode\":" +
            ajax.quoteJsonValue(postcode) + ",\"region\":" + ajax.quoteJsonValue(region) +
            ",\"region_id\":" + ajax.quoteJsonValue(regionId) + "}, \"shipping_carrier_code\":\"flatrate\", \"shipping_method_code\":\"flatrate\"}}"
          session.set("payload", payload)
        })
          .exec(
            http("Shopping Cart: Totals Information")
              .post("${secure}://${domain}/rest/default/V1/guest-carts/${quoteEntityId}/totals-information")
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Content-Type", "application/json")
              .body(StringBody("""${payload}""")).asJSON
              .check(status.is(200))
              .check(jsonPath("$.grand_total"))
          )
      }

    }

    /**
      * Onepage Checkout steps
      */
    object onepage {
      def view = {
        exec(
          http("Checkout Page")
            .get("${secure}://${domain}/checkout/")
            .check(regex("""<title>Checkout</title>"""))
            .check(regex(""""quoteData":\{"entity_id":"([^"]+)",""").saveAs("quoteEntityId"))
        )
      }

      def estimateShippingMethods(postcode: String, region: String, regionId: String) = {
        exec(session => {
          val payload = "{\"address\":{\"country_id\":\"US\",\"postcode\":" +
            ajax.quoteJsonValue(postcode) + ",\"region\":" + ajax.quoteJsonValue(region) +
            ",\"region_id\":" + ajax.quoteJsonValue(regionId) + "}}"
          session.set("payload", payload)
        })
          .exec(
            http("Checkout: Estimate Shipping")
              .post("${secure}://${domain}/rest/default/V1/guest-carts/${quoteEntityId}/estimate-shipping-methods")
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Content-Type", "application/json")
              .body(StringBody("""${payload}""")).asJSON
              .check(status.is(200))
              .check(jsonPath("$..carrier_code"))
          )
      }

      def setEmail = {
        exec(session => {
          val uuid = java.util.UUID.randomUUID.toString
          session.set("customerEmail", uuid + "@example.com")
        })
      }

      def isEmailAvailable = {
        exec(
          http("Checkout: Check email")
            .post("${secure}://${domain}/rest/default/V1/customers/isEmailAvailable")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/json")
            .body(StringBody("{\"customerEmail\":" + ajax.quoteJsonValue("${customerEmail}") + "}")).asJSON
            .check(status.is(200))
            .check(regex("""true"""))
        )
      }

      def prepareAddress = {
        feed(feedAddress)
      }

      def estimateFullShippingMethod() = {
        exec(session => {
          val address = if (isAjaxReviewRandomParam) {
            "\"country_id\":\"US\",\"postcode\":" +
              ajax.quoteJsonValue(session("postcode").as[String]) +
              ",\"region\":" + ajax.quoteJsonValue(session("region").as[String]) +
              ",\"region_id\":" + ajax.quoteJsonValue(session("region_id").as[String])
          } else {
            "\"city\":" + ajax.quoteJsonValue(session("city").as[String]) +
              ",\"company\":\"\",\"countryId\":\"US\",\"firstname\":" +
              ajax.quoteJsonValue(session("firstname").as[String]) +
              ",\"lastname\":" + ajax.quoteJsonValue(session("lastname").as[String]) +
              ",\"postcode\":" + ajax.quoteJsonValue(session("postcode").as[String]) +
              ",\"region\":" + ajax.quoteJsonValue(session("region").as[String]) +
              ",\"regionId\":" + ajax.quoteJsonValue(session("region_id").as[String]) +
              ",\"street\":[" + ajax.quoteJsonValue(session("street").as[String]) +
              "],\"telephone\":" + ajax.quoteJsonValue(session("telephone").as[String])
          }

          session.set("payload", "{\"address\":{" + address + "}}")
        })
          .exec(
            http("Checkout: Estimate Shipping")
              .post("${secure}://${domain}/rest/default/V1/guest-carts/${quoteEntityId}/estimate-shipping-methods")
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Content-Type", "application/json")
              .body(StringBody("""${payload}""")).asJSON
              .check(status.is(200))
              .check(jsonPath("$..carrier_code"))
          )
      }

      def saveShipping = {
        exec(session => {
          val address = "{\"city\":" + ajax.quoteJsonValue(session("city").as[String]) +
            ",\"company\":\"\",\"countryId\":\"US\",\"firstname\":" + ajax.quoteJsonValue(session("firstname").as[String]) +
            ",\"lastname\":" + ajax.quoteJsonValue(session("lastname").as[String]) +
            ",\"postcode\":" + ajax.quoteJsonValue(session("postcode").as[String]) +
            ",\"region\":" + ajax.quoteJsonValue(session("region").as[String]) +
            ",\"regionId\":" + ajax.quoteJsonValue(session("region_id").as[String]) +
            ",\"street\":[" + ajax.quoteJsonValue(session("street").as[String]) +
            "],\"telephone\":" + ajax.quoteJsonValue(session("telephone").as[String])
          val payload = "{\"addressInformation\":{\"shipping_address\":" + address + "},\"billing_address\":" +
            address + ",\"saveInAddressBook\":null},\"shipping_carrier_code\":\"flatrate\"," +
            "\"shipping_method_code\":\"flatrate\"}}"

          session.set("payload", payload)
        })
          .exec(
            http("Checkout: Save Shipping Address")
              .post("${secure}://${domain}/rest/default/V1/guest-carts/${quoteEntityId}/shipping-information")
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Content-Type", "application/json")
              .body(StringBody("""${payload}""")).asJSON
              .check(status.is(200))
              .check(jsonPath("$.payment_methods"))
          )
      }

      def placeOrder = {
        exec(session => {
          val address = "{\"city\":" + ajax.quoteJsonValue(session("city").as[String]) +
            ",\"company\":\"\",\"countryId\":\"US\",\"firstname\":" + ajax.quoteJsonValue(session("firstname").as[String]) +
            ",\"lastname\":" + ajax.quoteJsonValue(session("lastname").as[String]) +
            ",\"postcode\":" + ajax.quoteJsonValue(session("postcode").as[String]) +
            ",\"region\":" + ajax.quoteJsonValue(session("region").as[String]) +
            ",\"regionId\":" + ajax.quoteJsonValue(session("region_id").as[String]) +
            ",\"street\":[" + ajax.quoteJsonValue(session("street").as[String]) +
            "],\"telephone\":" + ajax.quoteJsonValue(session("telephone").as[String]) +
            ",\"saveInAddressBook\":null}"
          val payload = "{\"billingAddress\":" + address +
            ",\"cartId\":" + ajax.quoteJsonValue(session("quoteEntityId").as[String]) +
            ",\"email\":" + ajax.quoteJsonValue(session("customerEmail").as[String]) +
            ",\"paymentMethod\":{\"additional_data\":null,\"method\":\"checkmo\",\"po_number\":null}}"

          session.set("payload", payload)
        })
          .exec(
            http("Checkout: Place order")
              .post("${secure}://${domain}/rest/default/V1/guest-carts/${quoteEntityId}/payment-information")
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Content-Type", "application/json")
              .body(StringBody("""${payload}""")).asJSON
              .check(status.is(200))
          )
      }

      def success = {
        exec(
          http("Checkout: Success")
            .get("${secure}://${domain}/checkout/onepage/success/")
            .check(status.is(200))
            .check(regex("""Your order # is:"""))
        )
          .exec(ajax.loadSections("cart%2Cmessages", true))
      }
    }

  }

  object catalogBehaviour {
    def browseCategory = {
      exec(initSession)
        .exec(cms.homepage)
        .pause(minPause, maxPause)
        .exec(catalog.category.view)
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
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
        // In order to add second product
        // a user must go to a category page,
        // as he stays in product page
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
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
        // In order to add second product
        // a user must go to a category page,
        // as he stays in product page
        .pause(minPause, maxPause)
        .exec(catalog.category.back)
        .pause(minPause, maxPause)
        .exec(catalog.product.addSimple)
    }

    def abandonedCartMajority = {
      exec(abandonedCartTwoSimples)
    }

    def abandonedCartMinority = {
      exec(abandonedCartSimpleAndConfigurable)
    }

    def checkoutGuestMajority = {
      exec(abandonedCartMajority)
        .pause(minPause, maxPause)
        .exec(checkoutFlow)
    }

    def checkoutGuestMinority = {
      exec(abandonedCartMinority)
        .pause(minPause, maxPause)
        .exec(checkoutFlow)
    }


    def checkoutFlow = {
      exec(checkout.onepage.view)
        .pause(minPause, maxPause)
        .exec(checkout.onepage.estimateShippingMethods("", "", "0"))
        .pause(minPause, maxPause)
        .exec(checkout.onepage.setEmail)
        .exec(checkout.onepage.isEmailAvailable)
        .pause(minPause, maxPause)
        .exec(checkout.onepage.prepareAddress)
        .exec(checkout.onepage.estimateFullShippingMethod())
        .pause(minPause, maxPause)
        .exec(checkout.onepage.saveShipping)
        .pause(minPause, maxPause)
        .exec(checkout.onepage.placeOrder)
        .exec(checkout.onepage.success)
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
