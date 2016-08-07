package base

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder


abstract class AbstractCatalogActions(commonBehaviour: CommonBehaviour,
                             feedProvider: String => FeederBuilder[String],
                             productCheckRegexp: String,
                             categoryCheckRegExp: String,
                             categoryLayerCheckRegExp: String,
                             callbacks: Map[String, (String) => ChainBuilder])
  extends Callbacks {

  override def getCallbacks: Map[String, (String) => ChainBuilder] = callbacks

  def viewProduct(typeCode: String): ChainBuilder = {
    feed(feedProvider(typeCode))
      .exec(commonBehaviour.updateDefaultProtocol())
      .exec(session => session.set("productType", typeCode.capitalize))
      .exec(
        execInCallback(
          "product",
          typeCode,
          exec(
            commonBehaviour.visitPage("Product Page: ${productType}", "${url}")
              .check(regex(productCheckRegexp))
              .check(currentLocation.saveAs("productUrl"))
          )
        )
      )
  }

  def viewSimpleProduct(): ChainBuilder = {
    viewProduct("simple")
  }

  def viewConfigurableProduct(): ChainBuilder = {
    viewProduct("configurable")
  }

  def viewGroupedProduct(): ChainBuilder = {
    viewProduct("grouped")
  }

  def buildCategoryRequestByUrl(pageType: String, url: String, contentCheckRegexp: String): HttpRequestBuilder = {
    commonBehaviour.visitPage("Category Page: " + pageType, url)
      .check(regex(contentCheckRegexp))
      .check(currentLocation.saveAs("categoryUrl"))
  }

  def visitCategoryByFeed(feeder: FeederBuilder[String], pageType: String, contentCheckRegexp: String): ChainBuilder = {
    feed(feeder)
      .exec(commonBehaviour.updateDefaultProtocol())
      .exec(
        execInCallback(
          "category", pageType,
          exec(buildCategoryRequestByUrl(pageType, "${url}", contentCheckRegexp))
        )
      )
  }

  def viewCategoryRegular(): ChainBuilder = {
    visitCategoryByFeed(feedProvider("category"), "Default", categoryCheckRegExp)
  }

  def viewCategoryFiltered(): ChainBuilder = {
    visitCategoryByFeed(feedProvider("layer"), "Filtered", categoryCheckRegExp)
  }

  def viewCategoryPrevious(): ChainBuilder = {
    exec(
      execInCallback(
        "category", "Back",
        exec(commonBehaviour.visitPageRaw("Category Page: Back", "${categoryUrl}")
          .check(regex(categoryCheckRegExp)))
      )
    )
  }

}
