<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Placeholder Block
 *
 */
class Oro_Ajax_Block_Placeholder extends Mage_Core_Block_Template
{
    /**
     * Registers default template
     */
    protected function _construct()
    {
        $this->setTemplate('oro/ajax/placeholder.phtml');
        parent::_construct();
    }

    /**
     * Returns placeholder element tag name
     *
     * @return string
     */
    public function getElementTag()
    {
        $element = $this->getData('element');
        if (is_array($element) && array_key_exists('tag', $element)) {
            return $element['tag'];
        }

        return false;
    }

    /**
     * Returns placeholder element HTML attributes
     *
     * @return string
     */
    public function getElementAttributes()
    {
        $element = $this->getData('element');
        if (is_array($element) && array_key_exists('attributes', $element)) {
            $attributes = array();
            foreach ($element['attributes'] as $k => $v) {
                $attributes[] = sprintf('%s="%s"', $k, $this->escapeHtml($v));
            }

            return implode(' ', $attributes);
        }

        return '';
    }

    /**
     * Returns placeholder element HTML content
     *
     * @return string
     */
    public function getElementContent()
    {
        $element = $this->getData('element');
        if (is_array($element) && array_key_exists('content', $element)) {
            return $element['content'];
        }

        return '';
    }
}
