package com.crawljax.plugins.testcasegenerator;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.plugin.Plugin;
import com.crawljax.util.FSUtils;

/**
 * @author mesbah
 * @version $Id: JavaTestGenerator.java 6234 2009-12-18 13:46:37Z mesbah $
 */
public class JavaTestGenerator {

	private final VelocityEngine engine;
	private final VelocityContext context;
	private final String className;

	/**
	 * @param className
	 * @param url
	 * @throws Exception
	 */
	public JavaTestGenerator(String className, String url, List<TestMethod> testMethods,
			CrawljaxConfiguration config, String testSuitePath, String screenshotPath, 
			String diffPath) throws Exception {
		engine = new VelocityEngine();
		/* disable logging */
		engine.setProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM_CLASS,
		        "org.apache.velocity.runtime.log.NullLogChute");
		// tell Velocity to look in classpath for template file
		engine.setProperty("resource.loader", "file");
		engine.setProperty("file.resource.loader.class", 
				"org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		
		engine.init();
		context = new VelocityContext();
		this.className = className;
		context.put("date", new Date().toString());
		context.put("classname", className);
		context.put("url", url);

		context.put("waitAfterEvent", config.getCrawlRules().getWaitAfterEvent());
		context.put("waitAfterReloadUrl", config.getCrawlRules().getWaitAfterReloadUrl());

		/*
		 * boolean usePropertiesFile = PropertyHelper.getPropertiesFileName() != null &&
		 * !PropertyHelper.getPropertiesFileName().equals(""); context.put("usePropertiesFile",
		 * usePropertiesFile); context.put("propertiesfile",
		 * PropertyHelper.getPropertiesFileName());
		 */
		context.put("methodList", testMethods);
		context.put("database", true);
		context.put("testSuitePath", testSuitePath);
		
		context.put("crawlScreenshots", screenshotPath + File.separator + "screenshots");
		context.put("diffScreenshots", diffPath);
	}

	public void useJsonInsteadOfDB(String jsonStates, String jsonEventables) {
		context.put("jsonstates", jsonStates);
		context.put("jsoneventables", jsonEventables);
		context.put("database", false);
	}

	/**
	 * @param outputFolder
	 * @param fileNameTemplate
	 * @return filename of generates class
	 * @throws Exception
	 */
	public String generate(String outputFolder, String fileNameTemplate) throws Exception {

		Template template = engine.getTemplate(fileNameTemplate);
		FSUtils.directoryCheck(outputFolder);
		File f = new File(outputFolder + className + ".java");
		FileWriter writer = new FileWriter(f);
		template.merge(context, writer);
		writer.flush();
		writer.close();
		return f.getAbsolutePath();

	}
}
