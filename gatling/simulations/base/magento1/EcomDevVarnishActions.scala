package base.magento1

import base.CommonBehaviour
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.cookie.CookieJar
import io.gatling.http.request.builder.HttpRequestBuilder
import org.asynchttpclient.uri.Uri

class EcomDevVarnishActions(commonBehaviour: CommonBehaviour, minicartBlockName: String, redirectToCart: Boolean) {

  def reloadBlockRequest(block: String) : HttpRequestBuilder = {
    commonBehaviour.createPostRequest("EcomDev Varnish: Reload Blocks", "varnish/ajax/reload")
      .header("X-Requested-With", "XMLHttpRequest")
      .formParam("blocks", block)
      .check(status.is(200))
      .check(jsonPath("$." + block))
  }

  def resetFormKey: ChainBuilder = {
    exec(session => session.set("form_key", ""))
  }

  def formkeyRequest : ChainBuilder = {
    doIf(session => session("form_key").as[String] == "") {
      exec(
        commonBehaviour.visitPage("EcomDev Varnish: Init Form Key", "varnish/ajax/token/")
          .header("X-Requested-With", "XMLHttpRequest")
          .check(status.is(200))
      )
      .exec(extractFormKey)
    }
  }

  // EcomDev_Varnish extension is using cookie based CSRF protection with signing request
  def extractFormKey: ChainBuilder = {
    exec(session => session.set(
      "form_key",
      session("gatling.http.cookies").as[CookieJar]
        .get(Uri.create(commonBehaviour.fullHomeUrl))
          .find(_.getName == "varnish_token")
          .get.getValue)
    )
  }

  def messagesRequest(storage: String): HttpRequestBuilder = {
    commonBehaviour.visitPage("EcomDev Varnish: Session Messages", "varnish/ajax/message/")
        .header("X-Requested-With", "XMLHttpRequest")
        .check(status.is(200))
  }

  def validateAddToCart = (requestBuilder: HttpRequestBuilder) => {
    requestBuilder.check(status.is(302))
      .check(header("Location").is(expectedCartRedirect))
  }: HttpRequestBuilder

  def expectedCartRedirect: String = {
    if (redirectToCart) {
      return commonBehaviour.buildUrl() + "checkout/cart/"
    }

    "${productUrl}"
  }

  def afterCartRedirect(): ChainBuilder = {
    if (redirectToCart) {
      return exec(commonBehaviour.visitPage("Shopping Cart: View", "checkout/cart/"))
    }

    exec(commonBehaviour.visitPageRaw("Product Page: ${productType}", "${productUrl}"))
  }

  def catalogCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "category_after" -> createCallback("", formkeyRequest)
    )
  }

  def cartCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "add_to_cart_before" -> createCallback("", exec(extractFormKey)),
      "add_to_cart_after" -> createCallback("", exec(afterCartRedirect)
                                                  .pause(commonBehaviour.minPause, commonBehaviour.maxPause)
                                                  .exec(messagesRequest("checkout"))
                                                  .exec(reloadBlockRequest(minicartBlockName)))
    )
  }

  def checkoutCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "checkout_step_before" -> createCallback("", exec(extractFormKey))
    )
  }

  def createCallback(typeCode: String, chainBuilder: ChainBuilder): (String) => ChainBuilder = {
    (currentType: String) => {
      var chain = exec(commonBehaviour.refreshRandom())
      if (currentType == typeCode || typeCode == "") {
        chain = chainBuilder
      }

      chain
    }: ChainBuilder
  }
}
