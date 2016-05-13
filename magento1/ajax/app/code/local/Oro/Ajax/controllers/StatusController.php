<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro AJAX Status Controller action
 */
class Oro_Ajax_StatusController extends Oro_Ajax_Controller_Action
{
    /**
     * Returns all registered dynamic content
     */
    public function indexAction()
    {
        $page     = $this->getRequest()->getParam('page', null);
        Mage::helper('oro_ajax')->processStatusRequest($this->getRequest());
        $response = $this->_getResponseBlocks(null, $page, true);

        $this->_jsonResponse($response);
    }
}
