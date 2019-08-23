package com.crawljax.core;

import com.crawljax.browser.EmbeddedBrowser;

import com.crawljax.core.state.Eventable;
import org.w3c.dom.Element;

import java.util.List;

public interface ElementCrawlEventsProvider {
    public List<Eventable.EventType> getEventTypes(EmbeddedBrowser browser, Element element);
}
