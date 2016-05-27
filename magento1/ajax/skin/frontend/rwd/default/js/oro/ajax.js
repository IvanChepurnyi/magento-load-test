/**
 * @category   skin
 * @package    base_default
 * @copyright  Copyright (c) 2016 Oro Inc. DBA MageCore (http://www.magecore.com)
 */

var oroAjax = Class.create();
oroAjax.prototype = {
    initialize: function (config) {
        this.config = config || {};
        this.formKey = config.formKey || null;
        this.customer = false;
        this.callbacks = [];
        this.updaters = [];
        this.useLoader = false;
        this.isLoader = false;
        this.loadWrapper = null;
        this.loadElement = null;
        this.cartObject = null;

        if (this.config.updaters) {
            this.config.updaters.map(function (updater) {
                this.updaters.push(updater);
            }.bind(this));
        }

        this.statusRequest();
    },
    setStatusUrl: function (url) {
        this.config.statusUrl = url;
    },
    statusRequest: function () {
        if (!this.config.statusUrl) {
            return;
        }
        this.useLoader = false;
        this.callRequest(this.config.statusUrl + '?rnd=' + Math.random());
    },
    registerCallback: function (callback) {
        this.callbacks.push(callback);
    },
    onCreate: function (request) {
        request.transport.withCredentials = true;
        if (this.useLoader) {
            this.setLoadWaiting(true);
        }
    },
    onComplete: function (response) {
        if (this.useLoader) {
            this.setLoadWaiting(false);
        }
    },
    onFailure: function (response) {
    },
    onSuccess: function (response) {
        var json;
        if (response.responseJSON) { // content-type: application/json
            json = response.responseJSON;
        } else if (response.responseText) { // content-type: text/html
            try {
                json = eval('(' + response.responseText + ')');
            } catch (e) {
                json = {};
            }
        }

        this.processResponseJson(json);
    },
    processResponseJson: function (json) {
        if (document.readyState != 'complete' && document.readyState != 'interactive') { // wait for load DOM
            document.observe('dom:loaded', this.processResponseJson.bind(this, json));
            return;
        }

        if (json.form_key) { // register form_key
            this.replaceFormKey(json.form_key);
        }
        if ('customer' in json) {
            this.customer = json.customer;
        }
        if (json.redirect) { // redirect to page
            window.location = json.redirect;
        }

        // run registered updaters
        this.updaters.map(function (updater) {
            try {
                this.processUpdater(updater, json);
            } catch (e) {
                console.trace(e);
            }
        }.bind(this));

        // run registered response callbacks
        this.callbacks.map(function (callback) {
            try {
                callback(json);
            } catch (e) {
                console.trace(e);
            }
        }.bind(this));
    },
    callRequest: function (url, onSuccess, onFailure, onCreate, onComplete) {
        new Ajax.Request(url, {
            method: 'get',
            onCreate: onCreate || this.onCreate.bind(this),
            onComplete: onComplete || this.onComplete.bind(this),
            onSuccess: onSuccess || this.onSuccess.bind(this),
            onFailure: onFailure || this.onFailure.bind(this)
        });
    },
    postRequest: function (url, params, onSuccess, onFailure, onCreate, onComplete) {
        new Ajax.Request(url, {
            method: 'post',
            parameters: params || {},
            onCreate: onCreate || this.onCreate.bind(this),
            onComplete: onComplete || this.onComplete.bind(this),
            onSuccess: onSuccess || this.onSuccess.bind(this),
            onFailure: onFailure || this.onFailure.bind(this)
        });
    },
    evalScripts: function (scripts) {
        var code = scripts.join(";\n");
        try {
            if (window.execScript) {
                window.execScript(code);
            } else if (Object.prototype.toString.call(window.HTMLElement).indexOf('Constructor') > 0) { // Safari
                window.setTimeout(code, 0);
            } else {
                eval.call(window, code);
            }
        } catch (e) {
            console.log(e);
        }
    },
    triggerEvent: function (element, eventName) {
        if (document.createEvent) { // webkit, gecko, safari
            var event = document.createEvent('HTMLEvents');
            event.initEvent(eventName, true, true);

            return element.dispatchEvent(event);
        }

        if (element.fireEvent) { // IE
            return element.fireEvent('on' + eventName);
        }
    },
    registerUpdater: function (key, rule, callback) {
        if (typeof rule != 'object') {
            return;
        }
        var updater = {
            key: key,
            rule: rule,
            callback: callback || false
        };
        this.updaters.push(updater);
    },
    getElementByRule: function (rule) {
        var element = null;

        // define element match rule
        if (rule.id) {
            element = $(rule.id);
        } else if (rule.css) {
            element = $$(rule.css).first();
        } else if (rule.element && Object.isElement(rule.element)) {
            element = rule.element;
        }

        return element;
    },
    processUpdater: function (updater, json) {
        var key = updater.key;
        if (!Object.isString(key) || !(key in json)) {
            return;
        }
        var content = json[key];
        if (!Object.isString(content) || content == '') {
            return;
        }
        var scripts = content.extractScripts();
        content = content.stripScripts();

        var element = this.getElementByRule(updater.rule);

        if (!element) {
            return;
        }

        // update element
        if (updater.rule.replace) {
            Element.replace(element, content);
        } else if (updater.rule.insert && ['top', 'bottom', 'before', 'after'].indexOf(updater.rule.insert) != -1) {
            var insertions = {};
            insertions[updater.rule.insert] = content;
            Element.insert(element, insertions);
        } else {
            Element.update(element, content);
        }

        this.evalScripts(scripts);

        if (updater.callback) {
            var callback = updater.callback;
            if (Object.isString(updater.callback)) { // map to object method
                callback = this[updater.callback].bind(this);
            }
            if (!Object.isFunction(callback)) {
                return;
            }
            try {
                callback(json);
            } catch (e) {
                //console.trace(e);
            }
        }
    },
    maximizeElement: function (element) {
        var browseElement = document.body;
        if (this.config.browseElement) {
            browseElement = this.getElementByRule(this.config.browseElement) || browseElement;
        }
        var browseDims = browseElement.getDimensions();

        // set the style of the element so it is centered
        var styles = {
            position: 'absolute',
            top: 0,
            left: 0,
            width: browseDims.width + 'px',
            height: browseDims.height + 'px'
        };

        element.setStyle(styles);
    },
    centerElement: function (element) {
        // retrieve required dimensions
        var elementDims = element.getDimensions();
        var viewDims = $(document).viewport.getDimensions();
        var scrollTop = $(document).viewport.getScrollOffsets().top;

        // calculate the center of the page
        var y = ((viewDims.height - elementDims.height) / 2) + scrollTop;
        var x = (viewDims.width - elementDims.width) / 2;

        // set the style of the element so it is centered
        var styles = {
            position: 'absolute',
            top: y + 'px',
            left: x + 'px'
        };

        element.setStyle(styles);
    },
    getLoaderWrapper: function () {
        if (this.loadWrapper) {
            return this.loadWrapper;
        }
        if (!this.config.loadWrapperContent) {
            this.config.loadWrapperContent = '<div id="ajax-wrapper" style="display: none;"></div>';
        }
        Element.insert(document.body, {
            top: this.config.loadWrapperContent
        });

        this.loadWrapper = $('ajax-wrapper');
        if (this.loadWrapper) {
            this.maximizeElement(this.loadWrapper);
            Event.observe(window, 'resize', this.maximizeElement.bind(this, this.loadWrapper));
        }

        return this.loadWrapper;
    },
    getLoaderElement: function () {
        if (this.loadElement) {
            return this.loadElement;
        }

        if (!this.config.loadElementContent) {
            var message = 'Please wait...';
            if (Translator) {
                message = Translator.translate(message);
            }
            this.config.loadElementContent = '<div id="ajax-loader" style="display: none;"><div class="txt">'
                + '<div class="img"></div>' + message + '</div></div>';
        }
        Element.insert(document.body, {
            top: this.config.loadElementContent
        });

        this.loadElement = $('ajax-loader');
        if (this.loadElement) {
            this.centerElement(this.loadElement);
            Event.observe(window, 'resize', this.centerElement.bind(this, this.loadElement));
            Event.observe(window, 'scroll', this.centerElement.bind(this, this.loadElement));
        }

        return this.loadElement;
    },
    setLoadWaiting: function (flag) {
        if (flag) { // show
            if (this.isLoader) {
                return;
            }
            this.isLoader = true;
            if (this.getLoaderWrapper()) {
                this.getLoaderWrapper().show();
            }
            if (this.getLoaderElement()) {
                this.getLoaderElement().show();
            }
        } else { // hide
            if (!this.isLoader) {
                return;
            }
            this.isLoader = false;

            if (this.getLoaderWrapper()) {
                this.getLoaderWrapper().hide();
            }
            if (this.getLoaderElement()) {
                this.getLoaderElement().hide();
            }
        }
    },
    replaceFormKey: function(formKey) {
        if (this.formKey && this.formKey != formKey) {
            $$('input[name="form_key"]').map(function(element){
                element.value = formKey;
            });
            $$('a[href*="form_key"]').map(function(element){
                element.href = element.href.replace(/\/form_key\/[^/]+/, '/form_key/' + formKey);
            });
            $$('form[action*="form_key"]').map(function(element){
                element.action = element.action.replace(/\/form_key\/[^/]+/, '/form_key/' + formKey);
            });
            $$('[onclick*="form_key"]').map(function(element){
                element.writeAttribute('onclick', element.readAttribute('onclick').replace(/\/form_key\/[^/]+/, '/form_key/' + formKey));
            });
        }

        this.formKey = formKey;
    }
};

