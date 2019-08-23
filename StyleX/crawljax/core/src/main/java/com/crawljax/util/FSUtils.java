package com.crawljax.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FSUtils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FSUtils.class.getName());
	

	/**
	 * Checks the existence of the directory. If it does not exist, the method creates it.
	 * 
	 * @param dir
	 *            the directory to check.
	 * @throws IOException
	 *             if fails.
	 */
	public static void directoryCheck(String dir) throws IOException {
		final File file = new File(dir);

		if (!file.exists()) {
			FileUtils.forceMkdir(file);
		}
	}

	/**
	 * Checks whether the folder exists for fname, and creates it if necessary.
	 * 
	 * @param fname
	 *            folder name.
	 * @throws IOException
	 *             an IO exception.
	 */
	public static void checkFolderForFile(String fname) throws IOException {

		if (fname.lastIndexOf(File.separator) > 0) {
			String folder = fname.substring(0, fname.lastIndexOf(File.separator));
			directoryCheck(folder);
		}
}
}
