package base.magento2

import base.{AbstractCheckoutActions, CommonBehaviour}
import io.gatling.core.Predef._
import io.gatling.core.json.Json
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration.Duration

class CheckoutActions(commonBehaviour: CommonBehaviour,
                      feedProvider: String => FeederBuilder[String],
                      isFullAddressOnEstimate: Boolean,
                      callbacks: Map[String, (String) => ChainBuilder])
  extends AbstractCheckoutActions(callbacks)
{
  def view = {
    exec(
      commonBehaviour.visitPage("Checkout Page", "checkout/")
        .check(regex("""<title>Checkout</title>"""))
        .check(regex(""""quoteData":\{"entity_id":"([^"]+)",""").saveAs("quoteEntityId"))
    )
  }

  def setEmail = {
    exec(session => {
      val uuid = java.util.UUID.randomUUID.toString
      session.set("customerEmail", uuid + "@example.com")
    })
  }

  def isEmailAvailable = {
    exec(session => {
      val address = new SessionAddress(session)
      session.set("payload", Json.stringify(Map(
        "customerEmail" -> session("customerEmail").as[String]
      )))
    })
    .exec(
      commonBehaviour.createPostRequest("Checkout: Check email", "rest/default/V1/customers/isEmailAvailable")
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Content-Type", "application/json")
        .body(StringBody("""${payload}""")).asJSON
        .check(status.is(200))
        .check(regex("""true"""))
    )
  }

  def prepareAddress = {
    feed(feedProvider("address"))
  }

  def estimateFullShippingMethod() = {
    exec(session => {
      val sessionAdddress = new SessionAddress(session)
      val address = if (isFullAddressOnEstimate) { sessionAdddress.fullAddress } else { sessionAdddress.shortAddress }
      session.set("payload", Json.stringify(
        Map("address" -> address)
      ))
    })
    .exec(
      commonBehaviour
        .createPostRequest(
          "Checkout: Estimate Shipping",
          "rest/default/V1/guest-carts/${quoteEntityId}/estimate-shipping-methods"
        )
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Content-Type", "application/json")
        .body(StringBody("""${payload}""")).asJSON
        .check(status.is(200))
        .check(jsonPath("$..carrier_code"))
    )
  }

  def saveShipping = {
    exec(session => {
      val address = new SessionAddress(session)
      session.set("payload", Json.stringify(Map(
        "addressInformation" -> Map(
          "shipping_address" -> address.fullAddress,
          "billing_address" ->  address.billingAddress,
          "shipping_carrier_code" -> "flatrate",
          "shipping_method_code" -> "flatrate"
        )
      )))
    })
    .exec(
      commonBehaviour
        .createPostRequest(
          "Checkout: Save Shipping Address",
          "rest/default/V1/guest-carts/${quoteEntityId}/shipping-information"
        )
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Content-Type", "application/json")
        .body(StringBody("""${payload}""")).asJSON
        .check(status.is(200))
        .check(jsonPath("$.payment_methods"))
    )
  }

  def placeOrder = {
    exec(session => {
      val address = new SessionAddress(session)
      session.set("payload", Json.stringify(Map(
        "billingAddress" ->  address.billingAddress,
        "cartId" -> session("quoteEntityId").as[String],
        "email" -> session("customerEmail").as[String],
        "paymentMethod" -> Map(
          "additional_data" -> null,
          "method" -> "checkmo",
          "po_number" -> null
        )
      )))
    })
    .exec(
      commonBehaviour
        .createPostRequest(
          "Checkout: Place order",
          "rest/default/V1/guest-carts/${quoteEntityId}/payment-information"
        )
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Content-Type", "application/json")
        .body(StringBody("""${payload}""")).asJSON
        .check(status.is(200))
    )
  }

  def success = {
    exec(
      commonBehaviour.visitPage("Checkout: Success", "checkout/onepage/success/")
        .check(regex("""Your order # is:"""))
    )
  }

  override def asGuest(minPause: Duration, maxPause: Duration): ChainBuilder = {
    exec(prepareAddress)
      .exec(view)
      .pause(minPause, maxPause)
      .exec(estimateFullShippingMethod())
      .pause(minPause, maxPause)
      .exec(setEmail)
      .exec(isEmailAvailable)
      .pause(minPause, maxPause)
      .exec(saveShipping)
      .pause(minPause, maxPause)
      .exec(placeOrder)
      .exec(success)
  }
}
