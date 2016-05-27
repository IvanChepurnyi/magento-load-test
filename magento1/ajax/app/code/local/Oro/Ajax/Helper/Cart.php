<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Checkout Cart Helper
 */
class Oro_Ajax_Helper_Cart extends Mage_Core_Helper_Abstract
{
    /**
     * Shopping Cart instance
     *
     * @var Mage_Checkout_Model_Cart
     */
    protected $_cart;

    /**
     * Checkout session instance
     *
     * @var Mage_Checkout_Model_Session
     */
    protected $_session;

    /**
     * Returns shopping cart instance
     *
     * @return Mage_Checkout_Model_Cart
     */
    public function getCart()
    {
        if ($this->_cart === null) {
            $this->_cart = Mage::getSingleton('checkout/cart');
        }

        return $this->_cart;
    }

    /**
     * Returns current active quote instance
     *
     * @return Mage_Sales_Model_Quote
     */
    public function getQuote()
    {
        return $this->getCart()->getQuote();
    }

    /**
     * Returns checkout session instance
     *
     * @return Mage_Checkout_Model_Session
     */
    public function getSession()
    {
        if ($this->_session === null) {
            $this->_session = Mage::getSingleton('checkout/session');
        }

        return $this->_session;
    }

    /**
     * Initializes product instance from request data
     *
     * @param Mage_Core_Controller_Request_Http $request
     * @return Mage_Catalog_Model_Product|bool
     */
    protected function _initProduct(Mage_Core_Controller_Request_Http $request)
    {
        $productId = (int)$request->getParam('product');
        if ($productId) {
            /* @var $product Mage_Catalog_Model_Product */
            $product = Mage::getModel('catalog/product')
                ->setStoreId(Mage::app()->getStore()->getId())
                ->load($productId);
            if ($product->getId()) {
                return $product;
            }
        }

        return false;
    }

    /**
     * Updates shopping cart data
     *
     * @param Mage_Core_Controller_Request_Http $request
     * @return array
     */
    public function updateCart(Mage_Core_Controller_Request_Http $request)
    {
        try {
            $cartData = $request->getParam('cart');
            if (is_array($cartData)) {
                $cart  = $this->getCart();
                $quote = $this->getQuote();

                $filter = new Zend_Filter_LocalizedToNormalized(
                    array('locale' => Mage::app()->getLocale()->getLocaleCode())
                );
                foreach ($cartData as $index => $data) {
                    if (isset($data['qty'])) {
                        $cartData[$index]['qty'] = $filter->filter(trim($data['qty']));
                    }
                }

                if ($quote->getCustomerId() && !$cart->getCustomerSession()->getCustomer()->getId()) {
                    $quote->setCustomerId(null);
                }

                $cartData = $cart->suggestItemsQty($cartData);
                $cart->updateItems($cartData)
                     ->save();
            }
            $this->getSession()->setCartWasUpdated(true);

            $return = array(
                'result'  => true,
                'message' => $this->__('Shopping cart was updated')
            );
        } catch (Mage_Core_Exception $e) {
            $return = array(
                'result'  => false,
                'message' => $e->getMessage()
            );
        } catch (Exception $e) {
            Mage::logException($e);
            $return = array(
                'result'  => false,
                'message' => $this->__('Cannot update shopping cart')
            );
        }

        return $return;
    }

    /**
     * Updates shipping method
     *
     * @param Mage_Core_Controller_Request_Http $request
     * @return array
     */
    public function updateShippingMethod(Mage_Core_Controller_Request_Http $request)
    {
        try {
            $code = (string)$request->getParam('estimate_method');
            if (!empty($code)) {
                $quote = $this->getQuote();
                $quote->getShippingAddress()
                    ->setShippingMethod($code)
                    ->save();
                $quote->setTotalsCollectedFlag(false)
                    ->collectTotals()
                    ->save();
            }

            $return = array(
                'result'  => true,
                'message' => $this->__('Shipping estimate was updated')
            );
        } catch (Mage_Core_Exception $e) {
            $return = array(
                'result'  => false,
                'message' => $e->getMessage()
            );
        } catch (Exception $e) {
            Mage::logException($e);
            $return = array(
                'result'  => false,
                'message' => $this->__('Cannot update shipping estimate')
            );
        }

        return $return;
    }


