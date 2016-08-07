package base.magento2

import base.CommonBehaviour
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

class AjaxActions(commonBehaviour: CommonBehaviour, domain: String) {

  def loadSections(sections: String) = {
      exec(commonBehaviour.refreshRandom())
      .exec(
        commonBehaviour.visitPage("AJAX: Load Sections", "customer/section/load/?sections=" + sections + "&update_section_id=false&_=${rnd}")
          .header("X-Requested-With", "XMLHttpRequest")
      )
  }

  def validateAddToCart = (requestBuilder: HttpRequestBuilder) => {
    requestBuilder
      .header("X-Requested-With", "XMLHttpRequest")
      .check(status.is(200))
      .check(regex("""\[\]"""))
  }: HttpRequestBuilder

  def cartCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "add_to_cart_after" -> createCallback("", loadSections("cart%2Cmessages")),
      "cart_after" -> createCallback("", loadSections("directory-data"))
    )
  }

  def checkoutCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "onepage_after" -> createCallback("success", loadSections("cart%2Cmessages"))
    )
  }

  def addFormKey() = {
    exec(session => session.set("form_key", generateFormKey))
      .exec(addCookie(Cookie("form_key", "${form_key}").withDomain(domain)))
  }

  def generateFormKey = {
    val count   = 16
    val word    = new StringBuilder
    val pattern = """0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ""".toList
    val pLength = pattern.length

    for (i <- 0 until count) {
      word.append(pattern(commonBehaviour.randomGenerator.nextInt(pLength)))
    }

    word.toString
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
