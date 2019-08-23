var getElementFromXPath = function(xpath) {
    return document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
}

var getAppliedCSS = function(element) {
    var sheets = document.styleSheets, o = [];
    element.matches = element.matches ||
        element.webkitMatchesSelector ||
        element.mozMatchesSelector ||
        element.msMatchesSelector ||
        element.oMatchesSelector;
    for (var i in sheets) {
        var rules = sheets[i].rules || sheets[i].cssRules;
        for (var r in rules) {
            if (element.matches(rules[r].selectorText)) {
            		var cssSource = sheets[i].href ? "EXTERNAL_CSS" : "EMBEDDED_CSS";
                o.push({ source: cssSource, css: rules[r].cssText, url: sheets[i].href });
            }
        }
    }
    var styleAttribute = element.getAttribute("style");
    if (styleAttribute) {
        o.push({ source: "INLINE_CSS", css: styleAttribute, url: null })
    }
    return o;
}

var getAppliedCSSForXPath = function(xpath) {
    var element = getElementFromXPath(xpath);
    return getAppliedCSS(element);
}

var getComputedStyles = function(element) {
    var computedStylesMap = { };
    computedStyles = window.getComputedStyle(element, null);
    for (var i = 0; i < computedStyles.length; i++) {
        var property = computedStyles[i];
        var value = computedStyles.getPropertyValue(property);
        computedStylesMap[property] = value;
    }
    return computedStylesMap;
}

var getComputedStylesForXPath = function(xpath) {
    var element = getElementFromXPath(xpath);
    return getComputedStyles(element);
}

