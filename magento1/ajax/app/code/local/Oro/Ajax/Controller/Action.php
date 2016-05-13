<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Abstract Front Controller Action
 */
abstract class Oro_Ajax_Controller_Action extends Mage_Core_Controller_Front_Action
{
    /**
     * @var array
     */
    protected $_messageStorage  = array(
        'oro_ajax/session',
        'core/session',
        'catalog/session',
        'checkout/session',
        'wishlist/session'
    );

    /**
     * Outputs response in JSON format
     *
     * @param array $response
     */
    protected function _jsonResponse($response)
    {
        /* @var $helper Mage_Core_Helper_Data */
        $helper = Mage::helper('core');
        $this->getResponse()
            ->setBody($helper->jsonEncode($response))
            ->setHeader('Content-type', 'application/json', true);
    }

    /**
     * Returns List of Ajax Response Block(s)
     *
     * @param string|string[] $filter
     * @param bool $initMessages
     * @return array
     */
    protected function _getResponseBlocks($filter = null, $page = null, $initMessages = true)
    {
        if (!Mage::registry('oro_ajax_output')) {
            Mage::register('oro_ajax_output', true);
        }
        $customerHandle = Mage::getSingleton('customer/session')->isLoggedIn()
            ? 'customer_logged_in'
            : 'customer_logged_out';
        $this->getLayout()->getUpdate()->addHandle(array('oro_ajax_response', $customerHandle));
        $this->loadLayout(false);

        if ($initMessages) {
            $this->initLayoutMessages($this->_messageStorage);
        }

        /** @var Oro_Ajax_Block_Response $container */
        $container = $this->getLayout()->getBlock('root');

        return $container->getResponse($filter, $page);
    }

    /**
     * Sends page content for layout handle(s) as JSON
     *
     * @param array|string $handles
     * @param bool $initMessages
     */
    protected function _layoutContentResponse($handles, $initMessages = false, array $response = array())
    {
        $helper   = Mage::helper('oro_ajax');

        if ($this->_isLayoutLoaded) {
            // reset layout updates
            foreach (array_keys($this->getLayout()->getAllBlocks()) as $blockName) {
                $this->getLayout()->unsetBlock($blockName);
            }

            $this->getLayout()->getUpdate()->resetHandles();
            $this->getLayout()->getUpdate()->resetUpdates();
            $this->getLayout()->getUpdate()->setCacheId(false);

            $this->_isLayoutLoaded = false;
        }

        // load layout handles
        $this->loadLayout($handles);
        if ($initMessages) {
            $this->initLayoutMessages($this->_messageStorage);
        }

        // render messages
        $messages = $this->getLayout()->getBlock('global_messages');
        if ($messages instanceof Mage_Core_Block_Abstract) {
            $response['global_messages'] = $helper->processCachedContent($messages->toHtml());
        }

        $messages = $this->getLayout()->getBlock('messages');
        if ($messages instanceof Mage_Core_Block_Abstract) {
            $response['messages'] = $helper->processCachedContent($messages->toHtml());
        }

        // render content
        $content  = $this->getLayout()->getBlock('content');
        if ($content instanceof Mage_Core_Block_Abstract) {
            $response['content'] = $helper->processCachedContent($content->toHtml());
        }

        $this->_jsonResponse($response);
    }

    /**
     * Sends block content as JSON
     *
     * @param string[]|string $handles
     * @param string|string[] $blocks
     * @param bool $initMessages
     * @param array $response
     */
    protected function _layoutBlockResponse($handles, $blocks, $initMessages = false, array $response = array())
    {
        $helper   = Mage::helper('oro_ajax');

        // load layout handles
        $this->loadLayout($handles);
        if ($initMessages) {
            $this->initLayoutMessages($this->_messageStorage);
        }

        if (is_string($blocks)) {
            $blocks = array($blocks);
        }

        // render content
        foreach ($blocks as $block) {
            $content = $this->getLayout()->getBlock($block);
            if ($content instanceof Mage_Core_Block_Abstract) {
                $response[$block] = $helper->processCachedContent($content->toHtml());
            }
        }

        $this->_jsonResponse($response);
    }

    /**
     * Sends access control headers and no dispatch action for OPTIONS request
     */
    protected function _preDispatchOptionsRequest()
    {
        if ($this->getRequest()->getMethod() === 'OPTIONS') {
            $this->_addAccessControlHeaders();
            $this->setFlag('', self::FLAG_NO_DISPATCH, true);
            $this->setFlag('', self::FLAG_NO_POST_DISPATCH, true);
        }
    }

    /**
     * Adds access control headers to response
     */
    protected function _addAccessControlHeaders()
    {
        $store    = Mage::app()->getStore();
        $response = $this->getResponse();
        $url      = $store->getBaseUrl(Mage_Core_Model_Store::URL_TYPE_WEB, !$store->isCurrentlySecure());
        $url      = rtrim($url, '/');

        $response->setHeader('Access-Control-Allow-Origin', $url, true);
        $response->setHeader('Access-Control-Allow-Credentials', 'true', true);
        $response->setHeader('Access-Control-Allow-Headers', 'x-json, x-prototype-version, x-requested-with', true);
        $response->setHeader('Access-Control-Expose-Headers', 'x-json, x-prototype-version, x-requested-with', true);
    }
}
