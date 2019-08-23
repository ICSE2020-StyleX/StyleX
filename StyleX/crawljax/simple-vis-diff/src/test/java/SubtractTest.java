import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import detection.ObjectDetection;

public class SubtractTest {

	static {
		nu.pattern.OpenCV.loadShared();
	}

	@Test
	public void test() {

		Mat image1 = Imgcodecs.imread("src/test/resources/state12.png");
		Mat image2 = Imgcodecs.imread("src/test/resources/state1.png");

		Mat endResult = new Mat();
		Core.subtract(image1, image2, endResult);
		// Core.subtract(Mat.ones(image1.size(), CvType.CV_32F), image2, endResult);

		String folderName = "target/output/diff/";
		String fileName = folderName + "diffs" + Math.random() + ".png";
		try {
			ObjectDetection.directoryCheck(folderName);
			Imgcodecs.imwrite(fileName, endResult);
			File created = new File(fileName);
			assertTrue(created.exists());
		} catch (IOException e) {
			fail(e.getMessage());
		}

	}
}