var oroAjaxCart = Class.create();
oroAjaxCart.prototype = {
    initialize: function (oroAjax, urlTemplate, options) {
        this.ajaxObject  = oroAjax;
        this.urlTemplate = urlTemplate;
        this.options     = {};
        this.miniCart    = false;
        this.addToButton = null;

        document.observe('dom:loaded', this.loadEvent.bind(this));
        this.ajaxObject.cartObject = this;
        this.ajaxObject.registerCallback(this.topCartCallback.bind(this));
    },
    loadEvent: function () {
        if (window.productAddToCartForm) {
            this.observeProductAddToCartForm(window.productAddToCartForm);
        }
        // handle add to cart location
        $$('.btn-cart[onclick*="setLocation"]').map(function(element){
            var match = element.readAttribute('onclick').match(/\/product\/(\d+)\//);
            if (match) {
                element.writeAttribute('onclick', 'return false;');
                element.observe('click', this.productAddLocationEvent.bind(this, match[1]));
            }
        }.bind(this));
    },
    initMiniCart: function () {
        if (this.miniCart == false) {
            this.miniCart = new Minicart({formKey: this.ajaxObject.formKey});
        }
    },
    topCartCallback: function (json) {
        // rwd workaround
        if ('top_cart' in json) {
            var skipContents = $j('.skip-content.block-cart');
            var skipLinks = $j('.skip-link.skip-cart');

            skipLinks.on('click', function (e) {
                e.preventDefault();

                var self = $j(this);
                // Use the data-target-element attribute, if it exists. Fall back to href.
                var target = self.attr('data-target-element') ? self.attr('data-target-element') : self.attr('href');

                // Get target element
                var elem = $j(target);

                // Check if stub is open
                var isSkipContentOpen = elem.hasClass('skip-active') ? 1 : 0;

                // Hide all stubs
                skipLinks.removeClass('skip-active');
                skipContents.removeClass('skip-active');

                // Toggle stubs
                if (isSkipContentOpen) {
                    self.removeClass('skip-active');
                } else {
                    self.addClass('skip-active');
                    elem.addClass('skip-active');
                }
            });

            $j('#header-cart').on('click', '.skip-link-close', function(e) {
                var parent = $j(this).parents('.skip-content');
                var link = parent.siblings('.skip-link');

                parent.removeClass('skip-active');
                link.removeClass('skip-active');

                e.preventDefault();
            });

            if ('minicart_expand' in json) {
                skipLinks.click();
                this.initMiniCart();
                this.miniCart.hideMessage();
                if (json['minicart_success']) {
                    this.miniCart.showSuccess(json['minicart_message']);
                } else {
                    this.miniCart.showError(json['minicart_message']);
                }
            }
        }
    },
    observeProductAddToCartForm: function (object) {
        object.form.submit = this.productAddToCartFormSubmit.bind(this, object.form, object.form.submit);
        object.submit = this.productAddToCartButtonSubmit.bind(this, object.submit);
    },
    productAddToCartButtonSubmit: function(original, button, url) {
        if (Object.isElement(button)) {
            if (button.tagName.toUpperCase() == 'BUTTON') {
                this.addToButton = button;
            } else if (button.up('button')) {
                this.addToButton = button.up('button');
            } else {
                this.addToButton = false;
            }
        }
        original(button, url);
    },
    productAddToCartFormSubmit: function (form, original) {
        if (form.action != '' && form.action.search('checkout/cart/add') == -1) {
            return original();
        }
        var url = this.urlTemplate.replace('%action%', 'add');
        this.ajaxObject.useLoader = true;
        this.ajaxObject.postRequest(url, Form.serialize(form));
    },
    productAddLocationEvent: function(productId, event) {
        var url = this.urlTemplate.replace('%action%', 'add');
        this.ajaxObject.useLoader = true;
        this.ajaxObject.postRequest(url, {product: productId, form_key: this.ajaxObject.formKey});

        return false;
    }
};
