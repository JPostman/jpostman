package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for runners that reuse namespace and folder information
 * from blank-request and named-request {@code @JPostman.Request} dependencies.
 */
public class JPostmanAnnotationRunnerRequestScopeRegressionTest {

	@Test
	public void runnerExecutesAllRequestsFromRequestDependencyFolder() throws Exception {
		RunnerRequestScopeFixture fixture = new RunnerRequestScopeFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = RunnerRequestScopeFixture.class.getDeclaredMethod("runNestedFolder");
		JPostmanAnnotationEngine.runTestNg(fixture, method);

		assertEquals(1, fixture.scopeHelperCalls,
				"The blank-request dependency should run once as the runner scope helper.");
		assertEquals("product", fixture.scopeNamespace);
		assertEquals("level1/level2/level3", fixture.scopeFolder);
		assertEquals("", fixture.scopeRequest);
		assertEquals(1, fixture.executorCalls,
				"The runner should execute every request directly contained in the inherited folder.");
		assertEquals(List.of("Nested request"), fixture.executedRequests);
		assertEquals(List.of("product"), fixture.executedNamespaces);
		assertEquals(List.of("level1/level2/level3"), fixture.executedFolders);
	}

	@Test
	public void namedRequestDependencyAlsoProvidesRunnerScope() throws Exception {
		NamedRunnerRequestScopeFixture fixture = new NamedRunnerRequestScopeFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = NamedRunnerRequestScopeFixture.class.getDeclaredMethod("runNamedRequestFolder");
		JPostmanAnnotationEngine.runTestNg(fixture, method);

		assertEquals(1, fixture.namedScopeHelperCalls,
				"The named request dependency should still run once as a request helper.");
		assertEquals("product", fixture.namedScopeNamespace);
		assertEquals("level1/level2/level3", fixture.namedScopeFolder);
		assertEquals("Nested request", fixture.namedScopeRequest);
		assertEquals(1, fixture.executorCalls,
				"The request dependency itself must not suppress the matching runner request.");
		assertEquals(List.of("Nested request"), fixture.executedRequests);
		assertEquals(List.of("product"), fixture.executedNamespaces);
		assertEquals(List.of("level1/level2/level3"), fixture.executedFolders);
	}

	@Test
	public void missingInheritedFolderFailsInsteadOfSkipping() throws Exception {
		RunnerRequestScopeFixture fixture = new RunnerRequestScopeFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = RunnerRequestScopeFixture.class.getDeclaredMethod("runMissingFolder");
		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method));

		assertTrue(error.getMessage().contains("JPostman runner folder was not found."));
		assertTrue(error.getMessage().contains("Folder not found: level1/missing"));
		assertTrue(error.getMessage().contains("namespace=product, folder=level1/missing"));
	}

	@JPostman.TestNG
	private static final class RunnerRequestScopeFixture {

		@JPostman.Context(config = "classpath:annotation-test-runner-scope.properties", verifyStatusCode = 0)
		private JPostman.Runtime<TestNgContext> jpostman;

		@JPostman.TestContext(namespace = "product")
		private JPostman.Test product;

		private int scopeHelperCalls;
		private String scopeNamespace;
		private String scopeFolder;
		private String scopeRequest;
		private int executorCalls;
		private final List<String> executedRequests = new ArrayList<>();
		private final List<String> executedNamespaces = new ArrayList<>();
		private final List<String> executedFolders = new ArrayList<>();

		@JPostman.Request(id = "request1", namespace = "product", folder = { "level1", "level2", "level3" })
		public void nestedFolderScope(JPostman.Info compactInfo) {
			scopeHelperCalls++;
			JPostmanInfo info = compactInfo.attr();
			scopeNamespace = info.namespace;
			scopeFolder = info.folder;
			scopeRequest = info.request;
		}

		@JPostman.Runner(tags = "test", dependsOn = "#request1", verify = 0)
		@org.testng.annotations.Test
		public void runNestedFolder() {
			// Direct engine execution does not invoke the TestNG callback body.
		}

		@JPostman.Request(id = "missingRequest", namespace = "product", folder = { "level1", "missing" })
		public void missingFolderScope(JPostman.Info info) {
			// Scope-only request dependency.
		}

		@JPostman.Runner(dependsOn = "#missingRequest", verify = 0)
		@org.testng.annotations.Test
		public void runMissingFolder() {
			// The runner must fail before its body is invoked.
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			executorCalls++;
			executedRequests.add(info.request);
			executedNamespaces.add(info.namespace);
			executedFolders.add(info.folder);
			return () -> new ApiResponse(200, "{\"id\":1}", "{\"id\":1}".getBytes(), Map.of());
		}
	}

	@JPostman.TestNG
	private static final class NamedRunnerRequestScopeFixture {

		@JPostman.Context(config = "classpath:annotation-test-runner-scope.properties", verifyStatusCode = 0)
		private JPostman.Runtime<TestNgContext> jpostman;

		@JPostman.TestContext(namespace = "product")
		private JPostman.Test product;

		private int namedScopeHelperCalls;
		private String namedScopeNamespace;
		private String namedScopeFolder;
		private String namedScopeRequest;
		private int executorCalls;
		private final List<String> executedRequests = new ArrayList<>();
		private final List<String> executedNamespaces = new ArrayList<>();
		private final List<String> executedFolders = new ArrayList<>();

		@JPostman.Request(id = "namedRequest", namespace = "product", folder = { "level1", "level2",
				"level3" }, request = "Nested request")
		public void namedRequestScope(JPostman.Info compactInfo) {
			namedScopeHelperCalls++;
			JPostmanInfo info = compactInfo.attr();
			namedScopeNamespace = info.namespace;
			namedScopeFolder = info.folder;
			namedScopeRequest = info.request;
		}

		@JPostman.Runner(tags = "test", dependsOn = "#namedRequest", include = "Nested request", verify = 0)
		@org.testng.annotations.Test
		public void runNamedRequestFolder() {
			// The named request provides scope; include selects the runner request.
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			executorCalls++;
			executedRequests.add(info.request);
			executedNamespaces.add(info.namespace);
			executedFolders.add(info.folder);
			return () -> new ApiResponse(200, "{\"id\":1}", "{\"id\":1}".getBytes(), Map.of());
		}
	}
}
