package base

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.collection.mutable.StringBuilder

class warmUpTest extends Simulation {
  val dataDir = System.getProperty("dataDir", "m2ce").toString
  val nbUsers = System.getProperty("users", "20").toInt
  val nbRamp = System.getProperty("ramp", "30").toInt
  val domain = System.getProperty("domain", "m2ce.magecore.com").toString
  val useSecure = System.getProperty("useSecure", "0").toInt

  val httpProtocol = http
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip,deflate,sdch")
    .acceptLanguageHeader("en-US,en;q=0.8")
    .baseURL((if (useSecure > 0) { "https://" } else { "http://" }) + domain + "/")
    .disableFollowRedirect

  val simpleProductCsv = System.getProperty("simpleProductCsv", "product_simple").toString
  val feedCategory = csv(dataDir + "/category.csv").queue
  val feedProductConfigurable = csv(dataDir + "/product_configurable.csv").circular
  val feedProductSimple = csv(dataDir + "/" + simpleProductCsv + ".csv").circular
  val numberOfConfigurables = feedProductConfigurable.records.length
  val numberOfSimple = feedProductSimple.records.length
  val ratioSimpleToConfirable = 0.4d
  val numberOfUsersForConfigurable = (nbUsers * ratioSimpleToConfirable).toInt
  val numberOfUsersForSimple = nbUsers - numberOfUsersForConfigurable

  val random = new java.util.Random

  object BasePages {
    def visitPage(page: String, title: String) = http(title)
      .get(page)
      .check(status.is(200))
  }

  object CMS {
    def homepage = {
      exec(
        BasePages.visitPage("/", "Home Page")
      )
    }
  }

  object Catalog {
    def viewCategory = {
      feed(feedProductConfigurable)
        .exec(
          BasePages.visitPage("/${url}", "View Category")
        )
    }

    def viewSimpleProduct = {
      feed(feedProductSimple)
        .exec(
          BasePages.visitPage("/${url}", "View Simple Product")
        )
    }

    def viewConfigurableProduct = {
      feed(feedProductConfigurable)
        .exec(
          BasePages.visitPage("/${url}", "View Configurable Product")
        )
    }
  }

  object Scenario {

    def configurable = repeat(
      (numberOfConfigurables / numberOfUsersForConfigurable).toInt + 1, "configurablePages"
    ) {
      exec(Catalog.viewConfigurableProduct)
        .pause(
          10 milliseconds,
          50 milliseconds
        )
    }

    def category = repeat(feedCategory.records.length, "categoryPages") {
      exec(Catalog.viewCategory)
        .pause(
          10 milliseconds,
          50 milliseconds
        )
    }

    def simple = repeat(
      (numberOfSimple / numberOfUsersForSimple).toInt + 1, "simplePages"
    ) {
      exec(Catalog.viewSimpleProduct)
        .pause(
          10 milliseconds,
          50 milliseconds
        )
    }

    val scenarioCategoryBrowser = scenario("Category Browser").exec(CMS.homepage)
        .exec(CMS.homepage)
        .exec(category)

    val scenarioConfigurableProductBrowser = scenario("Configurable Product Browser").exec(configurable)

    val scenarioSimpleProductBrowser = scenario("Simple Product Browser").exec(simple)
  }


  setUp(
    Scenario.scenarioCategoryBrowser.inject(atOnceUsers(1)).protocols(httpProtocol),
    Scenario.scenarioConfigurableProductBrowser.inject(rampUsers(numberOfUsersForConfigurable) over (nbRamp seconds)).protocols(httpProtocol),
    Scenario.scenarioSimpleProductBrowser.inject(rampUsers(numberOfUsersForSimple) over (nbRamp seconds)).protocols(httpProtocol)
  )
}