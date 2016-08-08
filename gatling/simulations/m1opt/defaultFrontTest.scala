package m1opt

import base._
import base.magento1._
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder

class defaultFrontTest extends MagentoSimulation {

  def ecomdevAjaxActions: EcomDevVarnishActions = {
    new EcomDevVarnishActions(commonBehaviour, "minicart_head", false)
  }

  override def initSession: ChainBuilder = {
    exec(super.initSession)
      .exec(ecomdevAjaxActions.resetFormKey)
  }

  override def checkoutActions: AbstractCheckoutActions = {
    new CheckoutActions(
      commonBehaviour, feedProvider, ecomdevAjaxActions.checkoutCallbacks
    )
  }

  override def catalogActions: AbstractCatalogActions = {
    new CatalogActions(
      commonBehaviour,
      feedProvider,
      ecomdevAjaxActions.catalogCallbacks
    )
  }

  override def shoppingCartActions: AbstractCartActions = {
    new CartActions(
      commonBehaviour,
      "checkout/cart/add/uenc/${uenc}",
      ecomdevAjaxActions.validateAddToCart,
      ecomdevAjaxActions.cartCallbacks
    )
  }

}
