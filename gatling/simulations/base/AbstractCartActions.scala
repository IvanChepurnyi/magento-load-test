package base

import io.gatling.core.structure.ChainBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import io.gatling.http.Predef._
import io.gatling.core.Predef._

abstract class AbstractCartActions(commonBehaviour: CommonBehaviour,
                                   addToCartUrl: String,
                                   addToCartValidation: HttpRequestBuilder => HttpRequestBuilder,
                                   callbacks: Map[String, (String) => ChainBuilder])
  extends Callbacks {

  def viewCart: ChainBuilder


  override def getCallbacks: Map[String, (String) => ChainBuilder] = callbacks

  def addProductToCart(productType: String): ChainBuilder = {
    execInCallback(
      "add_to_cart",
      productType,
      exec(commonBehaviour.updateDefaultProtocol())
        .exec(
          addToCartValidation(
            buildCartRequest(
             productType,
             commonBehaviour.createPostRequest("Shopping Cart: Add " + productType.capitalize + " Product", addToCartUrl)
            )
          )
        )
    )
  }

  def buildCartRequest(productType: String, requestBuilder: HttpRequestBuilder): HttpRequestBuilder = {
    productType match {
      case "configurable" => buildCartConfigurableProductParams(requestBuilder)
      case "grouped" => buildCartGroupedProductParams(requestBuilder)
      case _ => buildCartSimpleProductParams(requestBuilder)
    }
  }

  def buildCartConfigurableProductParams(requestBuilder: HttpRequestBuilder): HttpRequestBuilder = {
    requestBuilder.formParam("product", "${product_id}")
      .formParam( "form_key", "${form_key}")
      .formParam( "qty", "1")
      .formParamMap(session => {
        val keys = session("options").as[String].split("&").map(k => "super_attribute[" + k.split("=")(0) + "]")
        val values = session("options").as[String].split("&").map(v => v.split("=")(1))
        val result = (keys zip values).toMap
        result
      })
  }

  def buildCartSimpleProductParams(requestBuilder: HttpRequestBuilder): HttpRequestBuilder = {
    requestBuilder.formParam( "product", "${product_id}")
      .formParam( "form_key", "${form_key}")
      .formParam( "qty", "1")
  }

  def buildCartGroupedProductParams(requestBuilder: HttpRequestBuilder): HttpRequestBuilder = {
    requestBuilder.formParam("product", "${product_id}")
      .formParam("form_key", "${form_key}")
      .formParam("qty", "1")
      .formParamMap(session => {
        val children = session("children").as[String].split(",")
        val childId = children(commonBehaviour.randomGenerator.nextInt(children.length))
        val keys = children.map(k => "super_group[" + k + "]")
        val values = children.map(v => if (v == childId) 1 else 0)
        val result = (keys zip values).toMap
        result
      })
  }


}
