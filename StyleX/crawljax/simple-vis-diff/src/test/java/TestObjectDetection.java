
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.opencv.imgcodecs.Imgcodecs;

import detection.ObjectDetection;
import pageobject.IPageObjectFactory;
import pageobject.MD5PageObjectFactory;

public class TestObjectDetection {
	
	private static final IPageObjectFactory pageObjectFactory = new MD5PageObjectFactory();

	@Test
	public void test() {

		/* Run the detection algorithm. */
		String name = "townshoes";
		ObjectDetection detection = new ObjectDetection(pageObjectFactory, "src/test/resources/" + name + ".png");

		detection.detectObjects();

		/* Write the annotated file to disk. */
		String folderName = "target/output/";
		String fileName = folderName + "townshoes-objects" + Math.random() + ".png";
		try {
			ObjectDetection.directoryCheck(folderName);
			Imgcodecs.imwrite(fileName, detection.getAnnotated());
			File created = new File(fileName);
			assertTrue(created.exists());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testWebPage() {

		/* Run the detection algorithm. */
		String name = "state12";
		ObjectDetection detection = new ObjectDetection(pageObjectFactory, "src/test/resources/" + name + ".jpg");
		detection.detectObjects();

		/* Write the annotated file to disk. */
		String folderName = "target/output/";
		String fileName = folderName + "state12" + Math.random() + ".png";
		try {
			ObjectDetection.directoryCheck(folderName);
			Imgcodecs.imwrite(fileName, detection.getAnnotated());
			File created = new File(fileName);
			assertTrue(created.exists());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

}
