package com.crawljax.plugins.testcasegenerator;

import static com.google.common.base.Preconditions.checkArgument;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.opencv.imgcodecs.Imgcodecs;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import com.codahale.metrics.MetricRegistry;
import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.condition.ConditionTypeChecker;
import com.crawljax.condition.browserwaiter.WaitConditionChecker;
import com.crawljax.condition.invariant.Invariant;
import com.crawljax.core.CrawlerContext;
import com.crawljax.core.CrawljaxException;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.plugin.Plugins;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.Identification;
import com.crawljax.core.state.Identification.How;
import com.crawljax.core.state.StateVertex;
import com.crawljax.di.CoreModule;
import com.crawljax.forms.FormHandler;
import com.crawljax.forms.FormInput;
import com.crawljax.oraclecomparator.StateComparator;
import com.crawljax.plugins.testcasegenerator.report.ReportBuilder;
import com.crawljax.plugins.testcasegenerator.util.GsonUtils;
import com.crawljax.util.DomUtils;
import com.crawljax.util.ElementResolver;
import com.crawljax.util.FSUtils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;

import detection.ObjectDetection;
import diff.ObjectDiff;
import pageobject.AveragePageObjectFactory;
import pageobject.IPageObjectFactory;


/**
 * Helper for the test suites.
 */
