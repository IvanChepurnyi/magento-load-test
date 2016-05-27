<?php
/**
 * @category   Oro
 * @package    Oro_Ajax
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

/**
 * Oro Ajax Observer
 */
class Oro_Ajax_Model_Observer
{
    /**
     * Changes Layout blocks to placeholders
     *
     * @param Varien_Event_Observer $observer
     */
    public function layoutGenerateBlocksBefore(Varien_Event_Observer $observer)
    {
        /** @var Mage_Core_Controller_Varien_Action $controller */
        $controller = $observer->getEvent()->getAction();
        $action     = strtolower($controller->getFullActionName());

        // prevent to remove blocks from native checkout ajax
        if (in_array($action, array('checkout_cart_ajaxdelete', 'checkout_cart_ajaxupdate'), true)) {
            return;
        }

        /** @var Mage_Core_Model_Layout $layout */
        $layout     = $observer->getEvent()->getLayout();

        $root  = $layout->getNode();
        $rules = $root->xpath('//block[@name="oro_ajax_header"]/action[@method="registerPlaceholder"]');
        if (!$rules) {
            return;
        }

        /** @var Mage_Core_Model_Layout_Element $rule */
        foreach ($rules as $rule) {
            if (empty($rule->xpath)) {
                continue;
            }

            $xpath = sprintf('//%s', $rule->xpath);
            $data  = $rule->placeholder;
            if ($data->callback && strpos($data->callback, '::')) {
                try {
                    list($helperName, $helperMethod) = explode('::', $data->callback);
                    $helper = Mage::helper($helperName);
                    if (is_callable(array($helper, $helperMethod))) {
                        $result = call_user_func(array($helper, $helperMethod));
                        if (!$result) {
                            continue;
                        }
                    }
                } catch (Exception $e) {
                    Mage::logException($e);
                }
            }
            $nodes = $root->xpath($xpath);

            if (is_array($nodes)) {
                /** @var Mage_Core_Model_Layout_Element $node */
                foreach ($nodes as $node) {
                    // removes original blocks and actions children
                    if ($node->action) {
                        unset($node->action);
                    }
                    if ($node->block) {
                        unset($node->block);
                    }

                    $type = 'oro_ajax/placeholder';
                    if ($data->block) {
                        $type = (string)$data->block;
                    }
                    $node['type'] = $type;
                    if ($data->template) {
                        $node['template'] = (string)$data->template;
                    } else {
                        unset($node['template']);
                    }
                    if ($data->id) {
                        $action = $node->addChild('action');
                        $action->addAttribute('method', 'setId');
                        $action->addChild('value', $data->id);
                    }
                    if ($data->element) {
                        $action = $node->addChild('action');
                        $action->addAttribute('method', 'setElement');
                        $action->appendChild($data->element);
                    }

                    // remove references to placeholder
                    $refPath  = sprintf('//reference[@name="%s"]', $node['name']);
                    foreach ($root->xpath($refPath) as $refNode) {
                        unset($refNode[0][0]);
                    }
                }
            } else {
                unset($rule[0][0]);
            }
        }
    }

    /**
     * Replaces cart delete URL for AJAX
     *
     * @param Varien_Event_Observer $observer
     */
    public function coreBlockAbstractToHtmlBefore(Varien_Event_Observer $observer)
    {
        if (!Mage::registry('oro_ajax_output')) {
            return;
        }

        /** @var Mage_Core_Block_Abstract $block */
        $block = $observer->getEvent()->getBlock();

        if ($block instanceof Mage_Checkout_Block_Cart_Item_Renderer) {
            $deleteUrl = $block->getUrl('checkout/cart/delete', array(
                'id'       => $block->getItem()->getId(),
                'form_key' => Mage::getSingleton('core/session')->getFormKey(),
            ));
            $block->setDeleteUrl($deleteUrl);
        }
    }
}
