package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testng.SkipException;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for compact @JPostman.TestContext mirrors and runtime
 * context access after TestNG method execution and skipped tests.
 */
public class JPostmanAnnotationContextRegressionTest {

	/**
	 * Verifies the teardown-visible context behavior expected by compact tests:
	 *
	 * <ul>
	 * <li>{@code active = false} keeps the default namespace context. In this flow,
	 * the default namespace response is the cached login response.</li>
	 * <li>{@code active = true} follows the latest active context. In this flow,
	 * the latest active response is the product namespace response.</li>
	 * <li>{@code jpostman.ctx()} exposes the latest active context,
	 * {@code jpostman.ctx("")} exposes the default namespace context, and
	 * {@code jpostman.ctx("product")} exposes the product namespace context.</li>
	 * <li>A later skipped response must not replace the injected fields with fresh
	 * empty contexts.</li>
	 * </ul>
	 */
	@Test
	public void compactTestContextsReturnDefaultAndActiveResponsesAfterSkippedTest() throws Exception {
		CompactContextTeardownFixture fixture = new CompactContextTeardownFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "newKeyboardProduct");
		fixture.assertTeardownContexts("after product response");

		assertThrows(SkipException.class, () -> runTestNg(fixture, "newShoesProduct"));
		fixture.assertTeardownContexts("after skipped response");

		assertEquals(1, fixture.tokenMethodCalls, "The cached login response dependency should execute only once.");
		assertEquals(1, fixture.loginExecutorCalls,
				"The Login user and get tokens request should execute only once because it is cached.");
		assertEquals(1, fixture.productExecutorCalls,
				"Only the non-skipped product response should execute the product request.");
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class CompactContextTeardownFixture {

		@JPostman.Context(verifyStatusCode = 200, logs = "debug", debug = "none")
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.TestContext(active = false)
		private JPostman.Test api;

		@JPostman.TestContext(active = true)
		private JPostman.Test active;

		@JPostman.TestContext(namespace = "product")
		private JPostman.Test product;

		@JPostman.ReportContext
		private JPostman.Report report;

		private int tokenMethodCalls;
		private int loginExecutorCalls;
		private int productExecutorCalls;

		@JPostman.Response(request = "Login user and get tokens", cache = "token")
		public String getToken(TestNgContext ctx, JPostman.Info info) {
			tokenMethodCalls++;
			assertNotNull(ctx.response(), "getToken should see the executed login response.");
			return ctx.path("accessToken");
		}

		@JPostman.Request(dependsOn = "getToken")
		public void authRequest(TestNgContext ctx, JPostman.Info info) {
			assertEquals("token-123", ctx.cache("token"));
			info.sauth("oauth2", ctx.cache("token"));
		}

		@JPostman.Request(namespace = "product", request = "Get current auth user", dependsOn = "authRequest")
		public void newProduct(TestNgContext ctx, JPostman.Info info) {
			// The context-regression test is about response mirrors, not cross-namespace
			// cache reads from the product helper. The token cache is validated in
			// authRequest() and by the token/executor call counters below.
		}

		@JPostman.Response(tags = "keyboard", dependsOn = "newProduct", verify = 200)
		@org.testng.annotations.Test
		public void newKeyboardProduct() {
			assertEquals("product", product.path("source"));
			assertEquals("product", active.path("source"));
			assertEquals("product", jpostman.ctx().path("source"));
			assertEquals("login", api.path("source"));
			assertEquals("login", jpostman.ctx("").path("source"));
		}

		@JPostman.Response(tags = "shoes", dependsOn = "newProduct", skipReason = "Testing")
		@org.testng.annotations.Test
		public void newShoesProduct() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestName = ctx.request().log();
			if (requestName.contains("Login user and get tokens")) {
				loginExecutorCalls++;
				return okExecutor("{\"source\":\"login\",\"accessToken\":\"token-123\"}");
			}
			if ("product".equals(info.namespace) && requestName.contains("Get current auth user")) {
				productExecutorCalls++;
				return okExecutor("{\"source\":\"product\",\"id\":1}");
			}
			throw new AssertionError("Unexpected request: namespace=" + info.namespace + ", request=" + requestName);
		}

		void assertTeardownContexts(String phase) {
			assertNotNull(report, phase + ": report should be injected");
			assertNotNull(api, phase + ": default api context should be injected");
			assertNotNull(active, phase + ": active context should be injected");
			assertNotNull(product, phase + ": product context should be injected");
			assertNotNull(jpostman, phase + ": runtime should be injected");

			assertNotNull(api.response(), phase + ": active=false api response should not be null");
			assertNotNull(active.response(), phase + ": active=true response should not be null");
			assertNotNull(product.response(), phase + ": product response should not be null");
			assertNotNull(jpostman.ctx().response(), phase + ": active runtime response should not be null");
			assertNotNull(jpostman.ctx("").response(), phase + ": default runtime response should not be null");
			assertNotNull(jpostman.ctx("product").response(), phase + ": product runtime response should not be null");

			assertEquals("login", api.path("source"), phase + ": active=false should keep default login response");
			assertEquals("product", active.path("source"), phase + ": active=true should follow product response");
			assertEquals("product", product.path("source"), phase + ": product namespace should keep product response");
			assertEquals("product", jpostman.ctx().path("source"),
					phase + ": runtime ctx() should follow the latest active response");
			assertEquals("login", jpostman.ctx("").path("source"),
					phase + ": runtime ctx(\"\") should keep the default login response");
			assertEquals("product", jpostman.ctx("product").path("source"),
					phase + ": runtime product ctx should keep product response");
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
