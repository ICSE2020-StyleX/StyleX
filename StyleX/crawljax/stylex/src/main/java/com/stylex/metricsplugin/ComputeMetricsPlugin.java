package com.stylex.metricsplugin;

import com.stylex.conditions.EventMode;
import com.stylex.chrome.ChromeSessionHandler;
import com.stylex.chrome.CoverageInfo;
import com.stylex.distance.APTEDDistance;
import com.stylex.distance.Distance;
import com.stylex.jscoverage.StateVertexWithCoverage;
import com.stylex.util.Util;
import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.CrawlSession;
import com.crawljax.core.CrawlerContext;
import com.crawljax.core.ExitNotifier;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.plugin.*;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.StateFlowGraph;
import com.crawljax.core.state.StateVertex;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.crawljax.util.DomUtils;
import com.google.gson.Gson;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ComputeMetricsPlugin implements PostCrawlingPlugin, PreCrawlingPlugin, OnEventFiredPlugin, OnBrowserCreatedPlugin, OnUrlLoadPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeMetricsPlugin.class);

    private final Supplier<Long> getCrawlStartTimeSupplier;
    private final CrawlOverview crawlOverviewPlugin;
    private File outputDir;
    private boolean firstRun = true;
    private int eventsFired = 0;
    private URI initialURL;
    private int maxEvents;

    public ComputeMetricsPlugin(Supplier<Long> getCrawlStartTimeSupplier, CrawlOverview crawlOverviewPlugin, int maxEvents) {
        this.getCrawlStartTimeSupplier = getCrawlStartTimeSupplier;
        this.crawlOverviewPlugin = crawlOverviewPlugin;
        this.maxEvents = maxEvents;
    }

    @Override
    public void preCrawling(CrawljaxConfiguration config) throws RuntimeException {
        this.outputDir = config.getOutputDir();
        this.initialURL = config.getUrl();
    }

    @Override
    public void postCrawling(CrawlSession session, ExitNotifier.ExitStatus exitReason) {
        //writeInstanceDistances(session);
        writeAllCoverage(session);
        writeStateEventsInfo(session);
        crawlOverviewPlugin.postCrawling(session, exitReason);
        System.exit(-1);
    }

    private void writeStateEventsInfo(CrawlSession session) {
        List<StateVertex> allStates = new ArrayList<>(session.getStateFlowGraph().getAllStates());
        allStates.sort(Comparator.comparingLong(o -> ((StateVertexWithCoverage) o).getExplorationTimeNanos()));

        StringBuilder builder = new StringBuilder();
        builder.append("ID\tState\tAllNodes\tActionable\tActionableByDefault").append(System.lineSeparator());

        for (int i = 0; i < allStates.size(); i++) {
            StateVertexWithCoverage stateVertex = (StateVertexWithCoverage) allStates.get(i);
            Map<String, Map<String, EventMode>> eventListeners = stateVertex.getEventListeners();
            int allBodyNodesSize = 0;
            int actionablesSize = 0;
            int actionableByDefaultSize = 0;
            for (String xpath : eventListeners.keySet()) {
                Map<String, EventMode> stringEventModeMap = eventListeners.get(xpath);
                allBodyNodesSize++;
                if (hasInterestingEvent(stringEventModeMap)) {
                    actionablesSize++;
                    if (stringEventModeMap.containsKey("click") &&
                            stringEventModeMap.get("click") == EventMode.ATTACHED_BY_DEFAULT) {
                        actionableByDefaultSize++;
                    }
                }
            }

            builder.append(i)
                    .append("\t")
                    .append(stateVertex.getName()).append("\t")
                    .append(allBodyNodesSize).append("\t")
                    .append(actionablesSize).append("\t")
                    .append(actionableByDefaultSize).append("\t")
                    .append(System.lineSeparator());

            try {
                String distanceFileOutput = outputDir.getAbsolutePath() + File.separator + "actionables.txt";
                FileUtils.writeStringToFile(new File(distanceFileOutput), builder.toString(), Charsets.toCharset("UTF-8"), false);
                LOGGER.info("Wrote actionables info file to {}", distanceFileOutput);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean hasInterestingEvent(Map<String, EventMode> stringEventModeMap) {
        return stringEventModeMap.containsKey("click") ||
                stringEventModeMap.containsKey("mouseover") ||
                stringEventModeMap.containsKey("mouseout") ||
                stringEventModeMap.containsKey("mousedown") ||
                stringEventModeMap.containsKey("touchstart");

    }

    private void writeInstanceDistances(CrawlSession session) {
        List<StateVertex> allStates = new ArrayList<>(session.getStateFlowGraph().getAllStates());
        allStates.sort(Comparator.comparingLong(o -> ((StateVertexWithCoverage) o).getExplorationTimeNanos()));
        if (allStates.size() >= 2) {
            Distance aptedDistance = new APTEDDistance();
            //LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
            for (int i = 1; i < allStates.size(); i++) {
                StateVertex sourceStateVertex = allStates.get(i - 1);
                StateVertex newState = allStates.get(i);
                float structuralDistance = 0;
                float normalizedStructuralDistance = 0;
                try {
                    Document oldDocument = DomUtils.asDocument(sourceStateVertex.getStrippedDom());
                    Document newDocument = DomUtils.asDocument(newState.getStrippedDom());
                    /*
                    float stringDistance = levenshteinDistance.getDOMDistance(oldDocument, newDocument);
                    float normalizedStringDistance =
                            (float) stringDistance / Math.max(sourceStateVertex.getStrippedDom().length(), newState.getStrippedDom().length());
                    LOGGER.info("Levenshtein distance for {} and {}: {} (normalized: {})",
                            sourceStateVertex.getName(),
                            newState.getName(),
                            stringDistance,
                            normalizedStringDistance);*/
                    structuralDistance = aptedDistance.getDOMDistance(oldDocument, newDocument);
                    normalizedStructuralDistance = structuralDistance / Util.getNumberOfNodes(oldDocument);
                    LOGGER.info("APTED tree edit distance: {} (normalized: {})", structuralDistance, normalizedStructuralDistance);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                StringBuilder builder = new StringBuilder();
                builder.append(sourceStateVertex.getName())
                        .append(" to ")
                        .append(newState.getName())
                        //.append(": Levenshtein ")
                        //.append(stringDistance)
                        //.append(", Normalized Levenshtein ")
                        //.append(normalizedStringDistance)
                        .append(", APTED: ")
                        .append(structuralDistance)
                        .append(", Normalized APTED: ")
                        .append(normalizedStructuralDistance)
                        .append(System.lineSeparator());
                try {
                    String distanceFileOutput = outputDir.getAbsolutePath() + File.separator + "diversity.txt";
                    FileUtils.writeStringToFile(new File(distanceFileOutput), builder.toString(), Charsets.toCharset("UTF-8"), true);
                    LOGGER.info("Wrote distance info file to {}", distanceFileOutput);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void eventFired(CrawlerContext context, Eventable eventable, boolean isFired) {
        eventsFired++;
        Map<String, CoverageInfo> stringCoverageInfoMap = ChromeSessionHandler.takePreciseCoverageSoFarCovered();
        long total = 0;
        long covered = 0;
        for (String scriptHash : stringCoverageInfoMap.keySet()) {
            CoverageInfo coverageInfo = stringCoverageInfoMap.get(scriptHash);
            total += coverageInfo.getScriptLength();
            covered += coverageInfo.getNumberOfCharactersCovered();
        }
        String fireStatus = isFired ? "fired" : "was not able to fire";
        StateVertex currentState = context.getCurrentState();
        String coverageMessage = String.format("Event #%s at %s seconds: %s %s on %s in %s, covered %s of %s (%s%%)",
                eventsFired,
                (int) Math.floor((System.nanoTime() - getCrawlStartTimeSupplier.get()) / 1e+9),
                fireStatus,
                eventable.getEventType(),
                eventable.getIdentification().getValue(),
                currentState.getName(),
                covered,
                total,
                (double) covered / total * 100);
        LOGGER.info(coverageMessage);
        String eventCoverageFileOutput = this.outputDir.getAbsolutePath() + File.separator + "events-coverage.txt";
        try {
            FileUtils.writeStringToFile(new File(eventCoverageFileOutput),
                    coverageMessage +  System.lineSeparator(), Charsets.toCharset("UTF-8"), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String coverageFileName = this.outputDir.getAbsolutePath() + File.separator + "events-states" + File.separator + eventsFired + ".json";
        try {
            FileUtils.writeStringToFile(new File(coverageFileName), new Gson().toJson(currentState), Charsets.toCharset("UTF-8"));
            LOGGER.info("Wrote coverage info to {}", coverageFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (eventsFired == maxEvents) {
            postCrawling(context.getSession(), ExitNotifier.ExitStatus.MAX_STATES);
        }
    }

    private void writeAllCoverage(CrawlSession session) {
        StateFlowGraph stateFlowGraph = session.getStateFlowGraph();
        List<StateVertex> allStates = new ArrayList<>(stateFlowGraph.getAllStates());
        allStates.sort(Comparator.comparingLong(o -> ((StateVertexWithCoverage) o).getExplorationTimeNanos()));
        StateVertexWithCoverage lastState = (StateVertexWithCoverage) allStates.get(allStates.size() - 1);
        Map<String, CoverageInfo> lastStateCoverageInfo = lastState.getCurrentCoverageInfo();
        int totalScriptsLength = 0;
        for (String hash : lastStateCoverageInfo.keySet()) {
            CoverageInfo coverageInfo = lastStateCoverageInfo.get(hash);
            totalScriptsLength += coverageInfo.getScriptLength();
        }
        StringBuilder builder = new StringBuilder();
        for (StateVertex state : allStates) {
            if (state instanceof StateVertexWithCoverage) {
                double totalCoveredInThisState = 0;
                StateVertexWithCoverage stateVertexWithCoverage = (StateVertexWithCoverage) state;
                for (String scriptId : lastStateCoverageInfo.keySet()) {
                    CoverageInfo coverageInfoForScriptID = stateVertexWithCoverage.getCoverageInfoForScriptID(scriptId);
                    if (null != coverageInfoForScriptID) {
                        totalCoveredInThisState += coverageInfoForScriptID.getNumberOfCharactersCovered();
                    }
                }
                double coverageForThisState = totalCoveredInThisState / totalScriptsLength * 100;
                LOGGER.info("Final coverage for {}: {}", state.getName(), coverageForThisState);
                builder.append("Final coverage for ")
                        .append(state.getName())
                        .append(" (")
                        .append((int)Math.floor(stateVertexWithCoverage.getExplorationTimeNanos() / 1e+9))
                        .append(") ")
                        .append(": ")
                        .append(coverageForThisState)
                        .append(System.lineSeparator());
            }
            serializeVertex(state);
        }
        LOGGER.info("Total # JS characters: {}", totalScriptsLength);
        LOGGER.info("Total number of JS files discovered: {}", lastStateCoverageInfo.size());
        builder.append("Total # JS characters: ").append(totalScriptsLength).append(System.lineSeparator());
        builder.append("Total number of JS code blocks discovered: ").append(lastStateCoverageInfo.size()).append(System.lineSeparator());

        double totalTime = (System.nanoTime() - getCrawlStartTimeSupplier.get()) / 1e+9;
        LOGGER.info("Total time taken: {} seconds", totalTime);
        builder.append("Total time taken: ").append(totalTime).append(" seconds").append(System.lineSeparator());

        LOGGER.info("Total events fired: {}", eventsFired);
        builder.append("Total events fired: ").append(eventsFired);

        String coverageFileOutput = this.outputDir.getAbsolutePath() + File.separator + "coverage.txt";
        try {
            FileUtils.writeStringToFile(new File(coverageFileOutput), builder.toString(), Charsets.toCharset("UTF-8"));
            LOGGER.info("Wrote coverage file to {}", coverageFileOutput);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void serializeVertex(StateVertex state) {
        String coverageFileName = this.outputDir.getAbsolutePath() + File.separator + "coverage" + File.separator + state.getName() + ".json";
        try {
            FileUtils.writeStringToFile(new File(coverageFileName), new Gson().toJson(state), Charsets.toCharset("UTF-8"));
            LOGGER.info("Wrote coverage info to {}", coverageFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBrowserCreated(EmbeddedBrowser newBrowser) {
        ChromeSessionHandler.get(initialURL);
    }

    @Override
    public void onUrlLoad(CrawlerContext context) {
        if (firstRun) {
            ChromeSessionHandler.initCoverage();
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

            Runnable periodicTask = () -> {
                Map<String, CoverageInfo> coverageMap = ChromeSessionHandler.takePreciseCoverageSoFarCovered();
                long total = 0;
                long covered = 0;
                for (String scriptHash : coverageMap.keySet()) {
                    CoverageInfo coverageInfo = coverageMap.get(scriptHash);
                    total += coverageInfo.getScriptLength();
                    covered += coverageInfo.getNumberOfCharactersCovered();
                }
                double time = (int) Math.floor((System.nanoTime() - getCrawlStartTimeSupplier.get()) / 1e+9);
                double coverage = (double) covered / total;
                String message = String.format("Coverage at %s = %s / %s = %s ", time, covered, total, coverage);
                LOGGER.info(message);
                try {
                    FileUtils.writeStringToFile(new File(outputDir.getAbsolutePath() + File.separator + "time-coverage.txt"),
                            time + "\t" + covered + "\t" + total + "\t" + coverage + System.lineSeparator(),
                            org.apache.commons.io.Charsets.toCharset("UTF-8"), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };

            executor.scheduleAtFixedRate(periodicTask, 0, 5, TimeUnit.SECONDS);
            firstRun = false;
        }
    }
}
