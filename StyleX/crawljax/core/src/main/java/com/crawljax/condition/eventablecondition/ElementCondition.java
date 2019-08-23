package com.crawljax.condition.eventablecondition;

import com.crawljax.browser.EmbeddedBrowser;
import org.w3c.dom.Element;

public interface ElementCondition {

    public boolean check(EmbeddedBrowser browser, Element element);

}
