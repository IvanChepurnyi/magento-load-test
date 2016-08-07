package base

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder

trait Callbacks {

  def getCallbacks: Map[String, (String) => ChainBuilder]

  def execInCallback(callbackType: String, pageType: String, chainBuilder: ChainBuilder): ChainBuilder = {
    if (!getCallbacks.contains(callbackType + "_before") && !getCallbacks.contains(callbackType + "_after")) {
      chainBuilder
    }

    var chain = chainBuilder

    if (getCallbacks.contains(callbackType + "_before")) {
      chain = exec(callCallback(getCallbacks.get(callbackType + "_before"), pageType), chain)
    }

    if (getCallbacks.contains(callbackType + "_after")) {
      chain = exec(chain, callCallback(getCallbacks.get(callbackType + "_after"), pageType))
    }

    chain
  }

  def callCallback(value: Option[(String) => ChainBuilder], pageType: String): ChainBuilder = {
    value match {
      case Some(v) => v(pageType)
      case None => exec(session => session.set("dummy", ""))
    }
  }
}
