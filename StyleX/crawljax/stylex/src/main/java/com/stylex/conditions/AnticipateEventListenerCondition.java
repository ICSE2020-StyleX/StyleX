package com.stylex.conditions;

import com.stylex.rintegration.REngine;
import com.stylex.rintegration.REngineUtil;
import com.stylex.util.CSSInfoCollector;
import com.stylex.util.Util;
import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.condition.eventablecondition.ElementCondition;
import com.crawljax.core.*;
import com.crawljax.core.configuration.ElementSorter;
import com.crawljax.core.plugin.OnEventFiredPlugin;
import com.crawljax.core.plugin.OnNewStatePlugin;
import com.crawljax.core.plugin.PostCrawlingPlugin;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.StateVertex;
import com.crawljax.util.DomUtils;
import com.crawljax.util.XPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.xpath.XPathExpressionException;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class AnticipateEventListenerCondition
        implements ElementCondition, ElementSorter, ElementCrawlEventsProvider,
        OnNewStatePlugin, PostCrawlingPlugin, OnEventFiredPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnticipateEventListenerCondition.class);
    private static final String R_MODEL_ENVIRONMENT_DATA_FILE = "eventables-model.RData";

    // The function name of the model for element prediction in R_MODEL_ENVIRONMENT_DATA_FILE
    private static final String EVENTABLES_MODEL_FUNCTION_NAME = "predict.handlers";
    // The function name of the model for clustering in R_MODEL_ENVIRONMENT_DATA_FILE
    private static final String CLUSTERING_FUNCTION_NAME = "cluster.elements";

    private final REngine rEngine;
    private final Map<String, Set<String>> currentEventables; // XPath -> Events
    private final Map<String, Map<String, Object>> featureVectors; // XPath -> feature vectors
    private final Map<Map<String, Object>, Integer> elementsExercisedSoFarFeatureVectors;
    private final boolean clickOnly;
    private final String tagName;
    private boolean requiresUpdate = true;

    private File outputFolder;

    public AnticipateEventListenerCondition(String tagName, boolean clickOnly) {
        this.tagName = tagName;
        this.clickOnly = clickOnly;
        this.rEngine = new REngine();
        rEngine.connect();
        String modelEnvironmentFilePath = null;
        try {
            modelEnvironmentFilePath =
                    new File(AnticipateEventListenerCondition.class.getClassLoader().getResource(R_MODEL_ENVIRONMENT_DATA_FILE).toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        try {
            LOGGER.info("Loading R environment data file {}", modelEnvironmentFilePath);
            rEngine.loadEnvironmentDataFile(modelEnvironmentFilePath);
            LOGGER.info("Loading packages: C50, cluster, apcluster");
            rEngine.loadLibrary("C50");
            rEngine.loadLibrary("cluster");
            rEngine.loadLibrary("apcluster");
        } catch (REngine.RCommandException e) {
            e.printStackTrace();
        }
        currentEventables = new HashMap<>();
        featureVectors = new HashMap<>();
        elementsExercisedSoFarFeatureVectors = new HashMap<>();
    }

    private void updateEventables(EmbeddedBrowser browser) {
        LOGGER.info("Updating clickables list");
        currentEventables.clear();
        featureVectors.clear();
        Document document = null;
        try {
            document = DomUtils.asDocument(browser.getStrippedDom());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (null == document) {
            LOGGER.warn("Unable to parse DOM in this state, couldn't get candidate elements");
            return;
        }

        // A map of model parameters -> values for each model parameter for each XPath in allInterestingElementsXPaths
        Map<String, List<Object>> modelMaps = new HashMap<>();
        List<String> allInterestingElementsXPaths = new ArrayList<>();
        LOGGER.info("Started collecting data for prediction");
        Map<String, Object> elementsInfo = CSSInfoCollector.getElementsInfo(tagName, browser);
        LOGGER.info("Finished collecting data for prediction");
        for (String xPath : elementsInfo.keySet()) {
            Object values = elementsInfo.get(xPath);
            if (values instanceof Map<?, ?>) {
                Map<String, Object> valuesMap = (Map<String, Object>) values;
                Element element = null;
                try {
                    element = DomUtils.getElementByXpath(document, xPath);
                } catch (XPathExpressionException e) {
                    e.printStackTrace();
                }
                if (null == element) {
                    continue;
                }
                if (Util.isClickableByDefault(element)) {
                    Set<String> events = currentEventables.computeIfAbsent(xPath, k -> new HashSet<>());
                    events.add("click");
                }
                allInterestingElementsXPaths.add(xPath);
                Rectangle2D.Double elementBoundingBox = getBoundingBox((Map<String, Object>) valuesMap.get("boundingBox"));
                Map<String, String> computedStyles = (Map<String, String>) valuesMap.get("computedStyles");
                int depth = Util.getDepth(element);
                int numberOfDescendants = Util.getNumberOfDescendants(element);
                int subtreeHeight = Util.getNodeSubtreeHeight(element);
                Map<String, Object> modelMap =
                        getStylesModelMap(computedStyles, elementBoundingBox, depth, numberOfDescendants, subtreeHeight, true);
                featureVectors.put(xPath, modelMap);
                addModelMap(modelMaps, modelMap);
            }
        }
        LOGGER.info("Started prediction");
        String RFeatureVectorString = REngineUtil.getDataFrameDeclarationFromMap(modelMaps);
        String predictString = String.format("%s(%s)",
                EVENTABLES_MODEL_FUNCTION_NAME,
                RFeatureVectorString);
        try {
            Map<?, ?> predictedEventablesMap = (Map<?, ?>) this.rEngine.evaluate(predictString);
            for (Object eventObject : predictedEventablesMap.keySet()) {
                String event = eventObject.toString();
                if (!clickOnly || "click".equalsIgnoreCase(event)) {
                    List<?> l = (List<?>) predictedEventablesMap.get(event);
                    for (int i = 0; i < l.size(); i++) {
                        Object predictionObject = l.get(i);
                        String xPath = allInterestingElementsXPaths.get(i);
                        Set<String> events = currentEventables.computeIfAbsent(xPath, k -> new HashSet<>());
                        if ("true".equalsIgnoreCase(predictionObject.toString())) {
                            events.add(event);
                        }
                    }
                }
            }
        } catch (REngine.RCommandException e) {
            e.printStackTrace();
        }
        LOGGER.info("Finished prediction");
        /*for (String parentXPath : currentEventables.keySet()) {
            if (currentEventables.get(parentXPath).size() != 0) {
                ChromeSessionHandler.highlightNode(parentXPath);
            }
        }*/
    }

    private Rectangle2D.Double getBoundingBox(Map<String, Object> boundingBox) {
        double x = Util.getDoubleValue(boundingBox.get("left"));
        double y = Util.getDoubleValue(boundingBox.get("top"));
        double width = Util.getDoubleValue(boundingBox.get("width"));
        double height = Util.getDoubleValue(boundingBox.get("height"));
        return new Rectangle2D.Double(x, y, width, height);
    }

    private void addModelMap(Map<String, List<Object>> modelMaps, Map<String, Object> modelMap) {
        for (String modelParameter : modelMap.keySet()) {
            List<Object> parameterValues = modelMaps.computeIfAbsent(modelParameter, k -> new ArrayList<>());
            parameterValues.add(modelMap.get(modelParameter));
        }
    }

    @Override
    public boolean check(EmbeddedBrowser browser, Element element) {
        if (requiresUpdate) {
            updateEventables(browser);
            requiresUpdate = false;
        }
        String xPathExpression = XPathHelper.getXPathExpression(element);
        Set<String> events = currentEventables.get(xPathExpression);
        boolean hasEvents = false;
        // TODO filter out parents when a child is clickable and look at the efficacy
        if (events != null) {
            hasEvents = events.size() > 0;
        }
        return hasEvents;
    }

    @Override
    public List<Eventable.EventType> getEventTypes(EmbeddedBrowser browser, Element element) {
        LinkedList<Eventable.EventType> eventTypes = new LinkedList<>();
        if (requiresUpdate) {
            updateEventables(browser);
            requiresUpdate = false;
        }
        String xPathExpression = XPathHelper.getXPathExpression(element);
        Set<String> events = currentEventables.get(xPathExpression);
        if (events.contains("click")) {
            eventTypes.add(Eventable.EventType.click);
            return eventTypes;
        } else {
            for (String event : events) {
                eventTypes.add(Eventable.EventType.ofJavaScriptEventName(event));
            }
            return eventTypes;
        }
    }

    private Map<String, Object> getStylesModelMap(Map<String, String> computedStyles,
                                                  Rectangle2D.Double boundingBox,
                                                  int depth,
                                                  int numberOfDescendants,
                                                  int subtreeHeight,
                                                  boolean binaryAsZeroAndOnes) {

        Map<String, Object> modelMap = new HashMap<>();

        modelMap.put("has.animation",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("animation-name").equalsIgnoreCase("none") ||
                                (!computedStyles.get("transition-property").equalsIgnoreCase("none") &&
                                        !computedStyles.get("transition-property").equalsIgnoreCase("all"))
                )
        );
        modelMap.put("has.bg",
                getBinaryValue(binaryAsZeroAndOnes,
                    !computedStyles.get("background-image").equalsIgnoreCase("none") ||
                        !computedStyles.get("background-color").equalsIgnoreCase("rgba(0, 0, 0, 0)")
                )
        );
        modelMap.put("has.border",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("border-top-style").equalsIgnoreCase("none") ||
                        !computedStyles.get("border-bottom-style").equalsIgnoreCase("none") ||
                        !computedStyles.get("border-left-style").equalsIgnoreCase("none") ||
                        !computedStyles.get("border-right-style").equalsIgnoreCase("none")
                )
        );
        modelMap.put("has.box.shadow",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("box-shadow").equalsIgnoreCase("none")
                )
        );
        modelMap.put("has.outline",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("outline-style").equalsIgnoreCase("none")
                )
        );
        modelMap.put("has.text.decoration",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("text-decoration-line").equalsIgnoreCase("none")
                )
        );
        modelMap.put("has.touch.action",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("touch-action").equalsIgnoreCase("auto")
                )
        );
        modelMap.put("has.transform",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("transform").equalsIgnoreCase("none")
                )
        );
        modelMap.put("has.will.change",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("will-change").equalsIgnoreCase("none")
                )
        );
        modelMap.put("has.z.index",
                getBinaryValue(binaryAsZeroAndOnes,
                        !computedStyles.get("z-index").equalsIgnoreCase("0") ||
                                !computedStyles.get("z-index").equalsIgnoreCase("auto")
                )
        );

        modelMap.put("boundingBox.x", boundingBox.x);
        modelMap.put("boundingBox.y", boundingBox.y);
        modelMap.put("boundingBox.width", boundingBox.getWidth());
        modelMap.put("boundingBox.height", boundingBox.getHeight());
        modelMap.put("depth", depth);
        modelMap.put("numberOfDescendants", numberOfDescendants);
        modelMap.put("subtreeHeight", subtreeHeight);

        modelMap.put("align_content", computedStyles.get("align-content"));
        modelMap.put("align_items", computedStyles.get("align-items"));
        modelMap.put("align_self", computedStyles.get("align-self"));
        modelMap.put("backface_visibility", computedStyles.get("backface-visibility"));
        modelMap.put("border_block_end_style", computedStyles.get("border-block-end-style"));
        modelMap.put("border_block_start_style", computedStyles.get("border-block-start-style"));
        modelMap.put("border_bottom_style", computedStyles.get("border-bottom-style"));
        modelMap.put("border_collapse", computedStyles.get("border-collapse"));
        modelMap.put("border_inline_end_style", computedStyles.get("border-inline-end-style"));
        modelMap.put("border_inline_start_style", computedStyles.get("border-inline-start-style"));
        modelMap.put("border_left_style", computedStyles.get("border-left-style"));
        modelMap.put("border_right_style", computedStyles.get("border-right-style"));
        modelMap.put("border_top_style", computedStyles.get("border-top-style"));
        modelMap.put("box_sizing", computedStyles.get("box-sizing"));
        modelMap.put("clear", computedStyles.get("clear"));
        modelMap.put("cursor", normalizeCursorValue(computedStyles.get("cursor")));
        modelMap.put("display", computedStyles.get("display"));
        modelMap.put("flex_direction", computedStyles.get("flex-direction"));
        modelMap.put("flex_grow", computedStyles.get("flex-grow"));
        modelMap.put("flex_wrap", computedStyles.get("flex-wrap"));
        modelMap.put("float", computedStyles.get("float"));
        modelMap.put("font_style", computedStyles.get("font-style"));
        modelMap.put("font_weight", computedStyles.get("font-weight"));
        modelMap.put("hyphens", computedStyles.get("hyphens"));
        modelMap.put("justify_content", computedStyles.get("justify-content"));
        modelMap.put("list_style_position", computedStyles.get("list-style-position"));
        modelMap.put("list_style_type", computedStyles.get("list-style-type"));
        modelMap.put("mix_blend_mode", computedStyles.get("mix-blend-mode"));
        modelMap.put("object_fit", computedStyles.get("object-fit"));
        modelMap.put("opacity", computedStyles.get("opacity"));
        modelMap.put("outline_style", computedStyles.get("outline-style"));
        modelMap.put("overflow_wrap", computedStyles.get("overflow-wrap"));
        modelMap.put("overflow_x", computedStyles.get("overflow-x"));
        modelMap.put("overflow_y", computedStyles.get("overflow-y"));
        modelMap.put("pointer_events", computedStyles.get("pointer-events"));
        modelMap.put("position", computedStyles.get("position"));
        modelMap.put("resize", computedStyles.get("resize"));
        modelMap.put("table_layout", computedStyles.get("table-layout"));
        modelMap.put("text_align", computedStyles.get("text-align"));
        modelMap.put("text_decoration_line", computedStyles.get("text-decoration-line"));
        modelMap.put("text_decoration_style", computedStyles.get("text-decoration-style"));
        modelMap.put("text_overflow", computedStyles.get("text-overflow"));
        modelMap.put("text_rendering", computedStyles.get("text-rendering"));
        modelMap.put("text_size_adjust", computedStyles.get("text-size-adjust"));
        modelMap.put("text_transform", computedStyles.get("text-transform"));
        modelMap.put("transform_style", computedStyles.get("transform-style"));
        modelMap.put("unicode_bidi", "plaintext".equalsIgnoreCase(computedStyles.get("unicode-bidi")) ? "normal" : computedStyles.get("unicode-bidi"));
        modelMap.put("user_select", computedStyles.get("user-select"));
        modelMap.put("visibility", computedStyles.get("visibility"));
        modelMap.put("white_space", computedStyles.get("white-space"));
        modelMap.put("word_break", computedStyles.get("word-break"));

        modelMap.put("color", computedStyles.get("color"));
        modelMap.put("background_color", computedStyles.get("background-color"));
        modelMap.put("animation_direction", computedStyles.get("animation-direction"));
        modelMap.put("animation_fill_mode", computedStyles.get("animation-fill-mode"));
        modelMap.put("animation_play_state", computedStyles.get("animation-play-state"));
        modelMap.put("animation_timing_function", computedStyles.get("animation-timing-function"));
        modelMap.put("background_attachment", computedStyles.get("background-attachment"));
        modelMap.put("background_blend_mode", computedStyles.get("background-blend-mode"));
        modelMap.put("background_clip", computedStyles.get("background-clip"));
        modelMap.put("background_origin", computedStyles.get("background-origin"));
        modelMap.put("touch_action", computedStyles.get("touch-action"));
        modelMap.put("transition_property", computedStyles.get("transition-property"));
        modelMap.put("widows", computedStyles.get("widows"));
        modelMap.put("will_change", computedStyles.get("will-change"));
        modelMap.put("padding_left", computedStyles.get("padding-left"));
        modelMap.put("padding_right", computedStyles.get("padding-right"));
        modelMap.put("padding_top", computedStyles.get("padding-top"));
        modelMap.put("padding_bottom", computedStyles.get("padding-bottom"));
        modelMap.put("margin_left", computedStyles.get("margin-left"));
        modelMap.put("margin_right", computedStyles.get("margin-right"));
        modelMap.put("margin_bottom", computedStyles.get("margin-bottom"));
        modelMap.put("margin_top", computedStyles.get("margin-top"));

        return modelMap;
    }

    private String normalizeCursorValue(String cursor) {
        switch (cursor) {
            case "alias":
            case "all-scroll":
            case "auto":
            case "cell":
            case "context-menu":
            case "col-resize":
            case "copy":
            case "crosshair":
            case "default":
            case "e-resize":
            case "ew-resize":
            case "grab":
            case "grabbing":
            case "help":
            case "move":
            case "n-resize":
            case "ne-resize":
            case "nesw-resize":
            case "ns-resize":
            case "nw-resize":
            case "nwse-resize":
            case "no-drop":
            case "none":
            case "not-allowed":
            case "pointer":
            case "progress":
            case "row-resize":
            case "s-resize":
            case "se-resize":
            case "sw-resize":
            case "text":
            case "w-resize":
            case "wait":
            case "zoom-in":
            case "zoom-out":
                return cursor;
            default:
                return "custom";
        }
    }

    private Object getBinaryValue(boolean binaryAsZeroAndOnes, boolean booleanValue) {
        if (binaryAsZeroAndOnes) {
            return booleanValue ? 1 : 0;
        } else {
            return booleanValue;
        }
    }

    @Override
    public void onNewState(CrawlerContext context, StateVertex newState) {
        requiresUpdate = true;
    }

    @Override
    public void postCrawling(CrawlSession session, ExitNotifier.ExitStatus exitReason) {
        this.rEngine.disconnect();
    }

    @Override
    public List<CandidateElement> sort(List<CandidateElement> candidateElementsToSort) {
        if (candidateElementsToSort.size() > 1) {

            //sortedList = sortWithClustering(candidateElementsToSort);


            LinkedList<CandidateElement> sortedList = new LinkedList<>(candidateElementsToSort);

            // First do clickables
            //moveClickablesFirst(candidateElementsToSort);

            // Push back elements for which we have done a similar element before
            Map<Integer, Integer> toMoveLastIndicesCountsMap = new HashMap<>();
            for (int candidateElementIndex = 0; candidateElementIndex < sortedList.size(); candidateElementIndex++) {
                CandidateElement candidateElement = sortedList.get(candidateElementIndex);
                String xpath = candidateElement.getIdentification().getValue();
                Map<String, Object> featureVector = featureVectors.get(xpath);
                featureVector = removeExtraFeatures(featureVector);
                if (elementsExercisedSoFarFeatureVectors.containsKey(featureVector)) {
                    Integer count = elementsExercisedSoFarFeatureVectors.get(featureVector);
                    toMoveLastIndicesCountsMap.put(candidateElementIndex, count);
                }
            }

            List<Integer> toMoveLastIndicesSorted = new ArrayList<>(toMoveLastIndicesCountsMap.keySet());
            Comparator<Integer> elementIndexComparator =
                    (index1, index2) -> Integer.compare(toMoveLastIndicesCountsMap.get(index1), toMoveLastIndicesCountsMap.get(index2));
            toMoveLastIndicesSorted.sort(elementIndexComparator);

            for (int index : toMoveLastIndicesSorted) {
                CandidateElement toMoveLast = sortedList.remove(index);
                sortedList.addLast(toMoveLast);
            }

            //Push back elements for which a parent is going to be clicked on
            sortedList = pushBackParents(sortedList);

            // Push back elements which are hidden. An element which is hidden should be always last
            sortedList = pushBackHiddenElements(sortedList);

            return sortedList;
        } else {
            return candidateElementsToSort;
        }
    }

    private LinkedList<CandidateElement> pushBackParents(LinkedList<CandidateElement> candidateElements) {
        LinkedList<CandidateElement> sortedList = new LinkedList<>();
        List<Integer> toMoveLastIndices = new ArrayList<>();
        Map<String, Integer> xPathsToIndices = new HashMap<>();
        for (int candidateElementIndex = 0; candidateElementIndex < candidateElements.size(); candidateElementIndex++) {
            CandidateElement candidateElement = candidateElements.get(candidateElementIndex);
            sortedList.add(candidateElement);
            String xpath = candidateElement.getIdentification().getValue();
            xPathsToIndices.put(xpath, candidateElementIndex);
        }
        for (int childIndex = 0; childIndex < candidateElements.size(); childIndex++) {
            CandidateElement candidateElement = candidateElements.get(childIndex);
            String childXPath = candidateElement.getIdentification().getValue();
            Set<String> childEvents = currentEventables.get(childXPath);
            String parentXpath = XPathHelper.getXPathExpression(candidateElement.getElement().getParentNode());
            Set<String> parentEvents = currentEventables.get(parentXpath);
            if (xPathsToIndices.containsKey(parentXpath) &&
                    childEvents.equals(parentEvents)) {
                toMoveLastIndices.add(xPathsToIndices.get(childXPath));
            }
        }
        for (int toMoveLastIndex : toMoveLastIndices) {
            CandidateElement candidateElement = sortedList.remove(toMoveLastIndex);
            sortedList.addLast(candidateElement);
        }
        return sortedList;
    }

    private LinkedList<CandidateElement> pushBackHiddenElements(List<CandidateElement> candidateElements) {
        LinkedList<CandidateElement> sortedList = new LinkedList<>(candidateElements);
        List<Integer> toMoveLastIndices = new ArrayList<>();
        for (int candidateElementIndex = 0; candidateElementIndex < candidateElements.size(); candidateElementIndex++) {
            CandidateElement candidateElement = candidateElements.get(candidateElementIndex);
            String xpath = candidateElement.getIdentification().getValue();
            Map<String, Object> featureVector = featureVectors.get(xpath);
            if (null != featureVector && isHidden(candidateElement, featureVector)) {
                toMoveLastIndices.add(candidateElementIndex);
            }
        }
        for (int toMoveToLastIndex : toMoveLastIndices) {
            CandidateElement candidateElement = sortedList.remove(toMoveToLastIndex);
            sortedList.addLast(candidateElement);
        }
        return sortedList;
    }

    private LinkedList<CandidateElement> moveClickablesFirst(List<CandidateElement> candidateElements) {
        LinkedList<CandidateElement> sortedList = new LinkedList<>(candidateElements);
        List<Integer> toDoFirst = new ArrayList<>();
        for (int candidateElementIndex = 0; candidateElementIndex < candidateElements.size(); candidateElementIndex++) {
            CandidateElement candidateElement = candidateElements.get(candidateElementIndex);
            if (Util.isClickableByDefault(candidateElement.getElement())) {
                toDoFirst.add(candidateElementIndex);
            }
        }
        for (int candidateElementIndex : toDoFirst) {
            CandidateElement candidateElement = sortedList.remove(candidateElementIndex);
            sortedList.addFirst(candidateElement);
        }
        return sortedList;
    }

    private List<CandidateElement> sortWithClustering(List<CandidateElement> candidateElementsToSort) {
        LinkedList<CandidateElement> sortedList = new LinkedList<>(candidateElementsToSort);
        try {
            List<Integer> clusteringExemplars = getElementsExemplarsWithClustering(candidateElementsToSort);
            for (int elementIndex: clusteringExemplars) {
                CandidateElement candidateElement = candidateElementsToSort.get(elementIndex);
                sortedList.remove(elementIndex);
                sortedList.addFirst(candidateElement);
                LOGGER.info("{} is an exemplar in clustering", candidateElement.getIdentification().getValue());
                //ChromeSessionHandler.highlightNode(candidateElement.getIdentification().getValue());
            }
            LOGGER.info("Finished clustering");
        } catch (REngine.RCommandException rce) {
            LOGGER.warn("Clustering failed");
            rce.printStackTrace();
        }
        return sortedList;
    }

    private List<Integer> getElementsExemplarsWithClustering(List<CandidateElement> candidateElements)
            throws REngine.RCommandException {
        LOGGER.info("Started clustering");
        Map<String, List<Object>> modelMaps = new HashMap<>();
        for (CandidateElement candidateElement : candidateElements) {
            String xpath = candidateElement.getIdentification().getValue();
            Map<String, Object> featureVectorForThisElement = featureVectors.get(xpath);
            addModelMap(modelMaps, featureVectorForThisElement);
        }
        String RFeatureVectorString = REngineUtil.getDataFrameDeclarationFromMap(modelMaps);
        String predictString = String.format("%s(%s)",
                CLUSTERING_FUNCTION_NAME,
                RFeatureVectorString);
        int[] evaluate = (int[]) this.rEngine.evaluate(predictString);
        List<Integer> clusteringExemplars = new ArrayList<>();
        Arrays.stream(evaluate).forEach(rIndex -> clusteringExemplars.add(rIndex - 1));
        return clusteringExemplars;
    }

    private boolean isHidden(CandidateElement candidateElement, Map<String, Object> featureVector) {
        if ("none".equalsIgnoreCase(featureVector.get("display").toString())) {
            return true;
        } else if ("hidden".equalsIgnoreCase(featureVector.get("visibility").toString())) {
            return true;
        } else if ("0".equals(featureVector.get("boundingBox.width")) ||
                "0".equals(featureVector.get("boundingBox.height"))) {
            return true;
        } else if (featureVector.get("boundingBox.x").toString().startsWith("-") ||
                featureVector.get("boundingBox.y").toString().startsWith("-")) {
            return true;
        }
        return false;
    }

    private Map<String, Object> removeExtraFeatures(Map<String, Object> originalFeatureVector) {
        Map<String, Object> copy = new HashMap<>(originalFeatureVector);
        copy.remove("boundingBox.x");
        copy.remove("boundingBox.y");
        //copy.remove("boundingBox.width");
        //copy.remove("boundingBox.height");
        copy.remove("depth");
        copy.remove("numberOfDescendants");
        copy.remove("subtreeHeight");
        return copy;
    }

    @Override
    public void eventFired(CrawlerContext context, Eventable eventable, boolean firingSuccessful) {
        int count = 0;
        String xPathExpression = eventable.getIdentification().getValue();
        Map<String, Object> originalFeatureVector = featureVectors.get(xPathExpression);
        if (originalFeatureVector != null) {
            Map<String, Object> copy = removeExtraFeatures(originalFeatureVector);
            if (elementsExercisedSoFarFeatureVectors.containsKey(copy)) {
                count = elementsExercisedSoFarFeatureVectors.get(copy);
            }
            if (firingSuccessful) {
                count += 1;
            } else {
                count += 5;
            }
            elementsExercisedSoFarFeatureVectors.put(copy, count);
        } else {
            LOGGER.warn("I was not able to find feature vector for {}", xPathExpression);
        }
    }
}
