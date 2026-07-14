package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanRunner;
import io.jpostman.testng.JPostmanTestNG;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for @JPostmanRunner request info isolation.
 */
public class JPostmanAnnotationRunnerInfoIsolationRegressionTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";

	@Test
	public void runnerCreatesIndependentInfoForEachCollectionRequest() throws Exception {
		RunnerInfoIsolationFixture fixture = new RunnerInfoIsolationFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "runCollection");

		assertEquals(2, fixture.sharedSetupCalls);
		assertEquals(List.of("Login user and get tokens", "Get current auth user"), fixture.executorRequests);
		assertEquals(2, fixture.executorMethods.size());

		List<String> loginMethods = fixture.executorMethods.get(0);
		List<String> userMethods = fixture.executorMethods.get(1);

		assertEquals(List.of("runCollection", "shareRunnerHeader", "defaultExecutor(\"Login user and get tokens\")"),
				loginMethods);
		assertEquals(List.of("runCollection", "shareRunnerHeader", "defaultExecutor(\"Get current auth user\")"),
				userMethods);
		assertFalse(userMethods.contains("defaultExecutor(\"Login user and get tokens\")"),
				"The second runner request must not inherit the first request's executor step: " + userMethods);
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostmanTestNG
	private static final class RunnerInfoIsolationFixture {

		@JPostmanContext(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostmanRuntime<TestNgContext> jpostman;

		private int sharedSetupCalls;
		private final List<String> executorRequests = new ArrayList<>();
		private final List<List<String>> executorMethods = new ArrayList<>();

		@JPostmanRequest
		public void shareRunnerHeader(TestNgContext ctx, JPostmanInfo info) {
			sharedSetupCalls++;
			info.add().headers("X-Shared", "yes");
		}

		@JPostmanRunner(dependsOn = "shareRunnerHeader", include = { "Login user and get tokens",
				"Get current auth user" }, verify = 200)
		@org.testng.annotations.Test
		public void runCollection() {
			// The assertion is in the outer JUnit test after the annotation runner
			// finishes.
		}

		@JPostmanExecutor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			assertEquals("yes", info.headersAdd.get("X-Shared"));
			assertFalse(info.headersAdd.containsKey("X-Request-Marker"),
					"A previous runner request mutated this info map: " + info.headersAdd);

			executorRequests.add(info.request);
			executorMethods.add(new ArrayList<>(info.methods));
			info.add().headers("X-Request-Marker", info.request);

			return okExecutor("{\"request\":\"" + info.request + "\"}");
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
