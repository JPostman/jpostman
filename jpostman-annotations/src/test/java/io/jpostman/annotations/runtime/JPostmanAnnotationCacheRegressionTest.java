package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for annotation dependency caching across framework test
 * method runs.
 */
public class JPostmanAnnotationCacheRegressionTest {

	/**
	 * Verifies that a cached @JPostman.Response dependency is executed only once
	 * for the same test class instance, even when multiple TestNG test methods
	 * depend on it.
	 *
	 * <p>
	 * This mirrors the real TestNG integration path: setup runs once for the class,
	 * then each test method calls JPostmanAnnotationEngine.runTestNg(...), which
	 * creates a new annotation runner internally. The cached token must survive
	 * those method-level runner instances.
	 * </p>
	 */
	@Test
	public void cachedResponseDependencyRunsOnceAcrossMultipleTestNgMethodRuns() throws Exception {
		CachedResponseDependencyFixture fixture = new CachedResponseDependencyFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "firstAuthUser");
		runTestNg(fixture, "secondAuthUser");
		runTestNg(fixture, "thirdAuthUser");

		assertEquals(1, fixture.tokenMethodCalls,
				"The cached getToken() response dependency must execute only once per test instance.");
		assertEquals(1, fixture.loginExecutorCalls,
				"The Login user and get tokens request must execute only once because getToken() is cached.");
		assertEquals(3, fixture.authExecutorCalls, "Each top-level auth-user response should still execute normally.");
		assertEquals(3, fixture.authRequestCalls,
				"The auth request helper can run for each top-level test, but it must reuse the cached token.");
		assertNotNull(fixture.api);
		assertEquals("token-123", fixture.api.cache("token"));
	}

	@Test
	public void blankCacheUsesAnnotationIdAndImplicitLookupIgnoresUncachedDependencies() throws Exception {
		IdBackedCacheFixture fixture = new IdBackedCacheFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "profile");

		assertEquals("token-123", fixture.explicitIdToken);
		assertEquals("token-123", fixture.implicitToken);
		assertEquals("token-123", fixture.customToken);
		assertEquals(1, fixture.primaryCalls);
		assertEquals(1, fixture.backupCalls);
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class CachedResponseDependencyFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<TestNgContext> runtime;

		@JPostman.TestContext
		private JPostman.Test api;

		private int tokenMethodCalls;
		private int authRequestCalls;
		private int loginExecutorCalls;
		private int authExecutorCalls;

		@JPostman.Response(id = "Ref1", request = "Login user and get tokens", cache = "token")
		public String getToken(TestNgContext ctx, JPostman.Info info) {
			tokenMethodCalls++;
			assertNotNull(ctx.response(), "The cached response dependency should execute before its method body.");
			return "token-123";
		}

		@JPostman.Request(dependsOn = "getToken")
		public void authRequest(JPostman.Test test, JPostman.Info info) {
			authRequestCalls++;
			assertEquals("token-123", test.cache("token"));
			assertEquals("token-123", test.cache("#Ref1"));
			info.sauth("oauth2", test.cache("token"));
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = "authRequest", verify = 200)
		@org.testng.annotations.Test
		public void firstAuthUser() {
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = "authRequest", verify = 200)
		@org.testng.annotations.Test
		public void secondAuthUser() {
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = "authRequest", verify = 200)
		@org.testng.annotations.Test
		public void thirdAuthUser() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostman.Info info) {
			String requestName = ctx.request().log();
			if (requestName.contains("Login user and get tokens")) {
				loginExecutorCalls++;
				return okExecutor("{\"accessToken\":\"executor-token\"}");
			}
			if (requestName.contains("Get current auth user")) {
				authExecutorCalls++;
				return okExecutor("{\"id\":1,\"firstName\":\"John\"}");
			}
			throw new AssertionError("Unexpected request: " + requestName);
		}
	}

	@JPostman.TestNG
	private static final class IdBackedCacheFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<TestNgContext> runtime;

		private String explicitIdToken;
		private String implicitToken;
		private String customToken;
		private int primaryCalls;
		private int backupCalls;

		@JPostman.Response(id = "Ref1", request = "Login user and get tokens", cache = "")
		public void loginPrimary() {
			primaryCalls++;
		}

		@JPostman.Response(id = "Ref2", request = "Login user and get tokens")
		public void loginBackup() {
			backupCalls++;
		}

		@JPostman.Request(dependsOn = { "#Ref1", "#Ref2" })
		public void prepare(JPostman.Test test) {
			explicitIdToken = test.cache("#Ref1:accessToken", String.class);
			implicitToken = test.cache("accessToken", String.class);
			customToken = test.cache("Ref1/accessToken", String.class);
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = "prepare", verify = 200)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostman.Info info) {
			if (ctx.request().log().contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\"}");
			}
			return okExecutor("{\"id\":1}");
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
