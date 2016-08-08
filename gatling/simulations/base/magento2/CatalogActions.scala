package base.magento2

import base._
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder

class CatalogActions(addReview: Boolean, isRandomAddedToReview: Boolean, commonBehaviour: CommonBehaviour,
                     feedProvider: String => FeederBuilder[String],
                     callbacks: Map[String, (String) => ChainBuilder]
                    )
  extends AbstractCatalogActions(
    commonBehaviour,
    feedProvider,
    "<span.*?itemprop=\"name\".*?>.*?</span>",
    """categorypath""",
    "Remove This Item",
    callbacks
  ) {

  def reviewUrl: String = {
    if (isRandomAddedToReview) {
      return "review/product/listAjax/id/${product_id}/?_=${rnd}"
    }

    "review/product/listAjax/id/${product_id}/"
  }


  override def viewProduct(typeCode: String): ChainBuilder = {
    var result = super.viewProduct(typeCode)

    if (addReview) {
      return exec(result).exec(reviewAjax)
    }

    result
  }

  def reviewAjax: ChainBuilder = {
    exec(commonBehaviour.refreshRandom())
      .exec(
        commonBehaviour
          .visitPage("Product Page: Review AJAX", reviewUrl)
          .header("X-Requested-With", "XMLHttpRequest")
      )
  }
}
