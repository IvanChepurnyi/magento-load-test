package base.magento2

import base.{AbstractCartActions, CommonBehaviour}
import io.gatling.core.Predef._
import io.gatling.core.json.Json
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

class CartActions(commonBehaviour: CommonBehaviour,
                  addToCartUrl: String,
                  addToCartValidation: HttpRequestBuilder => HttpRequestBuilder,
                  callbacks: Map[String, (String) => ChainBuilder])
  extends AbstractCartActions(commonBehaviour, addToCartUrl, addToCartValidation, callbacks)
{

  def viewCart: ChainBuilder = {
    exec(commonBehaviour.updateDefaultProtocol())
        .exec(execInCallback("cart", "view",
          exec(commonBehaviour.visitPage("Shopping Cart: View", commonBehaviour.buildUrl("checkout/cart"))
          .check(status.is(200))
          .check(css("""#shopping-cart-table input[name^="cart"]""", "value").findAll.saveAs("cart_qty_values"))
          .check(css("""#shopping-cart-table input[name^="cart"]""", "name").findAll.saveAs("cart_qty_name"))
          .check(regex(""""quoteData":\{"entity_id":"([^"]+)",""").saveAs("quoteEntityId")))))
  }

  def estimateShippingMethods(postcode: String, region: String, regionId: String) = {
      exec(session => {
        session.set("payload", Json.stringify(Map(
          "address" -> Map(
            "country_id" -> "US",
            "postcode" -> postcode,
            "region" -> region,
            "region_id" -> regionId
          )
        )))
      })
      .exec(commonBehaviour.updateSecureProtocol())
      .exec(
        commonBehaviour
          .createPostRequest(
            "Shopping Cart: Estimate Shipping",
            "rest/default/V1/guest-carts/${quoteEntityId}/estimate-shipping-methods"
          )
          .header("X-Requested-With", "XMLHttpRequest")
          .header("Content-Type", "application/json")
          .body(StringBody("""${payload}""")).asJSON
          .check(status.is(200))
          .check(jsonPath("$..carrier_code"))
      )
  }

  def totalsInformation(postcode: String, region: String, regionId: String) = {
    exec(session => {
      session.set("payload", Json.stringify(Map(
        "addressInformation" -> Map(
          "address" -> Map(
            "country_id" -> "US",
            "postcode" -> postcode,
            "region" -> region,
            "region_id" -> regionId
          ),
          "shipping_carrier_code" -> "flatrate",
          "shipping_method_code" -> "flatrate"
        )
      )))
    })
    .exec(
      commonBehaviour
        .createPostRequest(
          "Shopping Cart: Totals Information",
          "rest/default/V1/guest-carts/${quoteEntityId}/totals-information"
        )
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Content-Type", "application/json")
        .body(StringBody("""${payload}""")).asJSON
        .check(status.is(200))
        .check(jsonPath("$.grand_total"))
    )
  }

  override def buildCartSimpleProductParams(requestBuilder: HttpRequestBuilder): HttpRequestBuilder = {
    super.buildCartSimpleProductParams(requestBuilder)
      .formParam( """selected_configurable_option""", "")
      .formParam( """related_product""", "")
  }
}
