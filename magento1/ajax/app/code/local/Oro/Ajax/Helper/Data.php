<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Data Helper
 */
class Oro_Ajax_Helper_Data extends Mage_Core_Helper_Abstract
{
    const URL_PARAM_STORE           = '___store';
    const URL_PARAM_FROM_STORE      = '___from_store';

    /**
     * @var bool
     */
    protected $_isCacheContent;

    /**
     * Replaces CACHE keys with values for partial HTML content
     *
     * @param string $html
     * @return string
     */
    public function processCachedContent($html)
    {
        $app = Mage::app();
        if ($app->useCache(Mage_Page_Block_Html::CACHE_GROUP)) {
            $app->setUseSessionVar(false);
            $html = Mage::getSingleton('core/url')->sessionUrlVar($html);
        }

        return $html;
    }

    /**
     * Returns AJAX Status async call URL
     *
     * @param array $params
     * @return string
     */
    public function getAjaxStatusUrl(array $params = array())
    {
        $params['_secure'] = Mage::app()->getStore()->isCurrentlySecure();

        return Mage::getUrl('oro_ajax/status', $params);
    }

    /**
     * Returns Current Catalog Product ID or null
     *
     * @return null|string
     */
    public function getCurrentCatalogProductId()
    {
        $product = Mage::registry('current_product');
        if ($product) {
            return (string)$product->getId();
        }

        return null;
    }

    /**
     * Returns Current Catalog Category ID or null
     *
     * @return null|string
     */
    public function getCurrentCatalogCategoryId()
    {
        $category = Mage::registry('current_category');
        if ($category) {
            return (string)$category->getId();
        }

        return null;
    }

    /**
     * Checks if page content will be cached
     *
     * @return bool
     */
    public function isCacheContent()
    {
        if ($this->_isCacheContent === null) {
            if (Mage::app()->getStore()->isCurrentlySecure()) {
                $this->_isCacheContent = false;
                return false;
            }
            if (!$this->isModuleEnabled('Phoenix_VarnishCache')) {
                $this->_isCacheContent = false;
                return false;
            }
            if (!Mage::helper('varnishcache')->isEnabled()) {
                $this->_isCacheContent = false;
                return false;
            }

            $request        = Mage::app()->getRequest();
            $fullActionName = sprintf('%s_%s_%s', $request->getRequestedRouteName(),
                $request->getRequestedControllerName(), $request->getRequestedActionName());

            // check caching blacklist for request routes
            $disableRoutes = explode("\n", trim(Mage::getStoreConfig('varnishcache/general/disable_routes')));
            foreach ($disableRoutes as $route) {
                $route = trim($route);
                // if route is found at first position we have a hit
                if ($route !== '' && strpos($fullActionName, $route) === 0) {
                    $this->_isCacheContent = false;
                    return false;
                }
            }

            $this->_isCacheContent = true;
        }

        return $this->_isCacheContent;
    }

    /**
     * Prepares registry and events for Status Request
     *
     * @param Mage_Core_Controller_Request_Http $request
     */
    public function processStatusRequest($request)
    {
        $page = $request->getParam('page', '');
        switch ($page) {
            case 'catalog_product':
                $productId = $request->getParam('product');
                $this->_catalogProductView($productId);
                break;
        }
    }

    /**
     * Registers Catalog Product View object and run events
     *
     * @param int $productId
     */
    protected function _catalogProductView($productId)
    {
        if (!$productId) {
            return;
        }
        $store = Mage::app()->getStore();

        /** @var Mage_Catalog_Model_Product $product */
        $product = Mage::getModel('catalog/product')
            ->setStoreId($store->getId())
            ->load($productId);
        if (!$product->getId()) {
            return;
        }
        if (!in_array($store->getWebsiteId(), $product->getWebsiteIds())) {
            return;
        }
        if (!$product->isVisibleInCatalog() || !$product->isVisibleInSiteVisibility()) {
            return;
        }

        Mage::register('product', $product);
        Mage::register('current_product', $product);

        Mage::getModel('reports/product_index_viewed')
            ->setProductId($productId)
            ->save()
            ->calculate();
    }
}
