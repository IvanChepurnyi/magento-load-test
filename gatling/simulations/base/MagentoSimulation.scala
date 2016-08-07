package base

import org.asynchttpclient.util.Base64
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration._
import io.gatling.http.request.builder.HttpRequestBuilder
import probability.{DefaultScenario, Probability}

import scala.util.Random

abstract class MagentoSimulation extends Simulation
{
  val httpProtocol = http
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip,deflate,sdch")
    .acceptLanguageHeader("en-US,en;q=0.8")
    .disableFollowRedirect

  val dataDir = System.getProperty("dataDir", "m1ce").toString

  val nbUsers = System.getProperty("users", "20").toInt
  val nbRamp = System.getProperty("ramp", "30").toInt
  val nbDuring = System.getProperty("during", "10").toInt
  val domain = System.getProperty("domain", "magento-domain.com").toString
  val useSecure = System.getProperty("useSecure", "0").toInt
  val defaultProtocol = System.getProperty("defaultProtocol", "http").toString
  val simpleProductCsv = System.getProperty("simpleProductCsv", "product_simple").toString()
  val scenarioSuffix = " (" + nbUsers.toString + " users over " + nbRamp.toString() + " sec during " + nbDuring.toString() + " min)"

  val feedAddress = csv(dataDir + "/address.csv").random
  val feedCustomer = csv(dataDir + "/customer.csv").circular
  val feedCategory = csv(dataDir + "/category.csv").random
  val feedLayer = csv(dataDir + "/layer.csv").random
  val feedProductSimple = csv(dataDir + "/" + simpleProductCsv + ".csv").random
  val feedProductGrouped = csv(dataDir + "/product_grouped.csv").random
  val feedProductConfigurable = csv(dataDir + "/product_configurable.csv").random

  val minPause = new FiniteDuration(100, MILLISECONDS)
  val maxPause = new FiniteDuration(500, MILLISECONDS)
  val rampDuration = new FiniteDuration(nbRamp, SECONDS)
  val testDuration = new FiniteDuration(nbDuring, MINUTES)

  val probability: Probability  = DefaultScenario.calculateProbability(simpleProductCsv)
  val commonBehaviour = new CommonBehaviour(this)

  var homepageRegExp = """<title>Home page</title>"""

  def projectName: String = {
    "Magento CE 1.9.2.4"
  }

  def defaultDataDir: String = {
    "m1ce"
  }

  /**
    * Initializes new customer session
    */
  def initSession: ChainBuilder = {
    exec(flushCookieJar)
      .exec(commonBehaviour.updateDomain())
      .exec(commonBehaviour.updateSecure())
      .exec(commonBehaviour.updateDefaultProtocol())
      .exec(commonBehaviour.refreshRandom())
  }

  def createScenario () = {
    scenario(projectName + " Load Test" + scenarioSuffix)
      .during(testDuration) {
        randomSwitch(
          probability.abandonmentRatio("configurable") -> abandonedCartFlow("configurable"),
          probability.abandonmentRatio("simple") -> abandonedCartFlow("simple"),
          probability.browseRatio("configurable", 0.5) -> catalogDefaultBrowsingFlow("configurable"),
          probability.browseRatio("simple", 0.5) -> catalogDefaultBrowsingFlow("simple"),
          probability.browseRatio("configurable", 0.5) -> catalogLayerBrowsingFlow("configurable"),
          probability.browseRatio("simple", 0.5) -> catalogLayerBrowsingFlow("simple"),
          probability.checkoutRatio("configurable") -> checkoutFlow("configurable"),
          probability.checkoutRatio("simple") -> checkoutFlow("simple")
        )
      }
  }

  def checkoutActions: AbstractCheckoutActions

  def catalogActions: AbstractCatalogActions

  def shoppingCartActions: AbstractCartActions

  val feedProvider = (feedType: String) => {
    feedType match {
      case "simple" => feedProductSimple
      case "configurable" => feedProductConfigurable
      case "grouped" => feedProductGrouped
      case "category" => feedCategory
      case "layer" => feedLayer
      case "customer" => feedCustomer
      case "address" => feedAddress
    }
  }: FeederBuilder[String]

  def startBrowsing(): ChainBuilder = {
    exec(initSession)
      .exec(commonBehaviour.visitHomePage())
      .pause(minPause, maxPause)
      .exec(catalogActions.viewCategoryRegular())
  }

  def catalogDefaultBrowsingFlow(productType: String): ChainBuilder = {
    startBrowsing()
      .pause(minPause, maxPause)
      .exec(catalogActions.viewProduct(productType))
      .pause(minPause, maxPause)
      .exec(catalogActions.viewCategoryPrevious())
      .pause(minPause, maxPause)
      .exec(catalogActions.viewProduct(productType))
  }

  def catalogLayerBrowsingFlow(productType: String): ChainBuilder = {
    startBrowsing()
      .pause(minPause, maxPause)
      .exec(catalogActions.viewCategoryFiltered())
      .pause(minPause, maxPause)
      .exec(catalogActions.viewProduct(productType))
      .pause(minPause, maxPause)
      .exec(catalogActions.viewCategoryPrevious())
      .pause(minPause, maxPause)
      .exec(catalogActions.viewProduct(productType))
  }

  def abandonedCartFlow(productType: String): ChainBuilder = {
    startBrowsing()
      .pause(minPause, maxPause)
      .exec(catalogActions.viewProduct(productType))
      .pause(minPause, maxPause)
      .exec(shoppingCartActions.addProductToCart(productType))
      .pause(minPause, maxPause)
      .exec(catalogActions.viewCategoryPrevious())
      .pause(minPause, maxPause)
      .exec(shoppingCartActions.addProductToCart(productType))
  }

  def checkoutFlow(productType: String): ChainBuilder = {
    abandonedCartFlow(productType)
      .exec(commonBehaviour.updateSecureProtocol())
      .pause(minPause, maxPause)
      .exec(checkoutActions.asGuest(minPause, maxPause))
  }

  setUp(createScenario()
    .inject(rampUsers(nbUsers) over rampDuration)
    .protocols(httpProtocol))
}

