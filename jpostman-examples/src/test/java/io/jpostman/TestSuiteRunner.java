package io.jpostman;

import org.testng.TestNG;

/**
 * TestNG suite runner.
 */
public final class TestSuiteRunner {

	private TestSuiteRunner() {
		// Utility class
	}

	public static void main(String[] args) {
		TestNG testng = new TestNG();

		testng.setTestClasses(new Class[] {
				TestJPostman.class, 
				TestHttpClient.class, 
				TestRestAssured.class,
				TestPlaywright.class, 
				TestUnirest.class
		});

		testng.setDefaultSuiteName("JPostman Suite - See Report: jpostman-examples/test-output/index.html");
		testng.setDefaultTestName("JPostman Tests");
		testng.setVerbose(1);

		testng.run();
	}
}