public class TestSuiteHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestSuiteHelper.class.getName());

	private EmbeddedBrowser browser;
	private String url;

	private StateComparator oracleComparator;
	private ConditionTypeChecker<Invariant> invariantChecker;
	private WaitConditionChecker waitConditionChecker;
	private final ArrayList<Eventable> eventables = new ArrayList<Eventable>();
	private FormHandler formHandler;
	private Plugins plugins;

	private Map<Long, StateVertex> mapStateVertices;
	private Map<Long, Eventable> mapEventables;
	private CrawlerContext context;
	
	private String outputPath;
	private String tmpPath;

	private ReportBuilder reportBuilder;
	
	private EmbeddedBrowser oldPage;
	private EmbeddedBrowser newPage;

	/* Screenshot stuff. */
	private boolean firstState = true;

	private final File screenshotsOutputFolder;
	private final File screenshotsInputFolder;

	private final File diffOutputFolder;

	/**
	 * @param config
	 *            Configuration to use.
	 * @param jsonStates
	 *            The json states.
	 * @param jsonEventables
	 *            The json eventables.
	 * @throws Exception
	 *             On error.
	 */
	public TestSuiteHelper(CrawljaxConfiguration config, 
			String jsonStates,
	        String jsonEventables, 
	        String crawlScreenshots, 
	        String testRecords,
	        String url, 
	        String outputPath) throws Exception {
		LOGGER.info("Loading needed json files for States and Eventables");

		Gson gson = (new GsonBuilder())
				.registerTypeAdapter(ImmutableMap.class, new GsonUtils.ImmutableMapDeserializer())
				.create();
		// TODO We might want to parameterize this class to accept specific StateVertex
		mapStateVertices = gson.fromJson(new BufferedReader(new FileReader(jsonStates)), 
				new TypeToken<Map<Long, StateVertex>>(){}.getType());
		mapEventables = gson.fromJson(new BufferedReader(new FileReader(jsonEventables)), 
				new TypeToken<Map<Long, Eventable>>(){}.getType());

		this.url = url;
		this.context = Guice.createInjector(new CoreModule(config)).getInstance(CrawlerContext.class);
		this.plugins = new Plugins(config, new MetricRegistry());
		this.browser = context.getBrowser();
		
		this.formHandler =
		        new FormHandler(browser, config.getCrawlRules());

		this.oracleComparator = new StateComparator(config.getCrawlRules());
		this.invariantChecker = new ConditionTypeChecker<Invariant>(config.getCrawlRules().getInvariants());
		this.waitConditionChecker = new WaitConditionChecker(config.getCrawlRules());
		
		this.outputPath = outputPath + "diffs" + File.separator;
		FileUtils.deleteDirectory(new File(this.outputPath));
		FSUtils.checkFolderForFile(this.outputPath);
		reportBuilder = new ReportBuilder(this.outputPath);
		
		this.tmpPath = outputPath + "tmp" + File.separator;
		FileUtils.deleteDirectory(new File(this.tmpPath));
		FSUtils.checkFolderForFile(this.tmpPath);
		
		this.oldPage = Guice.createInjector(new CoreModule(config)).getInstance(EmbeddedBrowser.class);
		this.newPage = Guice.createInjector(new CoreModule(config)).getInstance(EmbeddedBrowser.class);
		
		LOGGER.info("Loading plugins...");
		plugins.runPreCrawlingPlugins(config);
		
		/* The folder where we will temporarily store screenshots. */
		screenshotsOutputFolder = new File(this.tmpPath);

		/* The folder where the oracle screenshots are stored. */
		screenshotsInputFolder = new File(crawlScreenshots);
		
		/* The folder where the image diffs are stored. */
		diffOutputFolder = new File(testRecords);

	}

	/**
	 * Loads start url and checks initialUrlConditions.
	 * 
	 * @throws Exception
	 *             On error.
	 */
	public void goToInitialUrl() throws Exception {
		browser.goToUrl(new URI(url));
		waitConditionChecker.wait(browser);
		plugins.runOnUrlLoadPlugins(context);
	}

	/**
	 * Closes browser and writes report.
	 * 
	 * @throws Exception
	 *             On error.
	 */
	public void tearDown() throws Exception {
		Thread.sleep(400);
		
		//if there's still a method it failed
		reportBuilder.methodFail();
		reportBuilder.build();
		
		LOGGER.info("Report generated in " + this.outputPath);
		
		browser.close();
		oldPage.close();
		newPage.close();
	}

	/**
	 * Fill in form inputs.
	 * 
	 * @param formInputs
	 *            The form inputs to handle.
	 * @throws Exception
	 *             On error.
	 */
	public void handleFormInputs(List<FormInput> formInputs) throws Exception {
		formHandler.handleFormElements(formInputs);
	}

	/**
	 * Run the InCrawling plugins.
	 */
	public void runInCrawlingPlugins(long stateId) {
		plugins.runOnNewStatePlugins(context, getStateVertex(stateId));
	}

	private Eventable getEventable(Long eventableId) {
		return mapEventables.get(eventableId);
	}

	/**
	 * @param eventableId
	 *            Id of the eventable.
	 * @return whether the event is fired
	 */
	public boolean fireEvent(long eventableId) {
		try {
			// browser.closeOtherWindows();
			Eventable eventable = getEventable(eventableId);
			eventables.add(eventable);
			reportBuilder.addEventable(eventable);
			String xpath = eventable.getIdentification().getValue();

			ElementResolver er = new ElementResolver(eventable, browser);
			String newXPath = er.resolve();
			boolean fired = false;
			if (newXPath != null) {
				if (!xpath.equals(newXPath)) {
					LOGGER.info("XPath of \"" + eventable.getElement().getText()
					        + "\" changed from " + xpath + " to " + newXPath);
				}
				eventable.setIdentification(new Identification(How.xpath, newXPath));
				LOGGER.info("Firing: " + eventable);
				fired = browser.fireEventAndWait(eventable);
			}
			if (!fired) {
				// String orgDom = "";
				// try {
				// orgDom = eventable.getEdge().getFromStateVertex().getDom();
				// } catch (Exception e) {
				// // TODO: Danny fix
				// orgDom = "<html>todo: fix</html>";
				// // LOGGER.info("Warning, could not get original DOM");
				// }
				// reportBuilder.addFailure(new EventFailure(browser, currentTestMethod, eventables,
				// orgDom, browser.getDom()));
				
				reportBuilder.markLastEventableFailed();
			}
			waitConditionChecker.wait(browser);
			return fired;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return false;
		}
	}

	/**
	 * @param StateVertexId
	 *            The id of the state vertix.
	 * @return the State with id StateVertex Id
	 */
	public StateVertex getStateVertex(Long StateVertexId) {
		return mapStateVertices.get(StateVertexId);
	}

	/**
	 * @param StateVertexId
	 *            The id of the state vertex.
	 * @return return where the current DOM in the browser is equivalent with the state with
	 *         StateVertexId
	 */
	public boolean compareCurrentScreenshotWithState(long StateVertexId) {

		/* The screenshots from before and after the change. */
		StateVertex vertex = getStateVertex(StateVertexId);
		File oldScreenshot = oldScreenShotFile(vertex.getName());
		File newScreenshot = newScreenShotFile(vertex.getName());

		/* Save the current screenshot to a temporary folder. */
		saveScreenshot(getBrowser(), vertex.getName(), newScreenshot);
		
		/* Create a visual diff of the screenshots. */
		ObjectDiff diff = visualDiff(oldScreenshot, newScreenshot);

		/* Build a report if the states differ. */
		if(diff.hasChanges()) {
			
			/* Write the annotated file to disk. */
			String srcAnnotatedFileName = oldDiffFile(vertex.getName()).getAbsolutePath();
			String dstAnnotatedFileName = newDiffFile(vertex.getName()).getAbsolutePath();
			try {
				ObjectDetection.directoryCheck(diffOutputFolder.getAbsolutePath());
				Imgcodecs.imwrite(srcAnnotatedFileName, diff.annotateOldPage());
				Imgcodecs.imwrite(dstAnnotatedFileName, diff.annotateNewPage());
			} catch (IOException e) {
				LOGGER.debug("Annotated files not written to disk because {}", e.getMessage(), e);
			}

			return false;
			
		}
		
		return true;

	}
	
	/**
	 * Annotate two versions of a screenshot with diff info.
	 */
	private ObjectDiff visualDiff(File oldScreenshot, File newScreenshot) {

		IPageObjectFactory pageObjectFactory = new AveragePageObjectFactory();

		/* Run the detection algorithm. */
		ObjectDetection srcDetection = new ObjectDetection(pageObjectFactory, oldScreenshot.getAbsolutePath());
		ObjectDetection dstDetection = new ObjectDetection(pageObjectFactory, newScreenshot.getAbsolutePath());

		srcDetection.detectObjects();
		dstDetection.detectObjects();

		/* Do the visual diff. */
		ObjectDiff diff = new ObjectDiff(srcDetection.getPage(), dstDetection.getPage(), false);
		diff.diff();
		
		return diff;

	}

	private void saveScreenshot(EmbeddedBrowser browser, String name, File newScreenshot) {

		if (firstState) {
			firstState = false;
			// check if screenshots folder is already created by core
			File screenshotsFolder = getScreenshotsOutputFolder();
			if (!screenshotsFolder.exists()) {
				// screenshots already taken, no need to retake here
				LOGGER.debug("Screenshot folder does not exist yet, creating...");
				boolean created = screenshotsFolder.mkdir();
				checkArgument(created, "Could not create screenshotsFolder dir");
			}
		}

		LOGGER.debug("Saving screenshot for state {}", name);

		try {
			BufferedImage screenshot = browser.getScreenShotAsBufferedImage(500);
			ImageWriter.writeScreenShotAndThumbnail(screenshot, newScreenshot);
		} catch (CrawljaxException | WebDriverException e) {
			LOGGER.warn(
					"Screenshots are not supported or not functioning for {}. Exception message: {}",
					browser, e.getMessage());
			LOGGER.debug("Screenshot not made because {}", e.getMessage(), e);
		}
		LOGGER.trace("Screenshot saved");

	}
	
	/**
	 * @return the current output folder for the diff.
	 */
	private File getDiffOutputFolder() {
		
		int maxID = 0;
		
		if (diffOutputFolder.exists()) { 
		
			/* Get the directories. */
			String[] dirs = diffOutputFolder.list(new FilenameFilter() {
				@Override
				public boolean accept(File current, String name) {
					return new File(current, name).isDirectory();
				}
			});
			
			if (null != dirs && dirs.length > 0) {
				/* Find the directory with the highest number. */
				maxID = -1;
				for(String dir : dirs) {
					try {
						int id = Integer.parseInt(dir);
						maxID = id > maxID ? id : maxID;
					}catch (NumberFormatException e) {
						continue;
					}
				}
			}
		} else {
			diffOutputFolder.mkdir();
		}
		
		File diffsFolder = new File(diffOutputFolder, maxID + File.separator + "diffs");

		if (!diffsFolder.exists()) {
			boolean created = diffsFolder.mkdirs();
			checkArgument(created, "Could not create diffs dir");
		}

		return diffsFolder;

	}
	
	File newScreenShotFile(String name) {
		return new File(screenshotsOutputFolder, name + ".png");
	}

	public File getScreenshotsOutputFolder() {
		return screenshotsOutputFolder;
	}

	File oldScreenShotFile(String name) {
		return new File(screenshotsInputFolder, name + ".png");
	}

	public File getScreenshotsInputFolder() {
		return screenshotsInputFolder;
	}

	File oldDiffFile(String name) {
		return new File(getDiffOutputFolder(), name + "_old.png");
	}

	File newDiffFile(String name) {
		return new File(getDiffOutputFolder(), name + "_new.png");
	}

	/**
	 * @param StateVertexId
	 *            The id of the state vertex.
	 * @return return where the current DOM in the browser is equivalent with the state with
	 *         StateVertexId
	 */
	public boolean compareCurrentDomWithState(long StateVertexId) {
		StateVertex vertex = getStateVertex(StateVertexId);
		reportBuilder.addState(vertex);
		String stateDom = vertex.getStrippedDom();
		Diff diff;
		Document oldDoc, newDoc;
		URI currUri;
		
		try {
			oldDoc = DomUtils.asDocument(stateDom);
			newDoc = DomUtils.asDocument(oracleComparator.getStrippedDom(browser));
			currUri = new URI(vertex.getUrl());
			diff = DiffBuilder
					.compare(oldDoc)
					.withTest(newDoc)
					.ignoreWhitespace()
					.build();
			
		} catch (IOException | URISyntaxException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			return false;
		}
		
		if (diff.hasDifferences()) {
			//make sure there is at least one visible difference
//			try {
				//this is to fix the css for visibility purposes
//				DaisyDiffUtils.fixLinks(oldDoc, currUri);
//				DaisyDiffUtils.fixLinks(newDoc, currUri);
//			} catch(URISyntaxException e) {
				//ignore
//			}
			
			oldPage.getWebDriver().get("about:blank");
			((JavascriptExecutor) oldPage.getWebDriver())
				.executeScript("document.documentElement.innerHTML = \"" 
						+ StringEscapeUtils.escapeEcmaScript(DomUtils.getDocumentToString(oldDoc)) + "\"");
			newPage.getWebDriver().get("about:blank");
			((JavascriptExecutor) newPage.getWebDriver())
				.executeScript("document.documentElement.innerHTML = \"" 
						+ StringEscapeUtils.escapeEcmaScript(DomUtils.getDocumentToString(newDoc)) + "\"");
			boolean visibleDiff = false;
			for(Difference currDiff : diff.getDifferences()) {
				try {
					String xPathOld = currDiff.getComparison().getControlDetails().getParentXPath();
					WebElement elemOld = oldPage.getWebDriver().findElement(By.xpath(xPathOld));
					String xPathNew = currDiff.getComparison().getControlDetails().getParentXPath();
					WebElement elemNew = newPage.getWebDriver().findElement(By.xpath(xPathNew));
					
					if(elemOld.isDisplayed() || elemNew.isDisplayed()) {
						visibleDiff = true;
						break;
					}
				} catch (Exception e) {
					//ignore differences that aren't on valid elements
					//(on the document element for instance)
				}
			}
			if(!visibleDiff) {
				return true;
			}
			
			LOGGER.info("Not Equivalent with state" + StateVertexId);
			String origPath = outputPath + StateVertexId + "-orig.html";
			String newPath = outputPath + StateVertexId + "-new.html";
			String origRawPath = outputPath + StateVertexId + "-raw-orig.html";
			String newRawPath = outputPath + StateVertexId + "-raw-new.html";
			try {
//				DaisyDiffUtils.diff(vertex.getDom(), browser.getStrippedDom(), new URI(vertex.getUrl()), 
//						origPath, newPath, origRawPath, newRawPath);
			} catch (Exception e) {
				System.err.println(e.getMessage());
				e.printStackTrace();
			}
			reportBuilder.markLastStateDifferent();
			return false;
		} else {
			return true;
		}
	}

	/**
	 * @return whether all the invariants are satisfied
	 */
	public boolean checkInvariants() {
		List<Invariant> failedInvariants = invariantChecker.getFailedConditions(browser);
		try {
			for (Invariant failedInvariant : failedInvariants) {
				// reportBuilder.addFailure(new InvariantFailure(browser, currentTestMethod
				// + " - " + failedInvariant.getDescription(), eventables, browser
				// .getDom(), failedInvariant.getDescription(), failedInvariant
				// .getInvariantCondition().getAffectedNodes()));
				LOGGER.info("Invariant failed: " + failedInvariant.toString());
			}
		} catch (Exception e) {
			LOGGER.error("Error with adding failure: " + e.getMessage(), e);
		}
		if(failedInvariants.size() > 0) {
			reportBuilder.markLastStateFailed(failedInvariants);
		}
		return failedInvariants.size() == 0;
	}

	/**
	 * @param currentTestMethod
	 *            The current method that is used for testing
	 */
	public void newCurrentTestMethod(String currentTestMethod) {
		LOGGER.info("New test: " + currentTestMethod);
		eventables.clear();
		reportBuilder.newMethod(currentTestMethod);
	}
	
	/**
	 * Marks the current method as successfully run by JUnit.
	 */
	public void markLastMethodAsSucceeded() {
		reportBuilder.methodSuccess();
	}
	
	/**
	 * Marks the current method as having a failure.
	 */
	public void markLastMethodAsFailed() {
		reportBuilder.methodFail();
	}

	/**
	 * @return the browser
	 */
	public EmbeddedBrowser getBrowser() {
		return browser;
	}

	/*public StructuralVisualDiff diffCurrentDomWithState(long stateVertexId) throws IOException {
		StateVertexForElementsWithVisualInfo oldVertex = (StateVertexForElementsWithVisualInfo)getStateVertex(stateVertexId);
		// TODO The following code seems to me to be very hacky. VERY HACKY
		// Since we don't do crawling, there is no StateVertex being returned by the CrawlerContext
		// That's why we should create it here
		StructuralVisualStateVertexFactory factory = new StructuralVisualStateVertexFactory();
		StateVertexForElementsWithVisualInfo newVertex = 
				(StateVertexForElementsWithVisualInfo) factory.newStateVertex((int)stateVertexId, url, "state" + stateVertexId, 
						browser.getStrippedDom(), oracleComparator.getStrippedDom(browser), browser, newScreenShotFile(oldVertex.getName()));
		StructuralVisualDiff structuralVisualDiff = StructuralVisualDiff.calculate(oldVertex, newVertex);
		LOGGER.info("APTED computation time: {} nanoseconds", structuralVisualDiff.getComputationTime());
		if (!structuralVisualDiff.statesAreIdentical()) {
			LOGGER.warn("The states are different: \n {}", structuralVisualDiff.toString());
			structuralVisualDiff.serializeDiff(this.outputPath + "visual-dom-diff-state" + stateVertexId + ".json");
		}
		return structuralVisualDiff;
				
	}*/

}
