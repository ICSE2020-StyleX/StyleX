package com.crawljax.browser;

import javax.inject.Inject;
import javax.inject.Provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.ProxyConfiguration;
import com.crawljax.core.configuration.ProxyConfiguration.ProxyType;
import com.crawljax.core.plugin.Plugins;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;

import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.bonigarcia.wdm.FirefoxDriverManager;
import io.github.bonigarcia.wdm.PhantomJsDriverManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of the EmbeddedBrowserBuilder based on Selenium WebDriver API.
 */
public class WebDriverBrowserBuilder implements Provider<EmbeddedBrowser> {

	private static final Logger LOGGER = LoggerFactory.getLogger(WebDriverBrowserBuilder.class);
	private final CrawljaxConfiguration configuration;
	private final Plugins plugins;

	@Inject
	public WebDriverBrowserBuilder(CrawljaxConfiguration configuration, Plugins plugins) {
		this.configuration = configuration;
		this.plugins = plugins;
	}

	/**
	 * Build a new WebDriver based EmbeddedBrowser.
	 * 
	 * @return the new build WebDriver based embeddedBrowser
	 */
	@Override
	public EmbeddedBrowser get() {
		LOGGER.debug("Setting up a Browser");
		// Retrieve the config values used
		ImmutableSortedSet<String> filterAttributes =
		        configuration.getCrawlRules().getPreCrawlConfig().getFilterAttributeNames();
		long crawlWaitReload = configuration.getCrawlRules().getWaitAfterReloadUrl();
		long crawlWaitEvent = configuration.getCrawlRules().getWaitAfterEvent();

		// Determine the requested browser type
		EmbeddedBrowser browser = null;
		EmbeddedBrowser.BrowserType browserType =
		        configuration.getBrowserConfig().getBrowsertype();
		try {
			switch (browserType) {
				case FIREFOX:
					browser =
					        newFireFoxBrowser(filterAttributes, crawlWaitReload, crawlWaitEvent);
					break;
				case CHROME:
					browser = newChromeBrowser(filterAttributes, crawlWaitReload, crawlWaitEvent, false);
					break;
				case HEADLESS_CHROME:
					browser = newChromeBrowser(filterAttributes, crawlWaitReload, crawlWaitEvent, true);
					break;
				case REMOTE:
					browser = WebDriverBackedEmbeddedBrowser.withRemoteDriver(
					        configuration.getBrowserConfig().getRemoteHubUrl(), filterAttributes,
					        crawlWaitEvent, crawlWaitReload);
					break;
				case PHANTOMJS:
					browser =
					        newPhantomJSDriver(filterAttributes, crawlWaitReload, crawlWaitEvent);
					break;
				default:
					throw new IllegalStateException("Unrecognized browsertype "
					        + configuration.getBrowserConfig().getBrowsertype());
			}
		} catch (IllegalStateException e) {
			LOGGER.error("Crawling with {} failed: " + e.getMessage(), browserType.toString());
			throw e;
		}
		if (browser instanceof WebDriverBackedEmbeddedBrowser) {
			WebDriverBackedEmbeddedBrowser webDriverBackedEmbeddedBrowser = (WebDriverBackedEmbeddedBrowser) browser;
			webDriverBackedEmbeddedBrowser.setPixelDensity(configuration.getPixelDensity());
		}
		plugins.runOnBrowserCreatedPlugins(browser);
		return browser;
	}

	private EmbeddedBrowser newFireFoxBrowser(ImmutableSortedSet<String> filterAttributes,
	        long crawlWaitReload, long crawlWaitEvent) {

		FirefoxDriverManager.getInstance().setup();

		FirefoxProfile profile = new FirefoxProfile();

		// disable download dialog (downloads directly without the need for a confirmation)
		profile.setPreference("browser.download.folderList", 2);
		profile.setPreference("browser.download.manager.showWhenStarting", false);
		// profile.setPreference("browser.download.dir","downloads");
		profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
		        "text/csv, application/octet-stream");

		if (configuration.getProxyConfiguration() != null) {
			String lang = configuration.getBrowserConfig().getLangOrNull();
			if (!Strings.isNullOrEmpty(lang)) {
				profile.setPreference("intl.accept_languages", lang);
			}

			profile.setPreference("network.proxy.http",
			        configuration.getProxyConfiguration().getHostname());
			profile.setPreference("network.proxy.http_port",
			        configuration.getProxyConfiguration().getPort());
			profile.setPreference("network.proxy.type",
			        configuration.getProxyConfiguration().getType().toInt());
			/* use proxy for everything, including localhost */
			profile.setPreference("network.proxy.no_proxies_on", "");

		}

