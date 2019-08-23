package com.stylex.chrome;

import com.crawljax.util.UrlUtils;
import com.google.common.base.Charsets;
import io.webfolder.cdp.command.DOMDebugger;
import io.webfolder.cdp.command.Emulation;
import io.webfolder.cdp.command.Runtime;
import io.webfolder.cdp.exception.CommandException;
import io.webfolder.cdp.session.Session;
import io.webfolder.cdp.session.SessionFactory;
import io.webfolder.cdp.type.constant.ImageFormat;
import io.webfolder.cdp.type.dom.Rect;
import io.webfolder.cdp.type.domdebugger.EventListener;
import io.webfolder.cdp.type.profiler.CoverageRange;
import io.webfolder.cdp.type.profiler.FunctionCoverage;
import io.webfolder.cdp.type.profiler.ScriptCoverage;
import io.webfolder.cdp.type.runtime.EvaluateResult;
import io.webfolder.cdp.type.runtime.RemoteObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChromeSessionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromeSessionHandler.class);

    private static Session session;
    private static Map<String, CoverageInfo> coverageMap;
    private static URI initialURL;

    public static Session get(URI initialURL) {
        if (null == session) {
            int chromeDebuggerPort = Integer.valueOf(System.getProperty("chrome.debugger.port"));
            String firstTab = System.getProperty("chrome.debugger.firsttab");
            SessionFactory factory = new SessionFactory(chromeDebuggerPort);
            session = factory.connect(firstTab);
            session.getCommand().getDebugger().enable();
            session.getCommand().getProfiler().enable();
            session.getCommand().getProfiler().startPreciseCoverage(false, true);
            session.activate();
            session.waitDocumentReady();
            ChromeSessionHandler.initialURL = initialURL;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return session;
    }

    public static void clear() {
        if (null != session) {
            if (session.isConnected()) {
                session.getCommand().getProfiler().stopPreciseCoverage();
            }
            session.close();
            session = null;
            coverageMap = null;
            initialURL = null;
        }
    }

    private static Map<String, CoverageInfo> takePreciseCoverage() {
        String currentUrl = session.getLocation();
        Map<String, CoverageInfo> preciseCoverage = new HashMap<>();
        if (UrlUtils.isSameDomain(currentUrl, initialURL)) {
            List<ScriptCoverage> scriptCoverages = session.getCommand().getProfiler().takePreciseCoverage();
            for (ScriptCoverage scriptCoverage : scriptCoverages) {
                String url = scriptCoverage.getUrl();
                if (null != url && !"".equals(url) && !url.startsWith("extensions::")) {
                    String scriptId = scriptCoverage.getScriptId();
                    try {
                        String scriptSource = null;
                        try {
                            scriptSource = session.getCommand().getDebugger().getScriptSource(scriptId);
                        } catch (CommandException cex) {
                            if (coverageMap != null && cex.getMessage().contains("No script for id: ")) {
                                for (String hash : coverageMap.keySet()) {
                                    CoverageInfo coverageInfo = coverageMap.get(hash);
                                    if (coverageInfo.getScriptId().equals(scriptId)) {
                                        scriptSource = coverageInfo.getScriptContents();
                                        break;
                                    }
                                }
                                if (scriptSource == null) {
                                    throw cex;
                                }
                            } else {
                                throw cex;
                            }
                        }
                        if (null != scriptSource) {
                            String scriptSourceHash = DigestUtils.sha1Hex(scriptSource.getBytes(Charsets.UTF_8));
                            String scriptSourceCopy = scriptSource;
                            CoverageInfo coverageInfo = preciseCoverage.computeIfAbsent(scriptSourceHash, key -> new CoverageInfo(scriptId, url, scriptSourceCopy));
                            List<FunctionCoverage> functions = scriptCoverage.getFunctions();
                            for (FunctionCoverage functionCoverage : functions) {
                                for (CoverageRange coverageRange : functionCoverage.getRanges()) {
                                    if (coverageRange.getCount() == 0) {
                                        coverageInfo.clear(coverageRange.getStartOffset(), coverageRange.getEndOffset());
                                    } else {
                                        coverageInfo.setCovered(coverageRange.getStartOffset(), coverageRange.getEndOffset());
                                    }
                                }
                            }
                        }
                    } catch (CommandException cex) {
                        LOGGER.warn("CommandException: {}", cex);
                    }
                }
            }
        } else {
            LOGGER.info("The initial URL and the current URL are not under the same domain (initial: {}, current: {})", initialURL, currentUrl);
        }
        return preciseCoverage;
    }

    public static String takeDOM() {
        if (session != null) {
            return session.getContent();
        } else {
            return "";
        }
    }

    public static BufferedImage takeScreenShot(double deviceScaleFactor) {
        if (session == null) {
            return null;
        }
        Emulation emulation = session.getCommand().getEmulation();
        Rect contentSizeRectangle = session.getCommand().getPage().getLayoutMetrics().getContentSize();
        int width = contentSizeRectangle.getWidth().intValue();
        int height = contentSizeRectangle.getHeight().intValue();
        emulation.setDeviceMetricsOverride(width, height, deviceScaleFactor, false);
        byte[] pageScreenshotBytes =
                session.getCommand().getPage().captureScreenshot(ImageFormat.Png, 100, null, true);
        emulation.clearDeviceMetricsOverride();
        emulation.resetPageScaleFactor();
        if (null != pageScreenshotBytes) {
            InputStream in = new ByteArrayInputStream(pageScreenshotBytes);
            try {
                return ImageIO.read(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Map<String, CoverageInfo> takePreciseCoverageSoFarCovered() {
        Map<String, CoverageInfo> newCoverage = takePreciseCoverage();
        for (String scriptHash : newCoverage.keySet()) {
            CoverageInfo newCoverageInfo = newCoverage.get(scriptHash);
            CoverageInfo alreadyCoveredCoverageInfo = coverageMap.get(scriptHash);
            if (null == alreadyCoveredCoverageInfo) {
                coverageMap.put(scriptHash, newCoverageInfo);
            } else {
                alreadyCoveredCoverageInfo.or(newCoverageInfo);
            }
        }
        return coverageMap;
    }

    public static void highlightNode(String xpath) {
        if (session != null) {
            session.getCommand().getDOM().enable();
            Runtime runtime = session.getCommand().getRuntime();
            runtime.enable();
            String expression = String.format("var e=document.evaluate(\"%s\",document,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null).singleNodeValue;if(e){e.style.border='solid 1px red';}", xpath);
            runtime.evaluate(expression);
            /*Overlay overlay = session.getCommand().getOverlay();
            overlay.enable();
            HighlightConfig config = new HighlightConfig();
            RGBA rgba = new RGBA();
            rgba.setA(1D);
            rgba.setR(255);
            rgba.setG(0);
            rgba.setB(0);
            config.setShowInfo(true);
            config.setDisplayAsMaterial(true);
            config.setBorderColor(rgba);
            overlay.highlightNode(config, null, null, evaluate.getResult().getObjectId());*/
        }
    }

    public static void initCoverage() {
        coverageMap = takePreciseCoverage();
    }

    public static Map<String, CoverageInfo> getLastCoverageAvailable() {
        return coverageMap;
    }

    public static List<String> getEventsWithListenersAttached(String xpath) {
        List<String> listeners = new ArrayList<>();
        try {
            Runtime runtime = session.getCommand().getRuntime();
            runtime.enable();
            String expression =
                    String.format("document.evaluate(\"%s\",document,null,XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,null).snapshotItem(0)", xpath);
            EvaluateResult evaluate = runtime.evaluate(expression);
            if (evaluate.getExceptionDetails() == null) {
                RemoteObject remoteObject = evaluate.getResult();
                DOMDebugger domDebugger = session.getCommand().getDOMDebugger();
                List<EventListener> eventListeners = domDebugger.getEventListeners(remoteObject.getObjectId());
                for (EventListener eventListener : eventListeners) {
                    listeners.add(eventListener.getType());
                }
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Unable to get events for {}", xpath);
        }
        return listeners;
    }
}
