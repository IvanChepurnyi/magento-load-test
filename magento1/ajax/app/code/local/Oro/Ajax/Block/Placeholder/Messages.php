<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Messages Placeholder Block
 */
class Oro_Ajax_Block_Placeholder_Messages extends Mage_Core_Block_Messages
{
    /**
     * @var Oro_Ajax_Model_Session
     */
    protected $_session;

    /**
     * Defines instances
     */
    protected function _construct()
    {
        parent::_construct();
        $this->_session = Mage::getSingleton('oro_ajax/session');
    }

    /**
     * Registers Messages
     *
     * @param Mage_Core_Model_Message_Collection $messages
     * @return $this
     */
    public function setMessages(Mage_Core_Model_Message_Collection $messages)
    {
        return $this->addMessages($messages);
    }

    /**
     * Registers Messages
     *
     * @param Mage_Core_Model_Message_Collection $messages
     * @return $this
     */
    public function addMessages(Mage_Core_Model_Message_Collection $messages)
    {
        foreach ($messages->getItems() as $message) {
            $this->_session->addMessage($message);
        }

        return $this;
    }

    /**
     * Registers Message
     *
     * @param Mage_Core_Model_Message_Abstract $message
     * @return $this
     */
    public function addMessage(Mage_Core_Model_Message_Abstract $message)
    {
        $this->_session->addMessage($message);

        return $this;
    }

    /**
     * Returns Placeholder HTML
     *
     * @return string
     */
    public function getGroupedHtml()
    {
        return '<div id="' . $this->getId() . '"></div>';
    }
}
