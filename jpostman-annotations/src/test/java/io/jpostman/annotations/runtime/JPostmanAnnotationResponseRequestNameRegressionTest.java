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
	public void explicitResponseRequestInheritsScopeFromBlankRequestDependency() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "responseWithExplicitRequestAndInheritedScope");

		assertEquals(1, fixture.blankScopedHelperCalls);
		assertTrue(fixture.blankScopedHelperSawRequest,
				"The blank request helper must receive the response request before its body runs.");
		assertEquals(1, fixture.executorCalls);
		assertEquals("Nested request", fixture.executedRequest);
		assertEquals("product", fixture.executedNamespace);
		assertEquals("level1/level2/level3", fixture.executedFolder);
	}

	@Test
	public void explicitResponseRequestInheritsScopeFromMethodNameDependency() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "responseWithExplicitRequestAndMethodDependency");

		assertEquals(1, fixture.blankScopedHelperCalls);
		assertTrue(fixture.blankScopedHelperSawRequest,
				"A method-name dependency must receive the prepared response request before its body runs.");
		assertEquals(1, fixture.executorCalls);
		assertEquals("Nested request", fixture.executedRequest);
		assertEquals("product", fixture.executedNamespace);
		assertEquals("level1/level2/level3", fixture.executedFolder);
	}

	@Test
	public void explicitCallRequestInheritsScopeFromBlankRequestDependency() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "callWithExplicitRequestAndInheritedScope");
		fixture.jpostman.call();

		assertEquals(1, fixture.blankScopedHelperCalls);
		assertTrue(fixture.blankScopedHelperSawRequest,
				"The blank request helper must receive the call request before its body runs.");
		assertEquals(1, fixture.executorCalls);
		assertEquals("Nested request", fixture.executedRequest);
		assertEquals("product", fixture.executedNamespace);
		assertEquals("level1/level2/level3", fixture.executedFolder);
	}

	@Test
	public void callRejectsBlankRequestScopeDependencyWithClearMessage() throws Exception {
		ResponseRequestNameFixture fixture = new ResponseRequestNameFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "callWithoutRequest");
		AssertionError error = assertThrows(AssertionError.class, () -> fixture.jpostman.call());

		assertTrue(error.getMessage().contains("@JPostman.Call request name is missing."));
		assertTrue(error.getMessage().contains(
				"Set request = \"...\" on @JPostman.Call, or depend on @JPostman.Request that defines request = \"...\"."));
		assertEquals(0, fixture.blankScopedHelperCalls);
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
		assertTrue(message.contains("@JPostman.Response request name is missing."));
		assertTrue(message.contains(
				"Set request = \"...\" on @JPostman.Response, or depend on @JPostman.Request that defines request = \"...\"."));
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class ResponseRequestNameFixture {

		@JPostman.Context(config = "classpath:annotation-test-response-request-scope.properties", verifyStatusCode = 0)
		private JPostman.Runtime<TestNgContext> jpostman;

		private int executorCalls;
		private String executedRequest;
		private String executedNamespace;
		private String executedFolder;
		private int blankScopedHelperCalls;
		private boolean blankScopedHelperSawRequest;

		@JPostman.Request(id = "blankRequest", folder = { "level1", "level2", "level3" })
		public void blankRequestScope(JPostman.Info info) {
			// A blank-request dependency may supply scope, but not an executable request
			// name.
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

		@JPostman.Request(id = "blankScopedRequest", namespace = "product", folder = { "level1", "level2", "level3" })
		public void blankScopedRequest(TestNgContext ctx) {
			blankScopedHelperCalls++;
			blankScopedHelperSawRequest = ctx.request() != null;
		}

		@JPostman.Response(dependsOn = "#blankScopedRequest", request = "Nested request", verify = 0)
		public void responseWithExplicitRequestAndInheritedScope() {
		}

		@JPostman.Request(namespace = "product", folder = { "level1", "level2", "level3" })
		public void blankScopedRequestByMethod(TestNgContext ctx) {
			blankScopedHelperCalls++;
			blankScopedHelperSawRequest = ctx.request() != null;
		}

		@JPostman.Response(dependsOn = "blankScopedRequestByMethod", request = "Nested request", verify = 0)
		public void responseWithExplicitRequestAndMethodDependency() {
		}

		@JPostman.Call(dependsOn = "#blankScopedRequest", request = "Nested request", log = "none")
		public void callWithExplicitRequestAndInheritedScope() {
		}

		@JPostman.Call(dependsOn = "#blankScopedRequest", log = "none")
		public void callWithoutRequest() {
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
			executedNamespace = info.namespace;
			executedFolder = info.folder;
			return () -> new ApiResponse(200, "{\"id\":1}", "{\"id\":1}".getBytes(), Map.of());
		}
	}
}
