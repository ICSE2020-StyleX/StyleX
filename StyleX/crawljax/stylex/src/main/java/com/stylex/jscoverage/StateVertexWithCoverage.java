package com.stylex.jscoverage;

import com.stylex.conditions.EventMode;
import com.stylex.chrome.ChromeSessionHandler;
import com.stylex.chrome.CoverageInfo;
import com.stylex.util.Util;
import com.crawljax.core.state.StateVertexImpl;
import com.crawljax.util.DomUtils;
import com.crawljax.util.XPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.*;

public class StateVertexWithCoverage extends StateVertexImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateVertexWithCoverage.class);

    private final Map<String, CoverageInfo> currentCoverageInfo;
    private final double coveragePercentageSoFar;
    private final long explorationTimeNanos;

    private transient final Map<String, Set<String>> eventsAttachedByPropagation = new HashMap<>();
    private final Map<String, Map<String, EventMode>> eventListeners = new HashMap<>();

    /**
     * Defines a State with coverage info.
     *
     * @param id
     * @param url         the current url of the state
     * @param name        the name of the state
     * @param dom         the current DOM tree of the browser
     * @param strippedDom
     */
    public StateVertexWithCoverage(int id, String url, String name, String dom, String strippedDom, long crawlStartTime) {
        super(id, url, name, dom, strippedDom);
        this.explorationTimeNanos = System.nanoTime() - crawlStartTime;
        Map<String, CoverageInfo> stringCoverageInfoMap = ChromeSessionHandler.getLastCoverageAvailable();
        Map<String, CoverageInfo> currentCoverageInfo = new HashMap<>();
        long total = 0;
        long covered = 0;
        for (String scriptHash : stringCoverageInfoMap.keySet()) {
            CoverageInfo coverageInfo = stringCoverageInfoMap.get(scriptHash);
            currentCoverageInfo.put(scriptHash, coverageInfo.clone());
            total += coverageInfo.getScriptLength();
            covered += coverageInfo.getNumberOfCharactersCovered();
        }
        double coveragePercentageSoFar = (double) covered / total * 100;
        LOGGER.info("Coverage for {}: {}", name, coveragePercentageSoFar);
        this.currentCoverageInfo = currentCoverageInfo;
        this.coveragePercentageSoFar = coveragePercentageSoFar;
        getElementsEventsInfo(strippedDom);
    }

    private void getElementsEventsInfo(String dom) {
        Document document = null;
        try {
            document = DomUtils.asDocument(dom);
            List<Element> allBodyElements = Util.bfs((Element) document.getElementsByTagName("body").item(0));
            for (Element element : allBodyElements) {
                String xpath = XPathHelper.getXPathExpression(element);
                eventListeners.put(xpath, getEventTypesForElement(element, xpath));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, EventMode> getEventTypesForElement(Element element, String xpath) {
        List<String> eventsWithListenersAttached = ChromeSessionHandler.getEventsWithListenersAttached(xpath);
        Map<String, EventMode> allAttachedEventsForThisElement = new HashMap<>();
        for (String event : eventsWithListenersAttached) {
            allAttachedEventsForThisElement.put(event, EventMode.ATTACHED_BY_EVENT_HANDLER);
        }

        if (Util.isClickableByDefault(element) /*&& !allAttachedEventsForThisElement.containsKey("click")*/) {
            allAttachedEventsForThisElement.put("click", EventMode.ATTACHED_BY_DEFAULT);
        }

        if (eventsAttachedByPropagation.containsKey(xpath)) {
            for (String eventFromPropagation : eventsAttachedByPropagation.get(xpath)) {
                if (!allAttachedEventsForThisElement.containsKey(eventFromPropagation)) {
                    allAttachedEventsForThisElement.put(eventFromPropagation, EventMode.ATTACHED_BY_PROPAGATION);
                }
            }
        }

        for (String event : allAttachedEventsForThisElement.keySet()) {
            EventMode eventMode = allAttachedEventsForThisElement.get(event);
            LOGGER.debug("{} has listener for {} attached: {}", xpath, event, eventMode);
            if (eventMode == EventMode.ATTACHED_BY_DEFAULT || eventMode == EventMode.ATTACHED_BY_EVENT_HANDLER) {
                // All the children are also have the attached event
                // This might not be necessarily true (if the propagation is canceled for a child),
                // but there is no way to figure this out for now
                if (!"HTML".equalsIgnoreCase(element.getNodeName()) && !"BODY".equalsIgnoreCase(element.getNodeName())) {
                    List<Element> allChildren = Util.bfs(element);
                    for (Element child : allChildren) {
                        if (child != element) {
                            String childXPathExpression = XPathHelper.getXPathExpression(child);
                            Set<String> eventsForThisChild = eventsAttachedByPropagation.computeIfAbsent(childXPathExpression, k -> new HashSet<>());
                            eventsForThisChild.add(event);
                        }
                    }
                }
            }
        }
        return allAttachedEventsForThisElement;
    }

    public Map<String, CoverageInfo> getCurrentCoverageInfo() {
        return currentCoverageInfo;
    }

    public double getCoveragePercentageSoFar() {
        return coveragePercentageSoFar;
    }

    public long getExplorationTimeNanos() {
        return explorationTimeNanos;
    }

    public CoverageInfo getCoverageInfoForScriptID(String coverageID) {
        if (this.currentCoverageInfo != null) {
            return this.currentCoverageInfo.get(coverageID);
        }
        return null;
    }

    public Set<String> getAllIDsForScriptsCovered() {
        return new HashSet<>(this.currentCoverageInfo.keySet());
    }

    public Map<String, Map<String, EventMode>> getEventListeners() {
        return eventListeners;
    }
}
