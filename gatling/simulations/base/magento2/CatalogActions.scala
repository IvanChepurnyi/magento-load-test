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

  var reviewUrl = "review/product/listAjax/id/${product_id}/"

  if (isRandomAddedToReview) {
    reviewUrl = reviewUrl + "?_=${rnd}"
  }

  override def viewProduct(typeCode: String): ChainBuilder = {
    var result = super.viewProduct(typeCode)

    if (addReview) {
      return reviewAjax(result)
    }

    result
  }

  def reviewAjax(chainBuilder: ChainBuilder): ChainBuilder = {
    chainBuilder
      .exec(commonBehaviour.updateDefaultProtocol())
      .exec(commonBehaviour.refreshRandom())
      .exec(
        commonBehaviour
          .visitPage("Product Page: Review AJAX", reviewUrl)
          .header("X-Requested-With", "XMLHttpRequest")
      )
  }
}
