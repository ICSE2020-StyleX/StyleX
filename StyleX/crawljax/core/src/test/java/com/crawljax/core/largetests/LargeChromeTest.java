package com.crawljax.core.largetests;

import org.junit.experimental.categories.Category;

import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.core.configuration.BrowserConfiguration;
import com.crawljax.test.BrowserTest;

@Category(BrowserTest.class)
public class LargeChromeTest extends LargeTestBase {

	@Override
	BrowserConfiguration getBrowserConfiguration() {
		return new BrowserConfiguration(BrowserType.CHROME);
	}

	@Override
	long getTimeOutAfterReloadUrl() {
		return 100;
	}

	@Override
	long getTimeOutAfterEvent() {
		return 100;
	}
}