    /**
     * Updates coupon
     *
     * @param  Mage_Core_Controller_Request_Http $request
     * @return array
     */
    public function updateCoupon(Mage_Core_Controller_Request_Http $request)
    {
        try {
            $couponCode = (string) $request->getParam('coupon_code');
            $quote = $this->getQuote();

            if ($request->getParam('remove') === '1') {
                $couponCode = '';
            }
            $oldCouponCode = (string) $quote->getCouponCode();

            if ($couponCode === '' && $oldCouponCode === '') {
                Mage::throwException($this->__('Please enter coupon code'));
            }

            $quote->getShippingAddress()
                ->setCollectShippingRates(true);
            $quote->setCouponCode(strlen($couponCode) ? $couponCode : '')
                ->setTotalsCollectedFlag(false)
                ->collectTotals()
                ->save();

            $message = '';
            if ($couponCode !== '') {
                if ($couponCode === $quote->getCouponCode()) {
                    $message = $this->__('Coupon code "%s" was applied', Mage::helper('core')->escapeHtml($couponCode));
                } else {
                    Mage::throwException($this->__('Coupon code "%s" is not valid',
                        Mage::helper('core')->escapeHtml($couponCode)));
                }
            } else {
                $message = $this->__('Coupon code was canceled');
            }

            $return = array(
                'result'  => true,
                'message' => $message
            );
        } catch (Mage_Core_Exception $e) {
            $return = array(
                'result'  => false,
                'message' => $e->getMessage()
            );
        } catch (Exception $e) {
            Mage::logException($e);
            $return = array(
                'result'  => false,
                'message' => $this->__('Cannot apply the coupon code')
            );
        }

        return $return;
    }


    /**
     * Adds product to cart
     *
     * @param Mage_Core_Controller_Request_Http $request
     * @param bool $saveCart
     * @return array
     */
    public function addItem(Mage_Core_Controller_Request_Http $request, $saveCart = true)
    {
        $cart   = $this->getCart();
        $params = $request->getParams();
        try {
            if (array_key_exists('qty', $params)) {
                $filter = new Zend_Filter_LocalizedToNormalized(
                    array('locale' => Mage::app()->getLocale()->getLocaleCode())
                );
                $params['qty'] = $filter->filter($params['qty']);
            }

            $product = $this->_initProduct($request);
            $related = $request->getParam('related_product');

            /**
             * Check product availability
             */
            if (!$product) {
                Mage::throwException($this->__('Invalid data'));
            }

            $cart->addProduct($product, $params);
            if (!empty($related)) {
                $cart->addProductsByIds(explode(',', $related));
            }

            if ($saveCart) {
                $cart->save();

                $this->getSession()->setCartWasUpdated(true);

                Mage::dispatchEvent('checkout_cart_add_product_complete', array(
                    'product'   => $product,
                    'request'   => $request,
                    'response'  => Mage::app()->getResponse()
                ));
            }

            $return = array(
                'result'  => true,
                'message' => $this->__('Item was added successfully.'),
            );
        } catch (Mage_Core_Exception $e) {
            $return = array(
                'result'  => false,
                'message' => $e->getMessage()
            );
        } catch (Exception $e) {
            Mage::logException($e);
            $return = array(
                'result'  => false,
                'message' => $this->__('Cannot add the item.')
            );
        }

        return $return;
    }


    /**
     * Deletes shopping cart item
     *
     * @param Mage_Core_Controller_Request_Http $request
     * @param bool $saveCart
     * @return array
     */
    public function deleteItem(Mage_Core_Controller_Request_Http $request, $saveCart = true)
    {
        try {
            $id = (int)$request->getParam('id');
            if ($id) {
                $cart = $this->getCart();
                $cart->removeItem($id);

                if ($saveCart) {
                    Mage::dispatchEvent('checkout_cart_delete_item_after', array(
                        'quote_item_id' => $id,
                        'request'       => $request,
                        'response'      => Mage::app()->getResponse()
                    ));

                    $cart->save();
                }
            }

            $return = array(
                'result'  => true,
                'message' => $this->__('Item was removed successfully.')
            );
        } catch (Mage_Core_Exception $e) {
            $return = array(
                'result'  => false,
                'message' => $e->getMessage()
            );
        } catch (Exception $e) {
            Mage::logException($e);
            $return = array(
                'result'  => false,
                'message' => $this->__('Cannot remove the item.')
            );
        }

        return $return;
    }

    /**
     * Returns delete item async call URL
     *
     * @param Mage_Sales_Model_Quote_Item $item
     * @return string
     */
    public function getDeleteUrl($item)
    {
        return Mage::getUrl('oro_ajax/cart/delete', array(
            'id'        => $item->getId(),
            '_secure'   => Mage::app()->getStore()->isCurrentlySecure(),
        ));
    }

    /**
     * Returns add item to cart URL
     *
     * @return string
     */
    public function getAddUrl()
    {
        return Mage::getUrl('oro_ajax/cart/add', array(
            '_secure'   => Mage::app()->getStore()->isCurrentlySecure(),
        ));
    }

    /**
     * Returns Cart action Url Template
     *
     * @return string
     */
    public function getUrlTemplate()
    {
        return Mage::getUrl('oro_ajax/cart/%action%', array(
            '_secure'   => Mage::app()->getStore()->isCurrentlySecure(),
        ));
    }
}
