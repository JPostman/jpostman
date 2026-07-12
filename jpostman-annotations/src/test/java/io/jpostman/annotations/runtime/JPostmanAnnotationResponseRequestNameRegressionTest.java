package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/** Regression coverage for response request-name validation. */
public class JPostmanAnnotationResponseRequestNameRegressionTest {

	@Test
	public void responseRejectsBlankRequestScopeDependency() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		AssertionError error = assertThrows(AssertionError.class,
				() -> runTestNg(fixture, "responseDependingOnBlankRequest"));

		assertMissingRequestMessage(error);
		assertEquals(0, fixture.executorCalls);
	}

	@Test
	public void runnerRejectsBlankResponseDependencyWithClearMessage() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		AssertionError error = assertThrows(AssertionError.class,
				() -> runTestNg(fixture, "runnerDependingOnBlankResponse"));

		assertMissingRequestMessage(error);
		assertEquals(0, fixture.executorCalls);
	}

	@Test
	public void responseInheritsRequestNameFromNamedRequestDependency() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "responseDependingOnNamedRequest");

		assertEquals(1, fixture.executorCalls);
		assertEquals("Nested request", fixture.executedRequest);
		assertEquals("level1/level2/level3", fixture.executedFolder);
	}

	private static void assertMissingRequestMessage(AssertionError error) {
		String message = error.getMessage();
		assertTrue(message.contains("JPostman response request name is missing."));
		assertTrue(message.contains("Set request = \"...\" on @JPostmanResponse"));
		assertTrue(message.contains("depend on @JPostmanRequest that defines request = \"...\""));
		assertTrue(message.contains("blank-request @JPostmanRequest"));
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class ResponseRequestNameFixture {

		@JPostman.Context(collection = "classpath:annotation-test-nested-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<TestNgContext> jpostman;

		private int executorCalls;
		private String executedRequest;
		private String executedFolder;

		@JPostman.Request(id = "blankRequest", folder = { "level1", "level2", "level3" })
		public void blankRequestScope(JPostman.Info info) {
			// A blank-request dependency is a valid folder scope for runners only.
		}

		@JPostman.Response(dependsOn = "#blankRequest", verify = 0)
		public void responseDependingOnBlankRequest() {
		}

		@JPostman.Response(id = "blankResponse", folder = { "level1", "level2", "level3" }, verify = 0)
		public void blankResponseDependency() {
		}

		@JPostman.Runner(dependsOn = "#blankResponse", verify = 0)
		public void runnerDependingOnBlankResponse() {
		}

		@JPostman.Request(id = "namedRequest", folder = { "level1", "level2", "level3" }, request = "Nested request")
		public void namedRequest() {
		}

		@JPostman.Response(dependsOn = "#namedRequest", verify = 0)
		public void responseDependingOnNamedRequest() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			executorCalls++;
			executedRequest = info.request;
			executedFolder = info.folder;
			return () -> new ApiResponse(200, "{\"id\":1}", "{\"id\":1}".getBytes(), Map.of());
		}
	}
}
