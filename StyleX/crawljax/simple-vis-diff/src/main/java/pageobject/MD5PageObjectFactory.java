package pageobject;
import org.opencv.core.Mat;

public class MD5PageObjectFactory implements IPageObjectFactory {

	public PageObject makePageObject(Mat image, int x, int y, int width, int height) {
		return new PageObjectMD5(image, x, y, width, height);
	}

}
