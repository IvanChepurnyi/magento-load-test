package base

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder

import scala.concurrent.duration.Duration

abstract class AbstractCheckoutActions(callbacks: Map[String, (String) => ChainBuilder])
  extends Callbacks {

  override def getCallbacks: Map[String, (String) => ChainBuilder] = callbacks

  def asGuest(minPause: Duration, maxPause: Duration): ChainBuilder
}
