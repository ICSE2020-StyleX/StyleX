package com.crawljax.visual;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

public class VisualDHashTest {

	final static DHash DHASH = new DHash();

	@Test
	public void testDHash() throws URISyntaxException, IOException {
		
		String file = VisualDHashTest.class.getResource("/screenshots/bookobject.jpg").toURI().getPath();
		String hash = DHASH.getDHash(file);
		assertThat(hash, is("1011011111111110110111101100111011011110010111100101110100011101"));
		
		file = VisualDHashTest.class.getResource("/screenshots/bookscene.jpg").toURI().getPath();
		hash = DHASH.getDHash(file);
		assertThat(hash, is("0010101111000100110011100100111101010011000100011010000101010111"));
		
		file = VisualDHashTest.class.getResource("/screenshots/oracle.png").toURI().getPath();
		hash = DHASH.getDHash(file);
		assertThat(hash, is("1110000000000000000100001101000010010000000100000000000000010001"));
		
		file = VisualDHashTest.class.getResource("/screenshots/test.png").toURI().getPath();
		hash = DHASH.getDHash(file);
		assertThat(hash, is("1110000000000000000100001101100010010000000100000000000000010001"));		
	}

	@Test
	public void testDHashIdenticalImages() throws URISyntaxException, IOException {
		
		String file = VisualDHashTest.class.getResource("/screenshots/bookobject.jpg").toURI().getPath();
		String file2 = VisualDHashTest.class.getResource("/screenshots/bookobject.jpg").toURI().getPath();
		assertTrue(DHASH.imagesPerceptuallySimilar(file, file2));
		
		file = VisualDHashTest.class.getResource("/screenshots/bookscene.jpg").toURI().getPath();
		file2 = VisualDHashTest.class.getResource("/screenshots/bookscene.jpg").toURI().getPath();
		assertTrue(DHASH.imagesPerceptuallySimilar(file, file2));
		
		file = VisualDHashTest.class.getResource("/screenshots/oracle.png").toURI().getPath();
		file2 = VisualDHashTest.class.getResource("/screenshots/oracle.png").toURI().getPath();
		assertTrue(DHASH.imagesPerceptuallySimilar(file, file2));
		
		file = VisualDHashTest.class.getResource("/screenshots/test.png").toURI().getPath();
		file2 = VisualDHashTest.class.getResource("/screenshots/test.png").toURI().getPath();
		assertTrue(DHASH.imagesPerceptuallySimilar(file, file2));
	}
	
	@Test
	public void testDHashSimilarImages() throws URISyntaxException, IOException {
		
		String file = VisualDHashTest.class.getResource("/screenshots/oracle.png").toURI().getPath();
		String file2 = VisualDHashTest.class.getResource("/screenshots/test.png").toURI().getPath();
		assertTrue(DHASH.imagesPerceptuallySimilar(file, file2));
		
	}
	
	@Test
	public void testDHashDifferentImages() throws URISyntaxException, IOException {
		
		String file = VisualDHashTest.class.getResource("/screenshots/bookscene.jpg").toURI().getPath();
		String file2 = VisualDHashTest.class.getResource("/screenshots/bookobject.jpg").toURI().getPath();
		assertFalse(DHASH.imagesPerceptuallySimilar(file, file2));
		
	}

}
