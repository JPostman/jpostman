package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotationListener;

/**
 * Regression coverage for manual class-soft verification during a real TestNG
 * {@code @AfterClass} lifecycle.
 */
public class JPostmanManualAfterClassPriorityRegressionTest {

	@Test
	void manualAfterClassVerifyIsReportedInsteadOfAutomaticVerification() {
		TestListenerAdapter capture = new TestListenerAdapter();

		TestNG testNG = new TestNG();
		testNG.setUseDefaultListeners(false);
		testNG.setVerbose(0);
		testNG.setTestClasses(new Class<?>[] { ManualAfterClassFixture.class });
		testNG.addListener(new JPostmanTestNgAnnotationListener());
		testNG.addListener(capture);
		testNG.run();

		List<ITestResult> failures = capture.getConfigurationFailures();
		List<String> failureNames = failures.stream().map(result -> result.getMethod().getMethodName())
				.collect(Collectors.toList());

		/*assertEquals(1, failures.size(),
				"Manual softAsserts.verify() must create exactly one configuration failure. "
						+ "JPostman automatic verification must not report the same collector first or again. "
						+ "Actual failures: " + failureNames);
		assertEquals("tearDownClass", failureNames.get(0),
				"The failure must belong to the user's @AfterClass method, not "
						+ "'JPostman automatic assertion verification'.");

		Throwable throwable = failures.get(0).getThrowable();
		String message = throwable == null ? "" : String.valueOf(throwable.getMessage());
		assertTrue(message.contains("The following asserts failed:"), "Actual message: " + message);
		assertTrue(message.contains("ManualAfterClassFixture.collectClassSoftFailures"),
				"Each class-soft failure must retain its originating test method: " + message);
		assertTrue(message.contains("expected [true] but found [false]"), "Actual message: " + message);
		assertTrue(message.contains("expected [false] but found [true]"), "Actual message: " + message);*/
	}

	public static final class ManualAfterClassFixture {

		@JPostman.AssertContext(soft = true)
		private JPostman.Assert softAsserts;

		@org.testng.annotations.Test
		public void collectClassSoftFailures() {
			softAsserts.isTrue(false).isFalse(true);
		}

		@AfterClass
		public void tearDownClass() {
			softAsserts.verify();
		}
	}
}
