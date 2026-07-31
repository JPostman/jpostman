package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
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
import io.jpostman.annotations.testng.JPostmanTestNgAnnotationListener;
import io.jpostman.junit.JUnitContext;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for verification values scoped to active runners only.
 */
public class JPostmanRunnerVerificationInheritanceRegressionTest {

	@Test
	void junitStandaloneResponseDoesNotInheritMatchingRunnerVerifyZero() throws Exception {
		JUnitRunnerVerifyZeroFixture fixture = new JUnitRunnerVerifyZeroFixture();
		Method runner = JUnitRunnerVerifyZeroFixture.class.getDeclaredMethod("allRootRequests");
		Method defaultResponse = JUnitRunnerVerifyZeroFixture.class.getDeclaredMethod("login");
		Method disabledResponse = JUnitRunnerVerifyZeroFixture.class.getDeclaredMethod("currentUser");

		assertDoesNotThrow(() -> JPostmanAnnotationEngine.runJUnit(fixture, runner),
				"verify = 0 must disable verification for requests executed by the runner.");
		assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.runJUnit(fixture, defaultResponse),
				"A standalone response with verify = -1 must use Context.verifyStatusCode = 200.");
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.runJUnit(fixture, disabledResponse),
				"A standalone response may disable its own verification explicitly.");
	}

	@Test
	void testNgStandaloneResponseDoesNotInheritMatchingRunnerVerifyZero() {
		TestListenerAdapter capture = new TestListenerAdapter();
		TestNG testNG = new TestNG();
		testNG.setUseDefaultListeners(false);
		testNG.setVerbose(0);
		testNG.setTestClasses(new Class<?>[] { RunnerVerifyZeroFixture.class });
		testNG.addListener(new JPostmanTestNgAnnotationListener());
		testNG.addListener(capture);
		testNG.run();

		List<ITestResult> passed = new ArrayList<>();
		List<ITestResult> failed = new ArrayList<>();
		capture.getTestContexts().forEach(context -> {
			passed.addAll(context.getPassedTests().getAllResults());
			failed.addAll(context.getFailedTests().getAllResults());
		});

		assertEquals(2, passed.size(), "The runner and explicitly disabled response should pass.");
		assertEquals(1, failed.size(),
				"The standalone default response should fail on 201 because Context expects 200.");
	}

	@JPostman.JUnit
	public static final class JUnitRunnerVerifyZeroFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JUnitContext> runtime;

		@JPostman.Runner(verify = 0)
		public void allRootRequests() {
		}

		@JPostman.Response(request = "Login user and get tokens")
		public void login() {
		}

		@JPostman.Response(request = "Get current auth user", verify = 0)
		public void currentUser() {
		}

		@JPostman.Executor
		public ApiExecutor executor(JUnitContext ctx, JPostman.Info info) {
			String json = "{\"ok\":true}";
			return () -> new ApiResponse(201, json, json.getBytes(StandardCharsets.UTF_8), Map.of());
		}
	}

	@JPostman.TestNG
	public static final class RunnerVerifyZeroFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<TestNgContext> runtime;

		@JPostman.Runner(verify = 0)
		@org.testng.annotations.Test
		public void allRootRequests() {
		}

		@JPostman.Response(request = "Login user and get tokens")
		@org.testng.annotations.Test
		public void login() {
		}

		@JPostman.Response(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void currentUser() {
		}

		@JPostman.Executor
		public ApiExecutor executor(TestNgContext ctx, JPostman.Info info) {
			String json = "{\"ok\":true}";
			return () -> new ApiResponse(201, json, json.getBytes(StandardCharsets.UTF_8), Map.of());
		}
	}
}
