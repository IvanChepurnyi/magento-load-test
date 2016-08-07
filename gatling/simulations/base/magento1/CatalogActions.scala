package base.magento1

import base.CommonBehaviour
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import org.asynchttpclient.util.Base64
import base._


class CatalogActions(commonBehaviour: CommonBehaviour,
                     feedProvider: String => FeederBuilder[String],
                     callbacks: Map[String, (String) => ChainBuilder])
  extends AbstractCatalogActions(
    commonBehaviour,
    feedProvider,
    "<div class=\"product-name\">",
    """page-title category-title""",
    """>Remove This Item</""",
    callbacks) {

  def setUenc: ChainBuilder = {
    exec(session => {
      val url = session("productUrl").as[String]
      session.set("uenc", Base64.encode(url.toCharArray.map(_.toByte)))
    })
  }

  override def viewProduct(typeCode: String): ChainBuilder = {
    super.viewProduct(typeCode)
        .exec(setUenc)
  }

}
