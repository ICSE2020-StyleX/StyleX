/**
 * Created Apr 17, 2008
 */
package com.crawljax.plugins.testcasegenerator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crawljax.core.CrawlSession;
import com.crawljax.core.ExitNotifier.ExitStatus;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.plugin.HostInterface;
import com.crawljax.core.plugin.HostInterfaceImpl;
import com.crawljax.core.plugin.PostCrawlingPlugin;
import com.crawljax.core.plugin.PreCrawlingPlugin;
import com.crawljax.util.DomUtils;
import com.crawljax.util.FSUtils;

/**
 * Test suite generator for crawljax. IMPORTANT: only works with CrawljaxConfiguration TODO: Danny,
 * also make sure package name is correct
 * 
 * @author danny
 * @version $Id: TestSuiteGenerator.java 6276 2009-12-23 15:37:09Z frank $
 */
public class TestSuiteGenerator implements PostCrawlingPlugin {

	private final String TEST_SUITE_PATH = "src/test/java/generated/";
	private final String CLASS_NAME = "GeneratedTests";
	private final String FILE_NAME_TEMPLATE = "TestCase.vm";

	private final String JSON_STATES = TEST_SUITE_PATH + "states.json";
	private final String JSON_EVENTABLES = TEST_SUITE_PATH + "eventables.json";

	private CrawlSession session;
	private HostInterface overviewHostInterface;
	private HostInterface testgenHostInterface;
	private String absPath;

	private static final Logger LOGGER =
	        LoggerFactory.getLogger(TestSuiteGenerator.class.getName());

	public TestSuiteGenerator() {
		this.testgenHostInterface = null;
		this.overviewHostInterface = null;
		absPath = "";
		LOGGER.info("Initialized the Test Suite Generator plugin");
	}

	public TestSuiteGenerator(HostInterface overviewHostInterface, 
							 HostInterface testgenHostInterface) {
		this.testgenHostInterface = testgenHostInterface;
		this.overviewHostInterface = overviewHostInterface;
		absPath = this.testgenHostInterface.getOutputDirectory().getAbsolutePath() + File.separator;
		LOGGER.info("Initialized the Test Suite Generator plugin");
	}

	@Override
	public void postCrawling(CrawlSession session, ExitStatus exitReason) {
		
		/* Set up the input and output directories for the test suite, if not
		 * specified. */

		if(overviewHostInterface == null) {
			overviewHostInterface = new HostInterfaceImpl(session.getConfig().getOutputDir(), null);
		}

		if(testgenHostInterface == null) {
			Map<String, String> params = new HashMap<String, String>();
			params.put("testRecordsDir", new File(session.getConfig().getOutputDir(), "test-results").getAbsolutePath());
			testgenHostInterface = new HostInterfaceImpl(session.getConfig().getOutputDir(), params);
			absPath = testgenHostInterface.getOutputDirectory().getAbsolutePath() + File.separator;
		}
		
		this.session = session;
		try {
			FSUtils.directoryCheck(absPath + TEST_SUITE_PATH);
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOGGER.info("Generating tests in " + absPath + TEST_SUITE_PATH);
		String fileName = generateTestCases();
		if (fileName != null) {
			LOGGER.info("Tests generated in " + fileName);
		} else {
			LOGGER.error("Failed to generate test cases");
		}
	}

	/**
	 * @return the filename of the generated java test class, null otherwise
	 */
	public String generateTestCases() {
		TestSuiteGeneratorHelper testSuiteGeneratorHelper = new TestSuiteGeneratorHelper(session);
		List<TestMethod> testMethods = testSuiteGeneratorHelper.getTestMethods();

		try {
			JavaTestGenerator generator =
			        new JavaTestGenerator(CLASS_NAME, session.getInitialState().getUrl(),
			                testMethods, session.getConfig(), 
			                absPath + TEST_SUITE_PATH,
			                this.overviewHostInterface.getOutputDirectory().getAbsolutePath(),
			                this.testgenHostInterface.getParameters().get("testRecordsDir"));
			testSuiteGeneratorHelper.writeStateVertexTestDataToJSON(absPath + JSON_STATES);
			testSuiteGeneratorHelper.writeEventableTestDataToJSON(absPath + JSON_EVENTABLES);
			generator.useJsonInsteadOfDB(absPath + JSON_STATES, absPath + JSON_EVENTABLES);
			return generator.generate(DomUtils.addFolderSlashIfNeeded(absPath + TEST_SUITE_PATH),
			        FILE_NAME_TEMPLATE);

		} catch (Exception e) {
			System.out.println("Error generating testsuite: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String toString() {
		return "Test Suite Generator plugin";
	}
}
