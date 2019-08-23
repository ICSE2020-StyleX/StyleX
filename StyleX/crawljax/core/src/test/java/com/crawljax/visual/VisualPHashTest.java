package com.crawljax.visual;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public class VisualPHashTest {

	final static PHash PHASH = new PHash();

	@Test
	public void testPHash() throws URISyntaxException, IOException {

		String hash = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));

		assertThat(hash, is("1101000000111101110101011111111110101110110011111100011111000111"));
	}

	@Test
	public void testPHashSame() throws URISyntaxException, IOException {
		long startTime = System.currentTimeMillis();

		String hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state1.jpg").toURI())));
		long elapsedTime = System.currentTimeMillis() - startTime;
		System.out.println("p-hash Run time: " + elapsedTime);

		String hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state3.jpg").toURI())));

		assertEquals(hash1, hash2);

		hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));
		hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));

		assertEquals(hash1, hash2);
	}

	@Test
	public void testPHashDifferent() throws URISyntaxException, IOException {

		String hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));
		String hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state3.jpg").toURI())));

		assertNotEquals(hash1, hash2);

		hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state1.jpg").toURI())));
		hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state6.jpg").toURI())));

		assertNotEquals(hash1, hash2);

		hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));
		hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state1.jpg").toURI())));

		assertNotEquals(hash1, hash2);
	}

	@Test
	public void testPHashSameMD5() throws URISyntaxException, IOException {
		long startTime = System.currentTimeMillis();

		String hashMD1 = Files.hash(
		        new File(VisualPHashTest.class.getResource("/screenshots/state1.jpg").toURI()),
		        Hashing.md5()).toString();
		long elapsedTime = System.currentTimeMillis() - startTime;
		System.out.println("MD5 Run time: " + elapsedTime);

		String hashMD2 = Files.hash(
		        new File(VisualPHashTest.class.getResource("/screenshots/state3.jpg").toURI()),
		        Hashing.md5()).toString();

		assertEquals(hashMD1, hashMD2);

	}

	@Test
	public void testPHashDifferentMD5() throws URISyntaxException, IOException {

		String hashMD1 = Files.hash(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI()),
		        Hashing.md5()).toString();
		String hashMD2 = Files.hash(
		        new File(VisualPHashTest.class.getResource("/screenshots/state3.jpg").toURI()),
		        Hashing.md5()).toString();

		assertNotEquals(hashMD1, hashMD2);
	}

	@Test
	public void testPHashSameSFG() throws URISyntaxException, IOException {

		String hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state1.jpg").toURI())));

		String hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/state3.jpg").toURI())));

		assertEquals(hash1, hash2);

		hash1 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));
		hash2 = PHASH.getHash(ImageIO.read(
		        new File(VisualPHashTest.class.getResource("/screenshots/index.jpg").toURI())));

		assertEquals(hash1, hash2);
	}

}