		return WebDriverBackedEmbeddedBrowser.withDriver(new FirefoxDriver(profile),
		        filterAttributes, crawlWaitReload, crawlWaitEvent);
	}

	private EmbeddedBrowser newChromeBrowser(ImmutableSortedSet<String> filterAttributes,
	        long crawlWaitReload, long crawlWaitEvent, boolean headless) {

		ChromeDriverManager.getInstance().setup();

		String chromeLogsPath = System.getProperty("user.dir") + "/chromedriver.log";
		System.setProperty(ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY, chromeLogsPath);
		System.setProperty(ChromeDriverService.CHROME_DRIVER_VERBOSE_LOG_PROPERTY, "true");

		ChromeOptions optionsChrome = new ChromeOptions();
		if (configuration.getProxyConfiguration() != null
				&& configuration.getProxyConfiguration().getType() != ProxyType.NOTHING) {
			String lang = configuration.getBrowserConfig().getLangOrNull();
			if (!Strings.isNullOrEmpty(lang)) {
				optionsChrome.addArguments("--lang=" + lang);
			}
			optionsChrome.addArguments("--proxy-server=http://"
					+ configuration.getProxyConfiguration().getHostname() + ":"
					+ configuration.getProxyConfiguration().getPort());
		} else {
			//optionsChrome.addArguments("--disable-web-security"); // This was needed for accessing IFrame info
			//optionsChrome.addArguments("--user-data-dir=chrome-user-data");
		}
		if (headless) {
			optionsChrome.addArguments("--headless");
		}
		ChromeDriver driverChrome = new ChromeDriver(optionsChrome);
		try {
			int chromeDebuggerPort = getChromeDebuggerPort(chromeLogsPath);
			System.setProperty("chrome.debugger.port", String.valueOf(chromeDebuggerPort));
			String chromeFirstTabId = getChromeFirstTabId(chromeDebuggerPort);
			System.setProperty("chrome.debugger.firsttab", chromeFirstTabId);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return WebDriverBackedEmbeddedBrowser.withDriver(driverChrome, filterAttributes,
		        crawlWaitEvent, crawlWaitReload);
	}

	private String getChromeFirstTabId(int chromeDebuggerPort) throws IOException {
		String chromeFirstTabID = null;
		final String jsonUrl = String.format("http://localhost:%s/json", chromeDebuggerPort);
		String json = readStringFromURL(jsonUrl);
		JsonElement rootElement = new JsonParser().parse(json);
		JsonArray rootArray = rootElement.getAsJsonArray();
		for (JsonElement jsonElement : rootArray) {
			JsonObject tabObject = jsonElement.getAsJsonObject();
			if (tabObject.get("url").getAsString().startsWith("data:,")) {
				String webSocketDebuggerUrl = tabObject.get("webSocketDebuggerUrl").getAsString();
				chromeFirstTabID = webSocketDebuggerUrl.replace(String.format("ws://localhost:%s/devtools/page/", chromeDebuggerPort), "");
				break;
			}
		}
		return chromeFirstTabID;
	}

	public static String readStringFromURL(String requestURL) throws IOException
	{
		try (Scanner scanner = new Scanner(new URL(requestURL).openStream(), StandardCharsets.UTF_8.toString())) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	private int getChromeDebuggerPort(String chromeLogsPath) throws IOException {
		int debuggerPort = -1;
		File file = new File(chromeLogsPath);
		//Pattern pattern = Pattern.compile("--remote-debugging-port=(\\d+)\\s");
		Pattern pattern = Pattern.compile("localhost:(\\d+)");
		Scanner sc = new Scanner(file);
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			//if (line.contains("--remote-debugging-port=")) {
			if (line.contains("\"debuggerAddress\":")) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					debuggerPort = Integer.valueOf(matcher.group(1));
				}
				break;
			}
		}
		sc.close();
		return debuggerPort;
	}

	private EmbeddedBrowser newPhantomJSDriver(ImmutableSortedSet<String> filterAttributes,
	        long crawlWaitReload, long crawlWaitEvent) {

		PhantomJsDriverManager.getInstance().setup();

		DesiredCapabilities caps = new DesiredCapabilities();
		caps.setCapability("takesScreenshot", true);
		caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS,
		        new String[] { "--webdriver-loglevel=WARN" });
		final ProxyConfiguration proxyConf = configuration.getProxyConfiguration();
		if (proxyConf != null && proxyConf.getType() != ProxyType.NOTHING) {
			final String proxyAddrCap =
			        "--proxy=" + proxyConf.getHostname() + ":" + proxyConf.getPort();
			final String proxyTypeCap = "--proxy-type=http";
			final String[] args = new String[] { proxyAddrCap, proxyTypeCap };
			caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, args);
		}

		PhantomJSDriver phantomJsDriver = new PhantomJSDriver(caps);
		phantomJsDriver.manage().window().maximize();
		
		return WebDriverBackedEmbeddedBrowser.withDriver(phantomJsDriver, filterAttributes,
		        crawlWaitEvent, crawlWaitReload);
	}

}
