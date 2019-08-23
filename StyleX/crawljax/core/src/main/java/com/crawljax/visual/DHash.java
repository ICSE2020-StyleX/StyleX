package com.crawljax.visual;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.apache.commons.text.similarity.HammingDistance;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/*
 * DHash-like image hash. 
 * Author: astocco (astocco@ece.ubc.ca) 
 * Based On: https://www.pyimagesearch.com/2017/11/27/image-hashing-opencv-python/
 */
public class DHash implements VisualHashCalculator {

	static {
		nu.pattern.OpenCV.loadShared();
		nu.pattern.OpenCV.loadLocally();
	}

	private static final Logger LOG = LoggerFactory.getLogger(DHash.class);

	private int size = 8;

	/* Returns the difference hashing (DHash for short) of the image. */
	public String getDHash(String object) {

		/*
		 * 1. Convert to grayscale. The first step is to convert the input image to
		 * grayscale and discard any color information.
		 * 
		 * Discarding color enables us to: (1) Hash the image faster since we only have
		 * to examine one channel (2) Match images that are identical but have slightly
		 * altered color spaces (since color information has been removed).
		 * 
		 * If, for whatever reason, one is interested in keeping the color information,
		 * he can run the hashing algorithm on each channel independently and then
		 * combine at the end (although this will result in a 3x larger hash).
		 */
		Mat objectImage = Imgcodecs.imread(object, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

		return getDHash(objectImage);
		
	}
	
	/**
	 * Computes DHash on a grayscale image
	 * @param objectImage A grayscale image
	 * @return The DHash of the image
	 */
	private String getDHash(Mat objectImage) {
		/*
		 * 2. Resize image. We squash the image down to 9×8 and ignore aspect ratio to
		 * ensure that the resulting image hash will match similar photos regardless of
		 * their initial spatial dimensions.
		 * 
		 * Why 9×8? We are implementing difference hash. The difference hash algorithm
		 * works by computing the difference (i.e., relative gradients) between adjacent
		 * pixels.
		 * 
		 * If we take an input image with 9 pixels per row and compute the difference
		 * between adjacent column pixels, we end up with 8 differences. Eight rows of
		 * eight differences (i.e., 8×8) is 64 which will become our 64-bit hash.
		 *
		 */
		Mat resized = new Mat();
		Imgproc.resize(objectImage, resized, new Size(size + 1, size));

		/*
		 * 3. Compute the difference image. The difference hash algorithm works by
		 * computing the difference (i.e., relative gradients) between adjacent pixels.
		 * 
		 * In practice we don't actually have to compute the difference — we can apply a
		 * “greater than” test (or “less than”, it doesn’t really matter as long as the
		 * same operation is consistently used).
		 */
		String hash = "";

		for (int i = 0; i < resized.rows(); i++) {

			for (int j = 0; j < resized.cols() - 1; j++) {

				double[] pixel_left = resized.get(i, j);
				double[] pixel_right = resized.get(i, j + 1);

				hash += (pixel_left[0] > pixel_right[0] ? "1" : "0");

			}
		}

		LOG.info("DHash: " + hash);
		return hash;
	}
		
	@Override
	public String getVisualHash(BufferedImage image) {
		Mat mat = matify(image);
		
		try {
			// Convert the image to grayscale
			//BufferedImage grayscaleImage = grayscale(image);
			
			BufferedImage image1 = mat2Img(mat);
			ImageIO.write(image1 , "jpeg", new File("1.jpg"));
			
			ImageIO.write(image, "jpeg", new File("original.jpg"));
			Mat mat22 = Imgcodecs.imread("original.jpg");
			
			BufferedImage image22 = mat2Img(mat22);
			ImageIO.write(image22 , "jpeg", new File("22.jpg"));
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Mat matGrayscale = new Mat(image.getHeight(),image.getWidth(),CvType.CV_8UC1);
		Imgproc.cvtColor(mat, matGrayscale, Imgproc.COLOR_RGB2GRAY);
		return getDHash(matGrayscale);
	}

	
	
	/**
	 * Accepts a BufferedImage and returns a Mat.
	 * This is to make the DHash class be able to talk to the exiting code.
	 * Code adopted from http://answers.opencv.org/question/28348/converting-bufferedimage-to-mat-in-java/
	 * @param im The BufferedImage
	 * @return The Mat
	 */
	private Mat matify(BufferedImage im) {
	    // Convert INT to BYTE
	    //im = new BufferedImage(im.getWidth(), im.getHeight(),BufferedImage.TYPE_3BYTE_BGR);
	    // Convert bufferedimage to byte array
	    byte[] pixels = ((DataBufferByte) im.getRaster().getDataBuffer()).getData();

	    // Create a Matrix the same size of image
	    int curCVtype = -1;
	    switch (im.getType()) {
	    case BufferedImage.TYPE_3BYTE_BGR:
	        curCVtype = CvType.CV_8UC3;
	        break;
	    case BufferedImage.TYPE_BYTE_GRAY:
	        curCVtype = CvType.CV_8UC1;
	        break;
	    case BufferedImage.TYPE_INT_BGR:
	    case BufferedImage.TYPE_INT_RGB:
	        curCVtype = CvType.CV_8SC3;
	        break;
	    case BufferedImage.TYPE_INT_ARGB:
	    case BufferedImage.TYPE_INT_ARGB_PRE:
	        curCVtype = CvType.CV_8SC4;
	        break;
	    default:
	    	}
	    Mat image = new Mat(im.getHeight(), im.getWidth(), curCVtype);
	    // Fill Matrix with image values
	    image.put(0, 0, pixels);

	    return image;
	}
 

public static BufferedImage mat2Img(Mat mat) {  
	MatOfByte bytemat = new MatOfByte();

	Imgcodecs.imencode(".jpg", mat, bytemat);

	byte[] bytes = bytemat.toArray();

	InputStream in = new ByteArrayInputStream(bytes);

	try {
		return ImageIO.read(in);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
		return null;
	}  
  }  

	private BufferedImage grayscale(BufferedImage img) {
		ColorConvertOp colorConvert = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
		colorConvert.filter(img, img);
		return img;
	}

	/**
	 * compares the DHash of two images and return whether they are perceptually
	 * similar (max 10 different pixels allowed)
	 * 
	 * @param img1
	 * @param img2
	 * @return true/false
	 */
	public boolean imagesPerceptuallySimilar(String img1, String img2) {
		return (distance(getDHash(img1), getDHash(img2)) > 10) ? false : true;
	}

	/**
	 * compares the DHash of two images and return whether they are perceptually
	 * similar (max @threshold different pixels allowed)
	 * 
	 * @param img1
	 * @param img2
	 * @param threshold
	 * @return true/false
	 */
	public boolean imagesPerceptuallySimilar(String img1, String img2, int threshold) {
		return (distance(getDHash(img1), getDHash(img2)) > threshold) ? false : true;
	}
	
	/**
	 * Calculate the Hamming distance between two hashes
	 * @param h1
	 * @param h2
	 * @return
	 */
	public static Integer distance(String h1, String h2) {
		HammingDistance distance = new HammingDistance();
		return distance.apply(h1, h2);
	}

	public static void main(String[] args) {

		String image1 = "src/test/resources/screenshots/bookobject.jpg";
		String image2 = "src/test/resources/screenshots/bookscene.jpg";

		final DHash DHash = new DHash();

		if (DHash.imagesPerceptuallySimilar(image1, image2))
			System.out.println("Images are perceptually similar");
		else
			System.out.println("Images are different");

		long startTime = System.currentTimeMillis();

		String DHash1 = DHash.getDHash(image1);

		System.out.println("DHash in: (ms) " + (System.currentTimeMillis() - startTime));

		startTime = System.currentTimeMillis();

		String DHash2 = DHash.getDHash(image2);

		System.out.println("DHash in: (ms) " + (System.currentTimeMillis() - startTime));

		try {

			InputStream is1 = Files.asByteSource(new File(image1)).openStream();
			InputStream is2 = Files.asByteSource(new File(image2)).openStream();

			final PHash pHash = new PHash();

			startTime = System.currentTimeMillis();

			String pHash1 = pHash.getHash(ImageIO.read(is1));

			System.out.println("phash in: (ms) " + (System.currentTimeMillis() - startTime));

			startTime = System.currentTimeMillis();

			String pHash2 = pHash.getHash(ImageIO.read(is2));

			System.out.println("phash in: (ms) " + (System.currentTimeMillis() - startTime));

			HammingDistance distance = new HammingDistance();

			LOG.info("Distance (DHash1, DHash2): " + distance.apply(DHash1, DHash2));
			LOG.info("Distance (pHash1, pHash2): " + distance.apply(pHash1, pHash2));

			LOG.info("Distance (pHash1, DHash1): " + distance.apply(pHash1, DHash1));
			LOG.info("Distance (pHash2, pHash2): " + distance.apply(pHash2, DHash2));

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}