package com.crawljax.core.plugin;

import com.crawljax.core.CrawlerContext;
import com.crawljax.core.state.Eventable;

public interface OnEventFiredPlugin extends Plugin {
    void eventFired(CrawlerContext context, Eventable eventable, boolean firingSuccessful);
}
