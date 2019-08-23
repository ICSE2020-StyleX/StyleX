package com.stylex.app;

import com.stylex.metricsplugin.ComputeMetricsPlugin;
import com.stylex.jscoverage.StateVertexWithCoverage;
import com.stylex.conditions.AnticipateEventListenerCondition;
import com.stylex.distance.ChromeEventsCondition;
import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.core.CrawlerContext;
import com.crawljax.core.CrawljaxRunner;
import com.crawljax.core.configuration.BrowserConfiguration;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.plugin.OnUrlLoadPlugin;
import com.crawljax.core.state.StateVertex;
import com.crawljax.core.state.StateVertexFactory;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class StyleXMain implements Runnable {

    private static final long WAIT_TIME_AFTER_RELOAD = 1000;
    private static final long WAIT_TIME_AFTER_EVENT = 200;
    private static final int MAXIMUM_STATES = Integer.MAX_VALUE;
    private static final int MAXIMUM_DEPTH = Integer.MAX_VALUE;

    private enum ExperimentMode {
        STYLEX_PRIORITIZATION,
        STYLEX_NO_PRIORITIZATION,
        ALL_ELEMENTS,
        ALL_ELEMENTS_RANDOM,
        DEFAULT_ELEMENTS,
        CHROME_EVENTS
    }

    @Option(names = { "-m", "--mode" }, description = "Mode of experiments. Valid values: ${COMPLETION-CANDIDATES}",
            paramLabel = "MODE", required = true)
    private ExperimentMode experimentMode = null;

    @Option(names = { "-u", "--url" }, description = "URL of the web app.", paramLabel = "URL", required = true)
    private String url = null;

    @Option(names = { "-o", "--output-folder" }, description = "Output folder. Default: ${DEFAULT-VALUE}", paramLabel = "PATH")
    private File outputDirectory = new File("out");

    @Option(names = { "-h", "--headless" }, description = "Use headless mode. Default: ${DEFAULT-VALUE}")
    private boolean headless = false;

    @Option(names = { "-t", "--max-crawl-time" }, description = "Maximum allowed crawl time (minutes). " +
            "Default = ${DEFAULT-VALUE}", paramLabel = "NUM")
    private int maxCrawlTime = 10; // minutes

    @Option(names = { "-d", "--device-pixel-ratio" }, description = "Device scale factor (for screenshots). " +
            "Default: ${DEFAULT-VALUE} (for normal screens). 2 for Retina display", paramLabel = "NUM")
    private double deviceScaleFactor = 1;

    @Option(names = { "-e", "--maximum-events" }, description = "Maximum number of allowed crawl actions. Default: ${DEFAULT-VALUE}", paramLabel = "NUM")
    private int maxEvents = 100;

    @Option(names = { "-c", "--click-only" }, description = "Only examine clicks. Default = ${DEFAULT-VALUE}")
    private boolean clickOnly = true;

    @Override
    public void run() {

        CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor(url);
        EmbeddedBrowser.BrowserType browserType = this.headless ? BrowserType.HEADLESS_CHROME : BrowserType.CHROME;

        String tagName = "*";
        if (experimentMode == ExperimentMode.STYLEX_PRIORITIZATION || experimentMode == ExperimentMode.STYLEX_NO_PRIORITIZATION) {
            AnticipateEventListenerCondition anticipateEventListenerCondition = new AnticipateEventListenerCondition(tagName, clickOnly);
            builder.crawlRules().consider(tagName)
                    .withElementConditions(anticipateEventListenerCondition)
                    .withEventsProvider(anticipateEventListenerCondition);
            // Required to update clickables at each new state, close the R Engine when done, etc:
            builder.addPlugin(anticipateEventListenerCondition);
            if (experimentMode == ExperimentMode.STYLEX_PRIORITIZATION) {
                builder.crawlRules().setElementSorter(anticipateEventListenerCondition);
            }
        } else if (experimentMode == ExperimentMode.ALL_ELEMENTS || experimentMode == ExperimentMode.ALL_ELEMENTS_RANDOM) {
            builder.crawlRules().click(tagName);
            if (experimentMode == ExperimentMode.ALL_ELEMENTS_RANDOM) {
                builder.crawlRules().clickElementsInRandomOrder(true);
            }
        } else if (experimentMode == ExperimentMode.DEFAULT_ELEMENTS) {
            builder.crawlRules().clickDefaultElements();
        } else if (experimentMode == ExperimentMode.CHROME_EVENTS) {
            ChromeEventsCondition chromeEventsCondition = new ChromeEventsCondition(clickOnly);
            builder.crawlRules().consider(tagName)
                    .withElementConditions(chromeEventsCondition)
                    .withEventsProvider(chromeEventsCondition);
            builder.addPlugin(chromeEventsCondition);
            builder.crawlRules().clickElementsInRandomOrder(true);
            builder.crawlRules().clickOnce(false);
        }
        //builder.crawlRules().crawlHiddenAnchors(true);

        builder.setMaximumStates(MAXIMUM_STATES);
        builder.setMaximumDepth(MAXIMUM_DEPTH);
        builder.setMaximumRunTime(maxCrawlTime, TimeUnit.MINUTES);
        builder.setPixelDensity(deviceScaleFactor);
        builder.setOutputDirectory(outputDirectory);

        builder.crawlRules().waitAfterReloadUrl(WAIT_TIME_AFTER_RELOAD, TimeUnit.MILLISECONDS);
        builder.crawlRules().waitAfterEvent(WAIT_TIME_AFTER_EVENT, TimeUnit.MILLISECONDS);

        builder.setBrowserConfig(new BrowserConfiguration(browserType, 1));


        CrawlOverview crawlOverview = new CrawlOverview();
        builder.addPlugin(crawlOverview);
        //builder.addPlugin(new TestSuiteGenerator());

        long[] currentTime = new long[1];

        builder.addPlugin(new OnUrlLoadPlugin() {
            boolean firstRun = true;
            @Override
            public void onUrlLoad(CrawlerContext context) {
                if (firstRun) {
                    currentTime[0] = System.nanoTime();
                    firstRun = false;
                }
            }
        });

        ComputeMetricsPlugin computeMetricsPlugin = new ComputeMetricsPlugin(() -> currentTime[0], crawlOverview, maxEvents);
        builder.addPlugin(computeMetricsPlugin);

        builder.setStateVertexFactory(new StateVertexFactory() {
            @Override
            public StateVertex newStateVertex(int id, String url, String name, String dom, String strippedDom, EmbeddedBrowser browser) {
                return new StateVertexWithCoverage(id, url, name, dom, strippedDom, currentTime[0]);
            }
        });

        CrawljaxRunner crawljax = new CrawljaxRunner(builder.build());

        crawljax.call();
    }

    public static void main(String[] args) {
        CommandLine.run(new StyleXMain(), args);
    }


}
