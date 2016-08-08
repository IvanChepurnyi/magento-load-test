package base

import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration._

import scala.util.Random


class CommonBehaviour(val simulation: MagentoSimulation) {
  val randomGenerator = Random

  def visitPage(pageName: String, urlString: String, statusCode: Int = 200): HttpRequestBuilder = {
    createGetRequest(pageName, urlString)
      .check(status.is(statusCode))
  }

  def visitHomePage(): HttpRequestBuilder = {
    visitPage("Homepage", "")
      .check(regex(simulation.homepageRegExp))
  }

  def visitPageRaw(pageName: String, urlString: String, statusCode: Int = 200): HttpRequestBuilder = {
    createGetRequestRaw(pageName, urlString)
      .check(status.is(statusCode))
  }

  def createPostRequest(pageName: String, path: String): HttpRequestBuilder = {
    http(pageName)
      .post(buildUrl() + "" + path)
  }

  def createGetRequest(pageName: String, path: String): HttpRequestBuilder = {
    createGetRequestRaw(pageName, buildUrl() + "" + path)
  }

  def createGetRequestRaw(pageName: String, path: String): HttpRequestBuilder = {
    http(pageName)
      .get(path)
  }

  def refreshRandom(): ChainBuilder = {
    exec(session => {
      session.set("rnd", s"${System.currentTimeMillis}.${randomGenerator.nextInt(Integer.MAX_VALUE)}")
    })
  }

  def updateDefaultProtocol(): ChainBuilder = {
    exec(session => session.set("protocol", simulation.defaultProtocol))
  }

  def updateSecureProtocol(): ChainBuilder = {
    exec(session => session.set("protocol", session("secure").as[String]))
  }

  def updateSecure(): ChainBuilder = {
    exec(session => session.set("secure", if (simulation.useSecure > 0) { "https" } else { simulation.defaultProtocol }))
  }

  def updateDomain(): ChainBuilder = {
    exec(session => session.set("domain", simulation.domain))
  }

  def buildUrl(path: String): String = {
    buildUrl() + path
  }

  def buildUrl(): String = {
    "${protocol}://${domain}/"
  }

  def minPause: Duration = {
    simulation.minPause
  }

  def maxPause: Duration = {
    simulation.minPause
  }

  def fullHomeUrl: String = {
    "http://" + simulation.domain + "/"
  }
}

