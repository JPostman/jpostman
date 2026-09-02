package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
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
	public void defaultLifecycleRunsBlankRequestDependencyPerRequestAndBodyOnceAtEnd() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runProductFolder");
		JPostmanAnnotationEngine.runTestNg(fixture, method, fixture::recordRunnerBody);

		assertEquals(List.of("helper:Folder request one", "executor:Folder request one", "helper:Folder request two",
				"executor:Folder request two", "helper:Folder request three", "executor:Folder request three",
				"body:Folder request three"), fixture.events);
		assertEquals("https://example.com/products/42?token=runner-token", fixture.executedUrls.get(0));
	}

	@Test
	public void wrappedValuesSurviveNestedRunnerBeforeBlankRequestHelperBody() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();
		List<String> output = new ArrayList<>();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runProductFolderAfterSetupRunner");
		try (JPostmanOutputs.Scope ignored = JPostmanOutputs.use(output::add)) {
			JPostmanAnnotationEngine.runTestNg(fixture, method);
		}

		assertEquals("https://example.com/product-two", fixture.executedUrls.get(0));
		assertEquals("https://example.com/products/42?token=runner-token", fixture.executedUrls.get(1));
		assertEquals("https://example.com/products/{{productId}}?token={{token}}", fixture.helperSourceUrl);
		assertTrue(fixture.helperResolvedLog.contains("/products/42?token=runner-token"), fixture.helperResolvedLog);
		assertTrue(output.stream().anyMatch(value -> value.contains("/products/42?token=runner-token")),
				String.join("\n", output));
	}

	@Test
	public void defaultLifecycleUsesRootRequestsAndInvokesBodyOnceAtEnd() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runRoot");
		JPostmanAnnotationEngine.runTestNg(fixture, method, fixture::recordRunnerBody);

		assertEquals(List.of("root-helper:Root request one", "executor:Root request one",
				"root-helper:Root request two", "executor:Root request two", "body:Root request two"), fixture.events);
	}

	@Test
	public void lifecycleModeKeepsBlankRequestDependencyPerRequest() throws Exception {
		PerRequestRunnerFixture fixture = new PerRequestRunnerFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = PerRequestRunnerFixture.class.getDeclaredMethod("runProductFolderLifecycle");
		JPostmanAnnotationEngine.runTestNg(fixture, method, fixture::recordRunnerBody);

		assertEquals(3, fixture.lifecycleHelperCalls);
		assertEquals("Folder request three", fixture.lifecycleHelperRequest);
		assertEquals(List.of("lifecycle-helper:Folder request one", "executor:Folder request one",
				"body:Folder request one", "lifecycle-helper:Folder request two", "executor:Folder request two",
				"body:Folder request two", "lifecycle-helper:Folder request three", "executor:Folder request three",
				"body:Folder request three"), fixture.events);
	}

	@Test
	public void runnerExecutesVerifiedExternalResponseBeforeRemainingFolderRequests() throws Exception {
		VerifiedExternalResponseFixture fixture = new VerifiedExternalResponseFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = VerifiedExternalResponseFixture.class.getDeclaredMethod("testAuthRunner");
		JPostmanAnnotationEngine.runTestNg(fixture, method);

		assertEquals(List.of("request-helper:Login user and get tokens", "executor:Login user and get tokens",
				"response-body:Login user and get tokens", "executor:Get current auth user"), fixture.events);
		assertEquals(2, fixture.executorBodies.size());
		assertEquals(Map.of("username", "{{username}}"), fixture.executorBodies.get(0));
		assertTrue(fixture.executorBodies.get(1).isEmpty(),
				"Request values created inside the external Response dependency must not leak into the next Runner request.");
		assertEquals("token-123", fixture.runtime.test().cache("token"));
		JPostmanReport report = (JPostmanReport) fixture.report;
		assertEquals(2, report.total(), "The external Response and remaining Runner request must both be reported.");
		assertEquals(2, report.passed.size());
		assertNotNull(report.execution("loginUserAndGetAccessRefreshTokens"));
	}

	@Test
	public void externalResponseWithoutExplicitVerifyRemainsFilteredFromRunner() throws Exception {
		DefaultVerifyExternalResponseFixture fixture = new DefaultVerifyExternalResponseFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = DefaultVerifyExternalResponseFixture.class.getDeclaredMethod("testAuthRunner");
		JPostmanAnnotationEngine.runTestNg(fixture, method);

		assertEquals(0, fixture.responseBodyCalls);
		assertEquals(List.of("Get current auth user"), fixture.executedRequests);
	}

	@Test
	public void runnerContinuesAfterVerifiedRequestFailureForNonTerminatingReportModes() throws Exception {
		ContinueAfterFailureFixture fixture = new ContinueAfterFailureFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = ContinueAfterFailureFixture.class.getDeclaredMethod("testAuthRunner");
		assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.runTestNg(fixture, method));

		assertEquals(List.of("Login user and get tokens", "Get current auth user"), fixture.executedRequests,
				"A 401 on the first request must not prevent the next folder request from executing.");
		JPostmanReport report = (JPostmanReport) fixture.report;
		assertEquals(1, report.failed.size());
		assertEquals(1, report.passed.size());
	}

	@Test
	public void runnerSkipAllRecordsLaterFolderRequestsAsSkippedAfterFirstFailure() throws Exception {
		SkipAllAfterFailureFixture fixture = new SkipAllAfterFailureFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = SkipAllAfterFailureFixture.class.getDeclaredMethod("testAuthRunner");
		assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.runTestNg(fixture, method));

		assertEquals(List.of("Login user and get tokens"), fixture.executedRequests,
				"The request after the first failure must not execute when fail=skipAll.");
		JPostmanReport report = (JPostmanReport) fixture.report;
		assertTrue(report.skipRemaining());
		assertEquals(2, report.total(), "The failed request and the skipped remainder must both be reported.");
		assertEquals(1, report.failed.size());
		assertEquals(1, report.skipped.size());
		assertEquals("Login user and get tokens", report.failed.get(0).request);
		assertEquals("Get current auth user", report.skipped.get(0).request);
		assertNull(report.skipped.get(0).statusCode(), "A skipAll remainder must not execute an HTTP request.");
	}

	@Test
	public void externalResponsePassThenRunnerFailureSkipsEveryLaterRequest() throws Exception {
		ExternalResponseSkipAllFixture fixture = new ExternalResponseSkipAllFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		Method method = ExternalResponseSkipAllFixture.class.getDeclaredMethod("testProductRunner");
		assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.runTestNg(fixture, method));

		assertEquals(List.of("Folder request one", "Folder request two"), fixture.executedRequests,
				"The external Response must run first, then the runner must stop after its first failure.");
		JPostmanReport report = (JPostmanReport) fixture.report;
		assertEquals(3, report.total());
		assertEquals(1, report.passed.size());
		assertEquals(1, report.failed.size());
		assertEquals(1, report.skipped.size());
		assertEquals("prepareProductRequest", report.passed.get(0).method);
		assertEquals("Folder request two", report.failed.get(0).request);
		assertEquals("Folder request three", report.skipped.get(0).request);
		assertNull(report.skipped.get(0).statusCode());
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
		private final List<String> executedUrls = new ArrayList<>();
		private String helperSourceUrl;
		private String helperResolvedLog;
		private int lifecycleHelperCalls;
		private String lifecycleHelperRequest;

		@JPostman.Request(id = "productScope", namespace = "product", folder = "Product")
		public void customizeProductRequest(TestNgContext ctx, JPostman.Info compactInfo) {
			assertNotNull(ctx.request(), "The selected folder request must be injected before the helper runs.");
			compactInfo.path("{{productId}}", 42);
			compactInfo.query("{{token}}", "runner-token");
			events.add("helper:" + compactInfo.attr().request);
		}

		@JPostman.Runner(id = "setupRunner", namespace = "product", folder = "Product", include = "Folder request two", verify = 0)
		public void setupRunner() {
		}

		@JPostman.Request(id = "chainedProductScope", namespace = "product", folder = "Product", dependsOn = "#setupRunner")
		public void customizeProductRequestAfterRunner(JPostman.Test test, JPostman.Info compactInfo) {
			compactInfo.path("{{productId}}", 42);
			compactInfo.query("{{token}}", "runner-token");
			helperSourceUrl = compactInfo.attr().sourceRequest().toUrl();
			helperResolvedLog = test.log();
			test.print();
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
			assertNotNull(ctx.request(), "Lifecycle mode must not change per-request @Request dependency scope.");
			events.add("lifecycle-helper:" + compactInfo.attr().request);
		}

		@JPostman.Runner(dependsOn = "#productScope", verify = 0)
		@org.testng.annotations.Test
		public void runProductFolder() {
		}

		@JPostman.Runner(namespace = "product", folder = "Product", include = "Folder request one", dependsOn = "#chainedProductScope", verify = 0)
		@org.testng.annotations.Test
		public void runProductFolderAfterSetupRunner() {
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
				executedUrls.add(ctx.request().request().toUrl());
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

	@JPostman.TestNG
	private static final class VerifiedExternalResponseFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.ReportContext(details = true)
		private JPostman.Report report;

		private final List<String> events = new ArrayList<>();
		private final List<Map<String, Object>> executorBodies = new ArrayList<>();

		@JPostman.Response(id = "Ref1", cache = "token", dependsOn = "#Ref2", verify = 200)
		public String loginUserAndGetAccessRefreshTokens() {
			events.add("response-body:Login user and get tokens");
			return runtime.test().path("accessToken");
		}

		@JPostman.Request(id = "Ref2", request = "Login user and get tokens")
		public void loginUserAndGetAccessRefreshTokensRequest(JPostman.Test test, JPostman.Info info) {
			events.add("request-helper:" + info.attr().request);
			info.body("username", "{{username}}");
		}

		@JPostman.Runner(verify = 200)
		@org.testng.annotations.Test
		public void testAuthRunner() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String request = info.request;
			events.add("executor:" + request);
			executorBodies.add(new LinkedHashMap<>(info.body));
			return () -> {
				String json = "Login user and get tokens".equals(request) ? "{\"accessToken\":\"token-123\"}"
						: "{\"ok\":true}";
				return new ApiResponse(200, json, json.getBytes(), Map.of());
			};
		}
	}

	@JPostman.TestNG
	private static final class DefaultVerifyExternalResponseFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		private int responseBodyCalls;
		private final List<String> executedRequests = new ArrayList<>();

		@JPostman.Response(id = "Ref1", request = "Login user and get tokens", cache = "token")
		public String loginUserAndGetAccessRefreshTokens() {
			responseBodyCalls++;
			return runtime.test().path("accessToken");
		}

		@JPostman.Runner(verify = 200)
		@org.testng.annotations.Test
		public void testAuthRunner() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			executedRequests.add(info.request);
			String json = "{\"ok\":true}";
			return () -> new ApiResponse(200, json, json.getBytes(), Map.of());
		}
	}

	@JPostman.TestNG
	private static final class ExternalResponseSkipAllFixture {

		@JPostman.Context(config = "classpath:annotation-test-runner-per-request.properties", verifyStatusCode = 200)
		private JPostman.Runtime<TestNgContext> runtime;

		@JPostman.ReportContext(details = true, fail = "skipAll")
		private JPostman.Report report;

		private final List<String> executedRequests = new ArrayList<>();

		@JPostman.Response(id = "Ref1", namespace = "product", folder = "Product", request = "Folder request one", cache = "token", verify = 200)
		public String prepareProductRequest() {
			return "token-123";
		}

		@JPostman.Runner(namespace = "product", folder = "Product", verify = 200)
		@org.testng.annotations.Test
		public void testProductRunner() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String request = info.request;
			executedRequests.add(request);
			int status = "Folder request two".equals(request) ? 401 : 200;
			String json = "{\"status\":" + status + "}";
			return () -> new ApiResponse(status, json, json.getBytes(), Map.of());
		}
	}

	@JPostman.TestNG
	private static final class ContinueAfterFailureFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.ReportContext(details = true, fail = "response")
		private JPostman.Report report;

		private final List<String> executedRequests = new ArrayList<>();

		@JPostman.Runner(verify = 200)
		@org.testng.annotations.Test
		public void testAuthRunner() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String request = info.request;
			executedRequests.add(request);
			int status = "Login user and get tokens".equals(request) ? 401 : 200;
			String json = "{\"status\":" + status + "}";
			return () -> new ApiResponse(status, json, json.getBytes(), Map.of());
		}
	}

	@JPostman.TestNG
	private static final class SkipAllAfterFailureFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.ReportContext(details = true, fail = "skipAll")
		private JPostman.Report report;

		private final List<String> executedRequests = new ArrayList<>();

		@JPostman.Runner(verify = 200)
		@org.testng.annotations.Test
		public void testAuthRunner() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String request = info.request;
			executedRequests.add(request);
			int status = "Login user and get tokens".equals(request) ? 401 : 200;
			String json = "{\"status\":" + status + "}";
			return () -> new ApiResponse(status, json, json.getBytes(), Map.of());
		}
	}

}
