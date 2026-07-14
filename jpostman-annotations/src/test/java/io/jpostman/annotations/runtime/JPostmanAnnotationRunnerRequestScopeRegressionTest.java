package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
				"The blank-request dependency should run once for the selected runner request.");
		assertEquals("product", fixture.scopeNamespace);
		assertEquals("level1/level2/level3", fixture.scopeFolder);
		assertEquals("Nested request", fixture.scopeRequest);
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
	public void defaultLifecycleRunsBlankRequestDependencyBeforeEachFolderRequestAndBody() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runProductFolder");
		JPostmanAnnotationEngine.runTestNg(fixture, method, fixture::recordRunnerBody);

		assertEquals(
				List.of("helper:Folder request one", "executor:Folder request one", "body:Folder request one",
						"helper:Folder request two", "executor:Folder request two", "body:Folder request two"),
				fixture.events);
	}

	@Test
	public void defaultLifecycleUsesRootRequestsWhenDependencyFolderIsBlank() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runRoot");
		JPostmanAnnotationEngine.runTestNg(fixture, method, fixture::recordRunnerBody);

		assertEquals(
				List.of("root-helper:Root request one", "executor:Root request one", "body:Root request one",
						"root-helper:Root request two", "executor:Root request two", "body:Root request two"),
				fixture.events);
	}

	@Test
	public void lifecycleModeKeepsBlankRequestDependencyAsOneTimeSetup() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runProductFolderLifecycle");
		JPostmanAnnotationEngine.runTestNg(fixture, method, fixture::recordRunnerBody);

		assertEquals(1, fixture.lifecycleHelperCalls);
		assertEquals("", fixture.lifecycleHelperRequest);
		assertEquals(List.of("lifecycle-helper:<none>", "executor:Folder request one", "body:Folder request one",
				"executor:Folder request two", "body:Folder request two"), fixture.events);
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
	private static final class PerRequestRunnerFixture {

		@JPostman.Context(config = "classpath:annotation-test-runner-per-request.properties", verifyStatusCode = 0)
		private JPostman.Runtime<TestNgContext> jpostman;

		private final List<String> events = new ArrayList<>();
		private int lifecycleHelperCalls;
		private String lifecycleHelperRequest;

		@JPostman.Request(id = "productScope", namespace = "product", folder = "Product")
		public void customizeProductRequest(TestNgContext ctx, JPostman.Info compactInfo) {
			assertNotNull(ctx.request(), "The selected folder request must be injected before the helper runs.");
			events.add("helper:" + compactInfo.attr().request);
		}

		@JPostman.Request(id = "rootScope", namespace = "product")
		public void customizeRootRequest(TestNgContext ctx, JPostman.Info compactInfo) {
			assertNotNull(ctx.request(), "The selected root request must be injected before the helper runs.");
			events.add("root-helper:" + compactInfo.attr().request);
		}

		@JPostman.Request(id = "lifecycleScope", namespace = "product", folder = "Product")
		public void lifecycleSetup(TestNgContext ctx, JPostman.Info compactInfo) {
			lifecycleHelperCalls++;
			lifecycleHelperRequest = compactInfo.attr().request;
			assertNull(ctx.request(), "Lifecycle mode keeps the existing one-time setup dependency behavior.");
			events.add("lifecycle-helper:<none>");
		}

		@JPostman.Runner(dependsOn = "#productScope", verify = 0)
		@org.testng.annotations.Test
		public void runProductFolder() {
		}

		@JPostman.Runner(dependsOn = "#rootScope", verify = 0)
		@org.testng.annotations.Test
		public void runRoot() {
		}

		@JPostman.Runner(dependsOn = "#lifecycleScope", lifecycle = true, verify = 0)
		@org.testng.annotations.Test
		public void runProductFolderLifecycle() {
		}

		private void recordRunnerBody() {
			assertNotNull(jpostman.ctx().request());
			events.add("body:" + jpostman.info().attr().request);
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String request = info.request;
			return () -> {
				events.add("executor:" + request);
				return new ApiResponse(200, "{\"id\":1}", "{\"id\":1}".getBytes(), Map.of());
			};
		}
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
