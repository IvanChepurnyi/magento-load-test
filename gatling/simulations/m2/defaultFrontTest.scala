package m2

import base.magento2._
import base._

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

class defaultFrontTest extends MagentoSimulation {

  def ajaxActions = new AjaxActions(commonBehaviour, domain)

  override def initSession: ChainBuilder = {
    exec(super.initSession).exec(ajaxActions.addFormKey())
  }

  def magentoVersion: String = {
    System.getProperty("magentoVersion", "2.0.7").toString
  }

  def isAjaxReviewRandomParam: Boolean = {
    magentoVersion match {
      case "2.0.0" => true
      case "2.0.1" => true
      case "2.0.2" => true
      case "2.0.3" => true
      case "2.0.4" => true
      case "2.0.5" => true
      case "2.0.6" => true
      case "2.0.7" => true
      case "2.0.8" => true
      case "2.0.9" => true
      case "2.1.0" => false
      case _ => false
    }
  }

  def executeAjaxReview:Int = {
    System.getProperty("ajaxReview", "1").toInt
  }

  override def projectName: String = {
    "Magento " + magentoVersion + " CE"
  }


  override def defaultDataDir: String = {
    "m2ce"
  }

  override def checkoutActions: AbstractCheckoutActions = new CheckoutActions(
    commonBehaviour, feedProvider, !isAjaxReviewRandomParam, ajaxActions.checkoutCallbacks
  )

  override def catalogActions: AbstractCatalogActions = new CatalogActions(
    executeAjaxReview > 0,
    isAjaxReviewRandomParam,
    commonBehaviour,
    feedProvider,
    Map()
  )

  override def shoppingCartActions: AbstractCartActions = new CartActions(
    commonBehaviour,
    "checkout/cart/add/",
    ajaxActions.validateAddToCart,
    ajaxActions.cartCallbacks
  )

}
