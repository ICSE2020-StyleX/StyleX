package com.testcue.playground;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.CrawljaxRunner;
import com.crawljax.core.configuration.CrawlRules.FormFillMode;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.InputSpecification;
import com.crawljax.core.state.Identification;
import com.crawljax.core.state.Identification.How;
import com.crawljax.core.state.StateVertex;
import com.crawljax.core.state.StateVertexFactory;
import com.crawljax.core.state.StateVertexImpl;
import com.crawljax.forms.FormInput.InputType;
import com.google.common.base.Objects;

/**
 * Use the sample plugin in combination with Crawljax.
 */
public class CrawljaxTestRunner {

	static final String MISSING_ARGUMENT_MESSAGE =
	        "Missing required argument login username and/or password.";

	private CrawljaxConfiguration config = null;

	private static final String URL = "http://testcue.com/crawljax-demo-full/";
	private static final int MAX_DEPTH = 2;
	private static final int MAX_NUMBER_STATES = 20;

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(CrawljaxTestRunner.class);

	/**
	 * Entry point for the crawl. Configure the crawl here.
	 */
	public static void main(String[] args) {

		try {
			CrawljaxTestRunner runner = new CrawljaxTestRunner(args);
			runner.runIfConfigured();
		} catch (NumberFormatException e) {
			System.err.println("Could not parse number " + e.getMessage());
			System.exit(1);
		} catch (RuntimeException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

	}

	private static InputSpecification getInputSpecification() {
		InputSpecification input = new InputSpecification();

		// new API
		input.inputField(InputType.TEXT, new Identification(How.id, "text"))
		        .inputValues("Crawljax");

		input.inputField(InputType.TEXTAREA, new Identification(How.id, "area"))
		        .inputValues("Testcue");
		input.inputField(InputType.CHECKBOX, new Identification(How.id, "checkbox"))
		        .inputValues(true);
		;
		return input;
	}

	/**
	 * Initialize the configuration programmatically and using user specified arguments from the
	 * command line.
	 */
	private CrawljaxTestRunner(String[] args) {

		FormFillMode formFillMode = FormFillMode.NORMAL;

		// Create the crawl configuration
		CrawljaxConfiguration.CrawljaxConfigurationBuilder builder =
		        CrawljaxConfiguration.builderFor(URL);
		builder.crawlRules().setFormFillMode(formFillMode);
		builder.crawlRules().filterAttributeNames("id");

		builder.setStateVertexFactory(new StateVertexFactory() {
			@Override
			public StateVertex newStateVertex(int id, String url, String name, String dom,
		            String strippedDom, EmbeddedBrowser browser) {

				return new StateVertexImpl(id, url, name, dom, strippedDom) {
			        private static final long serialVersionUID = 123433317983488L;

			        @Override
			        public int hashCode() {
				        return Objects.hashCode(strippedDom);
			        }

			        @Override
			        public boolean equals(Object object) {
				        if (object instanceof StateVertex) {
					        StateVertex that = (StateVertex) object;
					        return Objects.equal(this.getStrippedDom(), that.getStrippedDom());
				        }
				        return false;
			        }
		        };
			}
		});
		builder.crawlRules().click("a");
		builder.crawlRules().click("button");
		builder.crawlRules().click("div");

		// except these
		builder.crawlRules().dontClick("a").underXPath("//DIV[@id='guser']");
		builder.crawlRules().dontClick("a").withText("Language Tools");

		// limit the crawling scope
		builder.setMaximumStates(MAX_NUMBER_STATES);
		builder.setMaximumDepth(MAX_DEPTH);

		builder.crawlRules().setInputSpec(getInputSpecification());

		// Add the crawl overview plugin for visualizing the crawl.
		// builder.addPlugin(new CrawlOverview());

		// Create the configuration using the builder.
		config = builder.build();

	}

	/**
	 * Start the crawl if the configuration was successfully created.
	 */
	private void runIfConfigured() {
		if (config != null) {
			CrawljaxRunner runner = new CrawljaxRunner(config);
			runner.call();
		}
	}
}
