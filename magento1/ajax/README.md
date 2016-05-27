# Introduction

This package enables AJAX endpoints that allow to get dynamic content for pages cached by Varnish for Magento 1.9.x.

# Features

- JavaScript library for processing dynamic content with AJAX
- Dynamic placeholders management

# Installation

- Copy `app` and `skin` to the root of the Magento instance
- Refresh Magento cache

## Phoenix Varnish

Oro_Ajax module is compatible with `Phoenix_VarnishCache` extension version 4.0.x-4.2.x. It requires to add `oro_ajax`
route to the exception list of `Phoenix_VarnishCache` configuration in the backend.

# Customizations 

You can introduce additional placeholders for dynamic elements of the page. Configuration example:

```xml
<action method="registerPlaceholder">
    <xpath>block[@name="messages"]</xpath><!-- XPATH for block that need to replace -->
    <placeholder><!-- Placeholder -->
        <callback>oro_ajax::isCacheContent</callback><!-- Optional: Replace only if Helper callback returns true -->
        <block>oro_ajax/placeholder_messages</block><!-- Optional: Placeholder block, default: oro_ajax/placeholder -->
        <id>messages-placeholder</id><!-- Optional: Block ID -->
        <template>oro/ajax/placeholder.phtml</template><!-- Optional: Block template -->
        <element><!-- Optional: default placeholder data -->
            <tag>div</tag><!-- Placeholder element tag -->
            <attributes><!-- Placeholder element attributes -->
                <id>messages-placeholder</id>
            </attributes>
            <content>&nbsp;</content><!-- Default content -->
        </element>
    </placeholder>
    <updater><!-- JS Update rule -->
        <key>messages</key><!-- Key name in response -->
        <rule><!-- Update rule -->
            <id>messages-placeholder</id><!-- update by ID -->
            <css>#header div.links</css><!-- Or: update by CSS selector (first element only) -->
            <replace>1</replace><!-- Optional: Replace instead of update content -->
            <insert>top</insert><!-- Optional: Insert into top, bottom, before or after -->
        </rule>
    </updater>
</action>
```


