package com.stylex.distance;

import com.stylex.chrome.ChromeSessionHandler;
import com.stylex.util.Util;
import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.condition.eventablecondition.ElementCondition;
import com.crawljax.core.ElementCrawlEventsProvider;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.plugin.OnBrowserCreatedPlugin;
import com.crawljax.core.plugin.PreCrawlingPlugin;
import com.crawljax.core.state.Eventable;
import com.crawljax.util.XPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ChromeEventsCondition implements ElementCondition, ElementCrawlEventsProvider, PreCrawlingPlugin, OnBrowserCreatedPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromeEventsCondition.class);

    private final boolean clickOnly;
    private URI initialUrl;
    private Map<String, List<String>> eventsCache = new HashMap<>();

    public ChromeEventsCondition(boolean clickOnly) {
        this.clickOnly = clickOnly;
    }

    @Override
    public boolean check(EmbeddedBrowser browser, Element element) {
        if (Util.isClickableByDefault(element)) {
            return true;
        }
        String xpath = XPathHelper.getXPathExpression(element);
        List<String> eventsWithListenersAttached = ChromeSessionHandler.getEventsWithListenersAttached(xpath);
        eventsCache.put(xpath, eventsWithListenersAttached);
        if (clickOnly) {
            return eventsWithListenersAttached.contains("click");
        } else {
            return eventsWithListenersAttached.size() > 0;
        }
    }

    @Override
    public List<Eventable.EventType> getEventTypes(EmbeddedBrowser browser, Element element) {
        String xpath = XPathHelper.getXPathExpression(element);
        List<String> events = eventsCache.computeIfAbsent(xpath, ChromeSessionHandler::getEventsWithListenersAttached);
        LinkedList<Eventable.EventType> eventTypes = new LinkedList<>();
        for (String event : events) {
            switch (event) {
                case "click":
                case "mouseover":
                case "mousedown":
                case "mouseout":
                case "touchstart":
                    eventTypes.add(Eventable.EventType.ofJavaScriptEventName(event));
            }
        }
        if (!events.contains("click") && Util.isClickableByDefault(element)) {
            eventTypes.add(Eventable.EventType.click);
        }
        return eventTypes;
    }


    @Override
    public void onBrowserCreated(EmbeddedBrowser newBrowser) {
        ChromeSessionHandler.get(initialUrl);
    }

    @Override
    public void preCrawling(CrawljaxConfiguration config) throws RuntimeException {
        this.initialUrl = config.getUrl();
    }
}
