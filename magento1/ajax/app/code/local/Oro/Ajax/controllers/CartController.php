<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Ajax Cart Controller
 */
class Oro_Ajax_CartController extends Oro_Ajax_Controller_Action
{
    /**
     * Returns shopping cart helper
     *
     * @return Oro_Ajax_Helper_Cart
     */
    protected function _getHelper()
    {
        return Mage::helper('oro_ajax/cart');
    }

    /**
     * Adds item to cart. Renders mini cart content
     */
    public function addAction()
    {
        $result   = $this->_getHelper()->addItem($this->getRequest());

        if ($this->getRequest()->getParam('page') === 'checkout') {
            $this->_sendCartContentResponse();
        } else {
            $response = $this->_getResponseBlocks('top_cart');
            $response['minicart_expand']  = true;
            $response['minicart_message'] = $result['message'];
            $response['minicart_success'] = $result['result'];

            $this->_jsonResponse($response);
        }
    }

    /**
     * Updates shopping cart data
     */
    public function updateAction()
    {
        $result = $this->_getHelper()->updateCart($this->getRequest());
        if ($result['result'] === true) {
            Mage::getSingleton('checkout/session')->addSuccess($result['message']);
        } else {
            Mage::getSingleton('checkout/session')->addError($result['message']);
        }

        $this->_sendCartContentResponse(true);
    }

    /**
     * Updates coupon action
     */
    public function updateCouponAction()
    {
        $result = $this->_getHelper()->updateCoupon($this->getRequest());
        if ($result['result'] === true) {
            Mage::getSingleton('checkout/session')->addSuccess($result['message']);
        } else {
            Mage::getSingleton('checkout/session')->addError($result['message']);
        }

        $this->_sendCartContentResponse(true);
    }

    /**
     * Updates shipping method action
     */
    public function updateShippingMethodAction()
    {
        $result = $this->_getHelper()->updateShippingMethod($this->getRequest());
        if ($result['result'] !== true) {
            Mage::getSingleton('checkout/session')->addError($result['message']);
        }
        $this->_sendCartContentResponse(true);
    }

    /**
     * Deletes shopping cart item
     */
    public function deleteAction()
    {
        $result = $this->_getHelper()->deleteItem($this->getRequest());
        if ($this->getRequest()->getParam('page') === 'checkout') {
            $this->_sendCartContentResponse();
        } else {
            $response = $this->_getResponseBlocks('minicart_content');

            $response['action'] = 'checkout_cart_delete';
            $response['minicart_message'] = $result['message'];
            if (!$result['result']) {
                $response['error'] = true;
            } else {
                $response['minicart_expand'] = true;
            }

            $this->_jsonResponse($response);
        }
    }

    /**
     * Sends Shopping Cart content as JSON
     *
     * @param bool $showMessages
     */
    protected function _sendCartContentResponse($showMessages = true)
    {
        $this->_layoutContentResponse(array('default', 'checkout_cart_index'), $showMessages);
    }
}
