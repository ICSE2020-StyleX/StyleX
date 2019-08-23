package com.crawljax.visual;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.apache.commons.text.similarity.HammingDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.io.Files;

/*
 * PHASH-like image hash. Author: Elliot Shepherd (elliot@jarofworms.com Based On:
 * http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
 */
public class PHash implements VisualHashCalculator {
	private static final Logger LOG = LoggerFactory.getLogger(PHash.class);
	private int size = 32;
	private int smallerSize = 8;

	public PHash() {
		initCoefficients();
	}

	public PHash(int size, int smallerSize) {
		this.size = size;
		this.smallerSize = smallerSize;

		initCoefficients();
	}

	public int distance(String s1, String s2) {
		int counter = 0;
		for (int k = 0; k < s1.length(); k++) {
			if (s1.charAt(k) != s2.charAt(k)) {
				counter++;
			}
		}
		return counter;
	}

	public String getHash(byte[] imageByte) throws IOException {
		return getHash(ImageIO.read(new ByteArrayInputStream(imageByte)));
	}

	// Returns a 'binary string' (like. 001010111011100010) which is easy to do a
	// hamming distance
	// on.
	public String getHash(BufferedImage img) {

		/*
		 * 1. Reduce size. Like Average Hash, PHASH starts with a small image. However,
		 * the image is larger than 8x8; 32x32 is a good size. This is really done to
		 * simplify the DCT computation and not because it is needed to reduce the high
		 * frequencies.
		 */
		img = resize(img, size, size);

		/*
		 * 2. Reduce color. The image is reduced to a grayscale just to further simplify
		 * the number of computations.
		 */
		img = grayscale(img);

		double[][] vals = new double[size][size];

		for (int x = 0; x < img.getWidth(); x++) {
			for (int y = 0; y < img.getHeight(); y++) {
				vals[x][y] = getBlue(img, x, y);
			}
		}

		/*
		 * 3. Compute the DCT. The DCT separates the image into a collection of
		 * frequencies and scalars. While JPEG uses an 8x8 DCT, this algorithm uses a
		 * 32x32 DCT.
		 */
		long start = System.currentTimeMillis();
		double[][] dctVals = applyDCT(vals);
		LOG.debug("DCT: " + (System.currentTimeMillis() - start));

		/*
		 * 4. Reduce the DCT. This is the magic step. While the DCT is 32x32, just keep
		 * the top-left 8x8. Those represent the lowest frequencies in the picture.
		 */
		/*
		 * 5. Compute the average value. Like the Average Hash, compute the mean DCT
		 * value (using only the 8x8 DCT low-frequency values and excluding the first
		 * term since the DC coefficient can be significantly different from the other
		 * values and will throw off the average).
		 */
		double total = 0;

		for (int x = 0; x < smallerSize; x++) {
			for (int y = 0; y < smallerSize; y++) {
				total += dctVals[x][y];
			}
		}
		total -= dctVals[0][0];

		double avg = total / (double) ((smallerSize * smallerSize) - 1);

		/*
		 * 6. Further reduce the DCT. This is the magic step. Set the 64 hash bits to 0
		 * or 1 depending on whether each of the 64 DCT values is above or below the
		 * average value. The result doesn't tell us the actual low frequencies; it just
		 * tells us the very-rough relative scale of the frequencies to the mean. The
		 * result will not vary as long as the overall structure of the image remains
		 * the same; this can survive gamma and color histogram adjustments without a
		 * problem. Fixed as suggested at https://stackoverflow.com/questions/8178614/
		 */
		String hash = "";

		for (int x = 0; x < smallerSize; x++) {
			for (int y = 0; y < smallerSize; y++) {
				hash += (dctVals[x][y] > avg ? "1" : "0");
			}
		}
		LOG.info("Visual hash: " + hash);
		return hash;
	}

	private BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = resizedImage.createGraphics();
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return resizedImage;
	}

	private ColorConvertOp colorConvert = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

	private BufferedImage grayscale(BufferedImage img) {
		colorConvert.filter(img, img);
		return img;
	}

	private static int getBlue(BufferedImage img, int x, int y) {
		return (img.getRGB(x, y)) & 0xff;
	}

	// DCT function from
	// http://stackoverflow.com/questions/4240490/problems-with-dct-and-idct-algorithm-in-java

	private double[] c;

	private void initCoefficients() {
		c = new double[size];

		for (int i = 1; i < size; i++) {
			c[i] = 1;
		}
		c[0] = 1 / Math.sqrt(2.0);
	}

	private double[][] applyDCT(double[][] f) {
		int N = size;

		double[][] F = new double[N][N];
		for (int u = 0; u < N; u++) {
			for (int v = 0; v < N; v++) {
				double sum = 0.0;
				for (int i = 0; i < N; i++) {
					for (int j = 0; j < N; j++) {
						sum += Math.cos(((2 * i + 1) / (2.0 * N)) * u * Math.PI)
								* Math.cos(((2 * j + 1) / (2.0 * N)) * v * Math.PI) * (f[i][j]);
					}
				}
				sum *= ((c[u] * c[v]) / 4.0);
				F[u][v] = sum;
			}
		}
		return F;
	}

	public static void main(String[] args) {

		String image1 = "/Users/amesbah/repos/git/saltlab/art/crawljax/examples/out/phptravels.com/crawl12/screenshots/state3.jpg";
		String image2 = "/Users/amesbah/repos/git/saltlab/art/crawljax/examples/out/phptravels.com/crawl12/screenshots/state1.jpg";

		final PHash pHash = new PHash();

		try {

			InputStream is1 = Files.asByteSource(new File(image1)).openStream();
			InputStream is2 = Files.asByteSource(new File(image2)).openStream();
			long startTime = System.currentTimeMillis();
			String hash1 = pHash.getHash(ImageIO.read(is1));
			long stopTime = System.currentTimeMillis();
			long elapsedTime = stopTime - startTime;
			System.out.println("Run time: " + elapsedTime);
			String hash2 = pHash.getHash(ImageIO.read(is2));
			System.out.println("Hash 1: " + hash1 + " intHash: " + Objects.hashCode(hash1));
			System.out.println("Hash 2: " + hash2 + " intHash: " + Objects.hashCode(hash2));
			HammingDistance distance = new HammingDistance();
			int d = distance.apply(hash1, hash2);
			System.out.println("Distance: " + d);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public String getVisualHash(BufferedImage image) {
		// TODO I kept the original method for backward compatibility, should remove it later
		return getHash(image);
	}

}