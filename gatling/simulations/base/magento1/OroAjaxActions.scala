package base.magento1

import base.CommonBehaviour
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

/**
  * Created by ivan on 05/08/16.
  */
class OroAjaxActions(commonBehaviour: CommonBehaviour) {

  def statusRequest(page: String): ChainBuilder = {
      exec(commonBehaviour.refreshRandom())
        .exec(
          commonBehaviour.visitPage("ORO AJAX: Status Request", "ajax/status/index/page/" + page + "/?_=${rnd}")
            .header("X-Requested-With", "XMLHttpRequest")
            .check(status.is(200))
            .check(jsonPath("$.customer"))
            .check(jsonPath("$.form_key").saveAs("form_key"))
        )
  }

  def validateAddToCart = (requestBuilder: HttpRequestBuilder) => {
    requestBuilder.check(status.is(200))
      .check(jsonPath("$.minicart_message"))
  }: HttpRequestBuilder

  def catalogCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "category_after" -> createCallback("", statusRequest("catalog_category/category/${category_id}")),
      "product_after" -> createCallback("", statusRequest("catalog_product/product/${product_id}"))
    )
  }

  def cartCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "cart_after" -> createCallback("view", statusRequest("checkout"))
    )
  }

  def checkoutCallbacks: Map[String, (String) => ChainBuilder] = {
    Map(
      "onepage_after" -> createCallback("", statusRequest("checkout"))
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
