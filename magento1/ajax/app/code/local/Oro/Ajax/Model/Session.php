<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Session Model
 */
class Oro_Ajax_Model_Session extends Mage_Core_Model_Session_Abstract
{
    /**
     * Defines Session namespace
     */
    public function __construct()
    {
        $this->init('oro_ajax');
    }
}
