var getElementsInfo = function(tagName) {
    var elements = document.getElementsByTagName(tagName);
    var toReturn = {};
    for (var element of elements) {
        xPath = getXPath(element);
        toReturn[xPath] = {
            boundingBox: getBoundingBox(element),
            computedStyles: getComputedStyles(element)
        }
    }
    return toReturn;
}

var getBoundingBox = function(element) {
    var rect = element.getBoundingClientRect();
    return {
        left: rect.width + window.scrollX,
        top: rect.top + window.scrollY,
        width: rect.width,
        height: rect.height
    }
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

var getXPath = function(element) {

    if (element.xpath) {
        return element.xpath;
    }

    var parent = element.parentNode

    if ((parent == null) || parent.nodeName.includes("#document")) {
        return "/" + element.nodeName + "[1]";
    }

    var id = element.getAttribute("id");
    if(id != null) {
        return "//" + element.nodeName + "[@id = '" + id + "']";
    }

    var buffer = []
    if (parent != element) {
        buffer.push(getXPath(parent));
        buffer.push("/");
    }
    buffer.push(element.nodeName);
    siblings = getSiblingsWithTheSameTagName(element);
    for (var i = 0; i < siblings.length; i++) {
        el = siblings[i];
        if (el == element) {
            buffer.push('[');
            buffer.push(i + 1);
            buffer.push(']');
            break;
        }
    }
    var xPath = buffer.join("");
    element.xpath = xPath;
    return xPath;
}

var getSiblingsWithTheSameTagName = function(element) {
    var result = [];
    var children = element.parentNode ? element.parentNode.children : []
    for (var i = 0; i < children.length; i++) {
        var c = children[i];
        if (c.nodeName == element.nodeName) {
            result.push(c);
        }
    }
    return result;
}

var getElementFromXPath = function(xpath) {
    return document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
}
var highlightEvents = function(xpath, label) {
    var element = getElementFromXPath(xpath);
    element.style.setProperty('border', 'solid 1px red', 'important');
    element.addEventListener("mouseenter", function(event) {
        console.log(xpath + " is predicted to have " + label)
    }, false);
}
