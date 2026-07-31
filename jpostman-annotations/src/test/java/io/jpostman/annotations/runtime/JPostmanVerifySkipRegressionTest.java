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

/** Regression coverage for verify=1 deferred skipped-test behavior. */
public class JPostmanVerifySkipRegressionTest {

	@Test
	void responseVerifyOneRunsBodyThenReportsSkipped() {
		ResponseSkipFixture.bodyRan = false;
		TestNgReport report = runFixture(ResponseSkipFixture.class);

		assertTrue(ResponseSkipFixture.bodyRan, "verify=1 must not abort the user body");
		assertEquals(0, report.passed.size());
		assertEquals(0, report.failed.size());
		assertEquals(1, report.skipped.size());
	}

	@Test
	void responseVerifyZeroKeepsSuccessfulResult() {
		ResponsePassFixture.bodyRan = false;
		TestNgReport report = runFixture(ResponsePassFixture.class);

		assertTrue(ResponsePassFixture.bodyRan);
		assertEquals(1, report.passed.size());
		assertEquals(0, report.failed.size());
		assertEquals(0, report.skipped.size());
	}

	@Test
	void contextVerifyOneAppliesThroughAnnotationDefault() {
		ContextSkipFixture.bodyRan = false;
		TestNgReport report = runFixture(ContextSkipFixture.class);

		assertTrue(ContextSkipFixture.bodyRan);
		assertEquals(0, report.failed.size());
		assertEquals(1, report.skipped.size());
	}

	@Test
	void runtimeCallVerifyOneContinuesAfterCallThenReportsSkipped() {
		CallSkipFixture.afterCall = false;
		TestNgReport report = runFixture(CallSkipFixture.class);

		assertTrue(CallSkipFixture.afterCall, "runtime.call must return before the final skip is applied");
		assertEquals(0, report.failed.size());
		assertEquals(1, report.skipped.size());
	}

	@Test
	void failingUserAssertionStillWinsOverVerifyOne() {
		TestNgReport report = runFixture(FailingBodyFixture.class);

		assertEquals(0, report.passed.size());
		assertEquals(1, report.failed.size());
		assertEquals(0, report.skipped.size());
		assertFalse(String.valueOf(report.failed.get(0).getThrowable()).isBlank());
	}

	@Test
	void runnerVerifyOneEmitsInheritedContextOutputOncePerAnnotationAndRequest() {
		List<String> output = new ArrayList<>();
		TestNgReport report;
		try (JPostmanOutputs.Scope ignored = JPostmanOutputs.use(output::add)) {
			report = runFixture(RunnerSkipDebugFixture.class);
		}

		String text = String.join("", output).replace("\r\n", "\n");
		assertEquals(0, report.failed.size());
		assertEquals(1, report.skipped.size());
		assertEquals(3, occurrences(text, "=== runner ==="), text);
		assertEquals(3, occurrences(text, "=== executor ==="), text);
		assertEquals(3, occurrences(text, "annotation=@JPostmanRunner"), text);
		assertEquals(3, occurrences(text, "annotation=@JPostmanExecutor"), text);
		assertEquals(6, occurrences(text, "JPostmanInfo {"), text);
		assertEquals(3, occurrences(text, "[POST  ]"), text);
		assertTrue(text.contains("Folder request one"), text);
		assertTrue(text.contains("Folder request two"), text);
		assertTrue(text.contains("Folder request three"), text);
	}

	private static int occurrences(String value, String token) {
		int count = 0;
		int offset = 0;
		while ((offset = value.indexOf(token, offset)) >= 0) {
			count++;
			offset += token.length();
		}
		return count;
	}

	private static TestNgReport runFixture(Class<?> fixtureClass) {
		TestListenerAdapter capture = new TestListenerAdapter();
		TestNG testNG = new TestNG();
		testNG.setUseDefaultListeners(false);
		testNG.setVerbose(0);
		testNG.setTestClasses(new Class<?>[] { fixtureClass });
		testNG.addListener(new JPostmanTestNgAnnotationListener());
		testNG.addListener(capture);
		testNG.run();
		return TestNgReport.from(capture);
	}

	private static ApiExecutor unauthorizedExecutor() {
		String json = "{\"message\":\"unauthorized\"}";
		return () -> new ApiResponse(401, json, json.getBytes(StandardCharsets.UTF_8), Map.of());
	}

	private static final class TestNgReport {
		private final List<ITestResult> passed = new ArrayList<>();
		private final List<ITestResult> failed = new ArrayList<>();
		private final List<ITestResult> skipped = new ArrayList<>();

		private static TestNgReport from(TestListenerAdapter capture) {
			TestNgReport report = new TestNgReport();
			capture.getTestContexts().forEach(context -> {
				report.passed.addAll(context.getPassedTests().getAllResults());
				report.failed.addAll(context.getFailedTests().getAllResults());
				report.skipped.addAll(context.getSkippedTests().getAllResults());
			});
			return report;
		}
	}

	public static final class ResponseSkipFixture {
		static boolean bodyRan;

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json")
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return unauthorizedExecutor();
		}

		@JPostman.Response(request = "Get current auth user", verify = 1)
		@org.testng.annotations.Test
		public void response() {
			bodyRan = true;
		}
	}

	public static final class ResponsePassFixture {
		static boolean bodyRan;

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json")
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return unauthorizedExecutor();
		}

		@JPostman.Response(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void response() {
			bodyRan = true;
		}
	}

	public static final class ContextSkipFixture {
		static boolean bodyRan;

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 1)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return unauthorizedExecutor();
		}

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void response() {
			bodyRan = true;
		}
	}

	public static final class CallSkipFixture {
		static boolean afterCall;

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json")
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return unauthorizedExecutor();
		}

		@JPostman.Call(request = "Get current auth user", verify = 1)
		@org.testng.annotations.Test
		public void call() {
			runtime.call();
			afterCall = true;
		}
	}

	public static final class RunnerSkipDebugFixture {
		@JPostman.Context(config = "", collection = "classpath:annotation-test-runner-per-request-collection.json", debug = {
				"info", "request" })
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return unauthorizedExecutor();
		}

		@JPostman.Runner(folder = "Product", verify = 1)
		@org.testng.annotations.Test
		public void runner() {
		}
	}

	public static final class FailingBodyFixture {
		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json")
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			return unauthorizedExecutor();
		}

		@JPostman.Response(request = "Get current auth user", verify = 1)
		@org.testng.annotations.Test
		public void response() {
			throw new AssertionError("body failure");
		}
	}
}
