package base.magento1

import base.{AbstractCheckoutActions, CommonBehaviour}
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration.Duration

class CheckoutActions(commonBehaviour: CommonBehaviour,
                      feedProvider: String => FeederBuilder[String],
                      callbacks: Map[String, (String) => ChainBuilder])
  extends AbstractCheckoutActions(callbacks)
{
  def progress(toStep: String): ChainBuilder = {
    exec(
      commonBehaviour.visitPage("Checkout: Progress", "checkout/onepage/progress/")
        .queryParam("toStep", toStep)
    )
  }

  def setCheckoutMethod(method: String): ChainBuilder = {
      execInCallback(
          "checkout_step",
          "checkout_method",
          exec(
            commonBehaviour.createPostRequest(
              "Checkout: Save Checkout Method",
              "checkout/onepage/saveMethod/"
            )
            .formParam("""method""", method)
            .check(status.is(200))
          )
      )
  }

  def saveBillingAddressAsShipping: ChainBuilder = {
    feed(feedProvider("address"))
     .exec(execInCallback(
       "checkout_step",
        "billing",
        exec(commonBehaviour.createPostRequest("Checkout: Save Billing", "checkout/onepage/saveBilling/")
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
          .check(status.is(200)))))
  }

  def saveShippingMethod(method: String): ChainBuilder = {
    execInCallback(
      "checkout_step",
      "shipping_method",
      exec(commonBehaviour.createPostRequest("Checkout: Save Shipping Method", "checkout/onepage/saveShippingMethod/")
        .formParam("""shipping_method""", method)
        .check(status.is(200))
      ))
  }


  def savePayment(method: String): ChainBuilder = {
    execInCallback(
      "checkout_step",
      "payment",
      exec(commonBehaviour.createPostRequest("Checkout: Save Payment Method", "checkout/onepage/savePayment/")
        .formParam("""payment[method]""", method)
        .formParam("""form_key""", "${form_key}")
        .check(status.is(200))
        .check(regex(""""goto_section":"review"""")))
    )
  }

  def placeOrder(paymentMethod: String): ChainBuilder = {
    execInCallback(
      "checkout_step",
      "place",
      exec(commonBehaviour.createPostRequest("Checkout: Place Order", "checkout/onepage/saveOrder/")
        .formParam("""payment[method]""", paymentMethod)
        .formParam("""form_key""", "${form_key}")
        .check(status.is(200)))
    )
  }

  def success: ChainBuilder = {
    execInCallback(
      "onepage",
      "success",
      exec(commonBehaviour.visitPage("Checkout: Success", "checkout/onepage/success/"))
    )
  }

  /**
    * Checkout as Guest
    */
  def asGuest(minPause: Duration, maxPause: Duration) = {
      exec(session => session.set("uuid", java.util.UUID.randomUUID.toString))
      .exec(
        execInCallback("onepage", "view", exec(commonBehaviour.visitPage("Checkout: Onepage", "checkout/onepage/")))
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
