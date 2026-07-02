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
 * Regression coverage for request-helper dependencies that inherit a caller
 * request location after running nested response dependencies.
 */
public class JPostmanAnnotationRequestDependencyRegressionTest {

	/**
	 * Verifies that a blank @JPostman.Request helper used by a product request
	 * keeps the caller's product request context after its nested login/token
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

	private static ApiExecutor okExecutor(int statusCode, String json) {
		return () -> okResponse(statusCode, json);
	}

	private static ApiResponse okResponse(int statusCode, String json) {
		return new ApiResponse(statusCode, json, json.getBytes(), Map.of());
	}
}
