package com.stylex.util;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.util.XPathHelper;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.Map;
import java.util.Scanner;

public class CSSInfoCollector {

    private static final String COLLECT_CSS_JS_FILE_PATH = "elements-css-collector.js";
    private static final String COLLECT_CSS_JS_FILE_CONTENTS = readResourceFileToString(COLLECT_CSS_JS_FILE_PATH);
    private static final String COLLECT_CSS_COMPUTED_STYLES_METHOD_NAME = "getComputedStylesForXPath";

    private static final String COLLECT_ELEMENTS_INFO_JS_FILE_PATH = "get-elements-features-values.js";
    private static final String COLLECT_ELEMENTS_INFO_CONTENTS = readResourceFileToString(COLLECT_ELEMENTS_INFO_JS_FILE_PATH);
    private static final String COLLECT_ELEMENTS_INFO_METHOD_NAME = "getElementsInfo";
    private static final String HIGHLIGHT_ELEMENT_JS_FUNCTION_NAME = "highlightEvents";

    private static String readResourceFileToString(String resourceFilePath) {
        try {
            ClassLoader classLoader = CSSInfoCollector.class.getClassLoader();
            InputStream resourceInputStream = classLoader.getResourceAsStream(resourceFilePath);
            Scanner scanner = new Scanner(resourceInputStream).useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    public static Map<String, String> getComputedStyles(Element element, EmbeddedBrowser browser) {
        String functionCall = String.format("return %s(\"%s\")",
                COLLECT_CSS_COMPUTED_STYLES_METHOD_NAME,
                XPathHelper.getXPathExpression(element)
        );

        return (Map<String, String>) browser.executeJavaScript(COLLECT_CSS_JS_FILE_CONTENTS + functionCall);

    }

    public static Map<String, Object> getElementsInfo(String tagName, EmbeddedBrowser browser) {
        String functionCall = String.format("return %s(\"%s\")",
                COLLECT_ELEMENTS_INFO_METHOD_NAME,
                tagName
        );

        return (Map<String, Object>) browser.executeJavaScript(COLLECT_ELEMENTS_INFO_CONTENTS + functionCall);
    }

    public static void highlightElement(EmbeddedBrowser browser, String xPathExpression, String eventsLabel) {
        String js = HIGHLIGHT_ELEMENT_JS_FUNCTION_NAME + "(\"%s\", \"%s\")";
        String functionCall = String.format(js, xPathExpression, eventsLabel);
        browser.executeJavaScript(COLLECT_ELEMENTS_INFO_CONTENTS + functionCall);
    }
}
