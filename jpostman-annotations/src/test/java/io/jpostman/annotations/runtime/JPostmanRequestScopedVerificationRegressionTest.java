package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotationListener;
import io.jpostman.testng.TestNgContext;

/** Regression coverage for request-scoped soft AssertContext verification. */
public class JPostmanRequestScopedVerificationRegressionTest {

	@Test
	void responseAutomaticallyVerifiesSoftAssertContextAsTestFailure() {
		TestNgReport report = runFixture(AutoResponseFixture.class);

		assertEquals(1, report.testsCompleted());
		assertEquals(0, report.passedTests.size());
		assertEquals(1, report.failedTests.size());
		assertEquals(0, report.failedConfigurations.size());
		assertFailure(report.failedTests.get(0), "AutoResponseFixture.response");
		assertFalse(report.sinkOutput.contains("FAILED CONFIGURATION:"), report.sinkOutput);
	}

	@Test
	void manualVerifyInsideResponseDoesNotCreateDuplicateFailure() {
		TestNgReport report = runFixture(ManualResponseFixture.class);

		assertEquals(1, report.testsCompleted());
		assertEquals(0, report.passedTests.size());
		assertEquals(1, report.failedTests.size());
		assertEquals(0, report.failedConfigurations.size());
		assertFailure(report.failedTests.get(0), "ManualResponseFixture.response");
	}

	@Test
	void classCompletionDoesNotVerifySoftContextOutsideResponseOrRunner() {
		TestNgReport report = runFixture(PlainTestFixture.class);

		assertEquals(1, report.testsCompleted());
		assertEquals(1, report.passedTests.size());
		assertEquals(0, report.failedTests.size());
		assertEquals(0, report.failedConfigurations.size());
	}

	private static TestNgReport runFixture(Class<?> fixtureClass) {
		TestListenerAdapter capture = new TestListenerAdapter();
		StringBuilder sink = new StringBuilder();
		TestNG testNG = new TestNG();
		testNG.setUseDefaultListeners(false);
		testNG.setVerbose(0);
		testNG.setTestClasses(new Class<?>[] { fixtureClass });
		testNG.addListener(new JPostmanTestNgAnnotationListener());
		testNG.addListener(capture);
		try (JPostmanOutputs.Scope ignored = JPostmanOutputs.use(sink::append)) {
			testNG.run();
		}
		return TestNgReport.from(capture, sink.toString());
	}

	private static void assertFailure(ITestResult result, String expectedMethod) {
		assertEquals(ITestResult.FAILURE, result.getStatus());
		String message = result.getThrowable() == null ? "" : String.valueOf(result.getThrowable().getMessage());
		assertTrue(message.contains("The following asserts failed:"), message);
		assertTrue(message.contains("expected [true] but found [false]"), message);
		assertTrue(message.contains("expected [false] but found [true]"), message);
		assertFalse(message.contains(expectedMethod), message);
	}

	private static ApiExecutor successfulExecutor() {
		String json = "{\"id\":1}";
		return () -> new ApiResponse(200, json, json.getBytes(StandardCharsets.UTF_8), Map.of());
	}

	private static final class TestNgReport {
		private final List<ITestResult> passedTests = new ArrayList<>();
		private final List<ITestResult> failedTests = new ArrayList<>();
		private final List<ITestResult> skippedTests = new ArrayList<>();
		private final List<ITestResult> failedConfigurations = new ArrayList<>();
		private final String sinkOutput;

		private TestNgReport(String sinkOutput) {
			this.sinkOutput = sinkOutput;
		}

		private static TestNgReport from(TestListenerAdapter capture, String sinkOutput) {
			TestNgReport report = new TestNgReport(sinkOutput);
			capture.getTestContexts().forEach(context -> {
				report.passedTests.addAll(context.getPassedTests().getAllResults());
				report.failedTests.addAll(context.getFailedTests().getAllResults());
				report.skippedTests.addAll(context.getSkippedTests().getAllResults());
				report.failedConfigurations.addAll(context.getFailedConfigurations().getAllResults());
			});
			return report;
		}

		private int testsCompleted() {
			return passedTests.size() + failedTests.size() + skippedTests.size();
		}
	}

	public static final class AutoResponseFixture {
		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext(soft = true)
		private JPostman.Assert asserts;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return successfulExecutor();
		}

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void response() {
			asserts.isTrue(false).isFalse(true);
		}
	}

	public static final class ManualResponseFixture {
		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext(soft = true)
		private JPostman.Assert asserts;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return successfulExecutor();
		}

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void response() {
			asserts.isTrue(false).isFalse(true);
			asserts.verify();
		}
	}

	public static final class PlainTestFixture {
		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext(soft = true)
		private JPostman.Assert asserts;

		@org.testng.annotations.Test
		public void plainTest() {
			asserts.isTrue(false);
		}
	}
}
