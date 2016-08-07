package m1oro

import base._
import base.magento1._

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

class defaultFrontTest extends MagentoSimulation {

  def oroAjaxActions = new OroAjaxActions(commonBehaviour)

  override def checkoutActions: AbstractCheckoutActions = new CheckoutActions(
    commonBehaviour, feedProvider, oroAjaxActions.checkoutCallbacks
  )

  override def catalogActions: AbstractCatalogActions = new CatalogActions(
    commonBehaviour,
    feedProvider,
    oroAjaxActions.catalogCallbacks
  )

  override def shoppingCartActions: AbstractCartActions = new CartActions(
    commonBehaviour,
    "ajax/cart/add",
    oroAjaxActions.validateAddToCart,
    oroAjaxActions.cartCallbacks
  )
}