package base.magento1

import base.{AbstractCartActions, CommonBehaviour}
import io.gatling.core.structure.ChainBuilder
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

class CartActions(commonBehaviour: CommonBehaviour,
                  addToCartUrl: String,
                  addToCartValidation: HttpRequestBuilder => HttpRequestBuilder,
                  callbacks: Map[String, (String) => ChainBuilder])
  extends AbstractCartActions(commonBehaviour, addToCartUrl, addToCartValidation, callbacks)
{

  def viewCart: ChainBuilder = {
    exec(commonBehaviour.updateDefaultProtocol())
        .exec(execInCallback("cart", "view", exec(commonBehaviour.visitPage("Shopping Cart: View", commonBehaviour.buildUrl("checkout/cart/"))
          .check(status.is(200))
          .check(css( """#shopping-cart-table input[name^="cart"]""", "value").findAll.saveAs("cart_qty_values"))
          .check(css( """#shopping-cart-table input[name^="cart"]""", "name").findAll.saveAs("cart_qty_name")))))
  }


}
