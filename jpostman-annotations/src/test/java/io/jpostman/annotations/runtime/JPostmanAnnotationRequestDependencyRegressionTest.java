package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for request-helper dependencies that inherit a parent
 * request location after running nested response dependencies.
 */
public class JPostmanAnnotationRequestDependencyRegressionTest {

	/**
	 * Verifies that a blank @JPostman.Request helper used by a product request
	 * keeps the parent product request context after its nested login/token
	 * dependency finishes. The helper should see the inherited product request, not
	 * the previous login request left behind by getToken().
	 */
	@Test
	public void requestDependencyRestoresInheritedRequestAfterNestedResponseDependency() throws Exception {
		InheritedRequestDependencyFixture fixture = new InheritedRequestDependencyFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "newMouseProduct");

		assertEquals(1, fixture.tokenMethodCalls, "The cached login response dependency should execute once.");
		assertEquals(1, fixture.productRequestHelperCalls, "The product request helper should execute once.");
		assertEquals(1, fixture.loginExecutorCalls, "The login executor should execute only for getToken().");
		assertEquals(1, fixture.productExecutorCalls,
				"The product executor should execute for the top-level product response.");
		assertNotNull(fixture.product.response(), "Product namespace should keep the product response.");
		assertEquals("product", fixture.product.path("source"));
	}

	/**
	 * Verifies the complete runner dependency/report lifecycle. An explicit runner
	 * verify value of zero must disable status verification for every runner
	 * response, including after a request helper executes a cached response
	 * dependency. The response dependency is a real executor-backed request and is
	 * therefore reported once; cache hits are not reported again.
	 */
	@Test
	public void runnerVerifyZeroSkipsAllStatusChecksAndReportsResponseDependency() throws Exception {
		RunnerDependencyReportFixture fixture = new RunnerDependencyReportFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "products");

		JPostmanReport result = (JPostmanReport) fixture.report;
		assertEquals(1, fixture.loginMethodCalls,
				"The cached response dependency should execute and be reported only once.");
		assertEquals(3, fixture.productRequestHelperCalls,
				"The request helper should run once for each product runner request.");
		assertEquals(1, fixture.loginExecutorCalls);
		assertEquals(3, fixture.productExecutorCalls);

		assertEquals(4, result.total(), "One login dependency plus three runner requests should be reported.");
		assertEquals(4, result.passed.size());
		assertEquals(0, result.failed.size());
		assertEquals(0, result.skipped.size());
		assertEquals(1, result.all().stream().filter(info -> "getLogin".equals(info.method)).count(),
				"The executor-backed response dependency should be a report result.");
		assertEquals(3, result.all().stream().filter(
				info -> "@JPostmanRunner".equals(info.annotation) && info.request != null && !info.request.isBlank())
				.count());
		assertTrue(
				result.all().stream()
						.noneMatch(info -> "@JPostmanRunner".equals(info.annotation)
								&& (info.request == null || info.request.isBlank())),
				"A concrete runner execution must not create a blank zero-duration parent report record.");
		assertTrue(result.passed.stream().anyMatch(info -> Integer.valueOf(201).equals(info.statusCode())),
				"verify = 0 must allow the 201 runner response even when the context default is 200.");
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class InheritedRequestDependencyFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<TestNgContext> jpostman;

		@JPostman.TestContext(namespace = "product")
		private JPostman.Test product;

		private int tokenMethodCalls;
		private int productRequestHelperCalls;
		private int loginExecutorCalls;
		private int productExecutorCalls;

		@JPostman.Response(request = "Login user and get tokens", cache = "token")
		public String getToken(TestNgContext ctx, JPostman.Info info) {
			tokenMethodCalls++;
			assertNotNull(ctx.response(), "getToken should see the executed login response.");
			return ctx.path("accessToken");
		}

		@JPostman.Request(dependsOn = "getToken")
		public void productRequest(TestNgContext ctx, JPostman.Info compactInfo) {
			productRequestHelperCalls++;
			JPostmanInfo info = (JPostmanInfo) compactInfo;

			assertEquals("product", info.namespace);
			assertEquals("", info.folder);
			assertEquals("Get current auth user", info.request);
			assertEquals("token-123", ctx.cache("token"));

			String request = ctx.request().log();
			assertTrue(request.contains("Get current auth user"),
					"Request helper should receive the inherited product request, not the login request. Actual request: "
							+ request);
			compactInfo.sauth("oauth2", ctx.cache("token"));
		}

		@JPostman.Request(namespace = "product", request = "Get current auth user", dependsOn = "productRequest")
		public void newProductData(TestNgContext ctx, JPostman.Info info) {
		}

		@JPostman.Response(tags = "mouse", dependsOn = "newProductData", verify = 200)
		@org.testng.annotations.Test
		public void newMouseProduct() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestName = ctx.request().log();
			if (requestName.contains("Login user and get tokens")) {
				loginExecutorCalls++;
				return okExecutor(200, "{\"source\":\"login\",\"accessToken\":\"token-123\"}");
			}
			if ("product".equals(info.namespace) && requestName.contains("Get current auth user")) {
				productExecutorCalls++;
				return okExecutor(200, "{\"source\":\"product\",\"id\":1}");
			}
			throw new AssertionError("Unexpected request: namespace=" + info.namespace + ", request=" + requestName);
		}
	}

	@JPostman.TestNG
	private static final class RunnerDependencyReportFixture {

		@JPostman.Context(config = "classpath:annotation-test-runner-per-request.properties", verifyStatusCode = 200)
		private JPostman.Runtime<TestNgContext> jpostman;

		@JPostman.ReportContext(details = true)
		private JPostman.Report report;

		private int loginMethodCalls;
		private int productRequestHelperCalls;
		private int loginExecutorCalls;
		private int productExecutorCalls;

		@JPostman.Response(request = "Root request one", cache = "token")
		public String getLogin(TestNgContext ctx) {
			loginMethodCalls++;
			return ctx.path("accessToken");
		}

		@JPostman.Request(dependsOn = "getLogin")
		public void productRequest(TestNgContext ctx, JPostman.Info info) {
			productRequestHelperCalls++;
			assertEquals("token-123", ctx.cache("token"));
			info.sauth("oauth2", ctx.cache("token"));
		}

		@JPostman.Runner(namespace = "product", folder = "Product", verify = 0, dependsOn = "productRequest")
		@org.testng.annotations.Test
		public void products() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestName = ctx.request().log();
			if (!"product".equals(info.namespace) && requestName.contains("Root request one")) {
				loginExecutorCalls++;
				return okExecutor(200, "{\"accessToken\":\"token-123\"}");
			}
			if ("product".equals(info.namespace) && requestName.contains("Folder request one")) {
				productExecutorCalls++;
				return okExecutor(200, "{\"source\":\"product-one\"}");
			}
			if ("product".equals(info.namespace) && requestName.contains("Folder request two")) {
				productExecutorCalls++;
				return okExecutor(201, "{\"source\":\"product-two\"}");
			}
			if ("product".equals(info.namespace) && requestName.contains("Folder request three")) {
				productExecutorCalls++;
				return okExecutor(200, "{\"source\":\"product-three\"}");
			}
			throw new AssertionError("Unexpected request: namespace=" + info.namespace + ", request=" + requestName);
		}
	}

	private static ApiExecutor okExecutor(int statusCode, String json) {
		return () -> okResponse(statusCode, json);
	}

	private static ApiResponse okResponse(int statusCode, String json) {
		return new ApiResponse(statusCode, json, json.getBytes(), Map.of());
	}
}
