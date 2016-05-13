<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Response Container Block
 */
class Oro_Ajax_Block_Response extends Mage_Core_Block_Text_List
{
    /**
     * List of related response keys
     *
     * @var array[]
     */
    protected $_response    = array();

    /**
     * Registers JSON response block
     *
     * @param string $key The name in response
     * @param string $block The child block name
     * @param string $page The specific page filter
     */
    public function add($key, $block, $page = null)
    {
        $this->_response[$key] = array($block, $page);
    }

    /**
     * Returns JSON response blocks
     *
     * @param string|string[] $filter
     * @param string|null $page
     * @return array
     */
    public function getResponse($filter = null, $page = null)
    {
        $response = array();
        foreach ($this->_response as $key => $value) {
            if (is_array($filter) && !in_array($key, $filter, true)) {
                continue;
            }
            if (is_string($filter) && $key !== $filter) {
                continue;
            }
            list($blockName, $pageName) = $value;
            if ($pageName !== null && (($page !== null && $pageName !== $page) || $page === null)) {
                continue;
            }

            /** @var Mage_Core_Block_Abstract $block */
            $block = $this->getChild($blockName);
            if (!$block) {
                $this->getLayout()->getBlock($blockName);
            }
            if ($block) {
                $response[$key] = $block->toHtml();
            }
        }

        $response['form_key'] = Mage::getSingleton('core/session')->getFormKey();

        // add customer data
        $session = Mage::getSingleton('customer/session');
        if ($session->isLoggedIn()) {
            $response['customer'] = array(
                'id'    => $session->getCustomer()->getId(),
                'group' => $session->getCustomer()->getGroupId(),
                'name'  => $session->getCustomer()->getName(),
            );
        } else {
            $response['customer'] = false;
        }

        return $response;
    }
}
