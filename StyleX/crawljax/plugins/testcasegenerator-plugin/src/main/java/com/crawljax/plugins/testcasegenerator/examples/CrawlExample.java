package com.crawljax.plugins.testcasegenerator.examples;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.core.CrawljaxRunner;
import com.crawljax.core.configuration.BrowserConfiguration;
import com.crawljax.core.configuration.CrawlRules.FormFillMode;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.CrawljaxConfiguration.CrawljaxConfigurationBuilder;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.crawljax.plugins.testcasegenerator.TestSuiteGenerator;
import com.crawljax.visual.PHashStateVertexFactory;

/**
 * Example of running Crawljax with the CrawlOverview plugin on a single-page web app. The crawl
 * will produce output using the {@link CrawlOverview} plugin. Default output dir is "out".
 */
public final class CrawlExample {

	private static final long WAIT_TIME_AFTER_EVENT = 200;
	private static final long WAIT_TIME_AFTER_RELOAD = 20;
//	private static final String URL = "http://phptravels.com/demo/";
//	private static final String URL = "http://testcue.com/crawljax-demo-full/";
    private static final String URL = "http://localhost:8080/";

	/**
	 * Run this method to start the crawl.
	 * 
	 * @throws IOException
	 *             when the output folder cannot be created or emptied.
	 */
	public static void main(String[] args) throws IOException {
		CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor(URL);
		builder.crawlRules().setFormFillMode(FormFillMode.NORMAL);
		builder.setStateVertexFactory(new PHashStateVertexFactory());

		// click these elements
		builder.crawlRules().clickDefaultElements();
		// builder.crawlRules().click("div");
		builder.crawlRules().crawlHiddenAnchors(true);

		builder.setMaximumStates(5);
		builder.setMaximumDepth(3);
		builder.crawlRules().clickElementsInRandomOrder(false);

		// Set timeouts
		builder.crawlRules().waitAfterReloadUrl(WAIT_TIME_AFTER_RELOAD, TimeUnit.MILLISECONDS);
		builder.crawlRules().waitAfterEvent(WAIT_TIME_AFTER_EVENT, TimeUnit.MILLISECONDS);

		builder.setBrowserConfig(new BrowserConfiguration(BrowserType.CHROME, 1));

		// TODO: Add CrawlOverview here?
		builder.addPlugin(new CrawlOverview());
		builder.addPlugin(new TestSuiteGenerator());

		CrawljaxRunner crawljax = new CrawljaxRunner(builder.build());
		crawljax.call();

	}

}
