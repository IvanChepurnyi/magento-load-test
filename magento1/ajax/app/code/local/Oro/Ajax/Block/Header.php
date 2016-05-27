<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Header Block
 *
 * @method string getPage()
 * @method Oro_Ajax_Block_Header setPage(string $value)
 */
class Oro_Ajax_Block_Header extends Mage_Core_Block_Template
{
    /**
     * Status URL parameters
     *
     * @var array
     */
    protected $_urlParams       = array();

    /**
     * @var array
     */
    protected $_placeholders    = array();

    /**
     * Sets Status URL parameters
     *
     * @param string $key
     * @param string $value
     */
    public function setStatusUrlParam($key, $value)
    {
        $this->_urlParams[$key] = $value;
    }

    /**
     * Returns AJAX Status URL
     *
     * @return string
     */
    public function getStatusUrl()
    {
        return Mage::helper('oro_ajax')->getAjaxStatusUrl($this->_urlParams);
    }

    /**
     * Returns actual form key
     *
     * @return string
     */
    public function getFormKey()
    {
        return Mage::getSingleton('core/session')->getFormKey();
    }

    /**
     * Registers Placeholder and Updater
     *
     * @param string $xpath
     * @param array $layout
     * @param array $updater
     * @return $this
     */
    public function registerPlaceholder($xpath, $layout = array(), $updater = array())
    {
        $placeholder = array(
            'xpath'   => $xpath,
            'layout'  => $layout,
            'updater' => $updater,
        );

        $this->_placeholders[] = $placeholder;

        return $this;
    }

    /**
     * Returns registered updaters json
     *
     * @return string
     */
    public function getUpdatersJson()
    {
        $updaters = array();
        foreach ($this->_placeholders as $placeholder) {
            $updaters[] = $placeholder['updater'];
        }

        return Mage::helper('core')->jsonEncode($updaters);
    }
}
