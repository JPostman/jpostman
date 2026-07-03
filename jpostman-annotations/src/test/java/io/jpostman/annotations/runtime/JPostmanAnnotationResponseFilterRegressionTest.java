package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for response filters when a request dependency prepares
 * the active request.
 */
public class JPostmanAnnotationResponseFilterRegressionTest {

	private static final String CACHE_TOKEN = "accessToken";
	private static final String TOKEN_API = "#token";

	@Test
	public void responseFilterIsAppliedToLogAfterDependencyRequestPreparesRequest() throws Exception {
		FilterDependencyFixture fixture = new FilterDependencyFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "filter1");
		assertEquals(1, fixture.tokenExecutions);
		assertEquals(1, fixture.profileExecutions);
		assertNumberEquals(1, fixture.jpostman.ctx().path("id"));
		assertEquals("Emily-1", fixture.jpostman.ctx().path("firstName"));
		assertEquals("Johnson-1", fixture.jpostman.ctx().path("lastName"),
				"filter is for secure log output; it should not delete the raw response path.");
		assertFilteredLog(fixture.jpostman.ctx().log(), "Emily-1", "Johnson-1", "female-1");

		runTestNg(fixture, "filter2");
		assertEquals(1, fixture.tokenExecutions, "The cached auth response should run only once.");
		assertEquals(2, fixture.profileExecutions);
		assertEquals("Emily-2", fixture.jpostman.ctx().path("firstName"));
		assertEquals("Johnson-2", fixture.jpostman.ctx().path("lastName"),
				"filter is for secure log output; it should not delete the raw response path.");
		assertFilteredLog(fixture.jpostman.ctx().log(), "Emily-2", "\"id\": 2", "Johnson-2", "female-2");

		runTestNg(fixture, "filter3");
		assertEquals(1, fixture.tokenExecutions, "The cached auth response should run only once.");
		assertEquals(3, fixture.profileExecutions, "Each response test needs its own profile request execution.");
		assertEquals("Johnson-3", fixture.jpostman.ctx().path("lastName"));
		assertEquals("Emily-3", fixture.jpostman.ctx().path("firstName"),
				"filter is for secure log output; it should not delete the raw response path.");
		assertFilteredLog(fixture.jpostman.ctx().log(), "Johnson-3", "\"id\": 3", "Emily-3", "female-3");
	}

	@Test
	public void cachedResponseDependencyIsPreservedForRuntimeOnlyNamespaceAcrossResponseTests() throws Exception {
		RuntimeNamespaceFilterDependencyFixture fixture = new RuntimeNamespaceFilterDependencyFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "filter1");
		assertEquals(1, fixture.tokenExecutions);
		assertEquals(1, fixture.authExecutions);
		assertEquals(1, fixture.profileExecutions);
		assertEquals("Emily-1", fixture.jpostman.ctx().path("firstName"));

		runTestNg(fixture, "filter2");
		assertEquals(1, fixture.tokenExecutions, "The cached auth response should run only once in namespace=test.");
		assertEquals(2, fixture.authExecutions);
		assertEquals(2, fixture.profileExecutions);
		assertEquals("Emily-2", fixture.jpostman.ctx().path("firstName"));

		runTestNg(fixture, "filter3");
		assertEquals(1, fixture.tokenExecutions, "The cached auth response should still run only once.");
		assertEquals(3, fixture.authExecutions);
		assertEquals(3, fixture.profileExecutions);
		assertEquals("Johnson-3", fixture.jpostman.ctx().path("lastName"));
	}

	@Test
	public void responseFilterOverrideFromResponseDependencyIsActiveForTestBody() throws Exception {
		ResponseDependencyOverrideFixture fixture = new ResponseDependencyOverrideFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNgWithBody(fixture, "filter1");
		assertFilteredLog(fixture.jpostman.ctx().response().log(), "\"gender\": \"female-1\"", "firstName", "lastName",
				"\"id\"");
		assertEquals(1, fixture.authResponseBodyChecks);

		runTestNgWithBody(fixture, "filter2");
		assertFilteredLog(fixture.jpostman.ctx().response().log(), "\"firstName\": \"Emily-2\"", "lastName", "gender",
				"\"id\"");
		assertEquals(2, fixture.authResponseBodyChecks);

		runTestNgWithBody(fixture, "filter3");
		assertFilteredLog(fixture.jpostman.ctx().response().log(), "\"lastName\": \"Johnson-3\"", "firstName", "gender",
				"\"id\"");
		assertEquals(3, fixture.authResponseBodyChecks);
	}

	@Test
	public void responseDependencyFilterDoesNotLeakIntoCallerOwnRequestFilter() throws Exception {
		ResponseDependencyFilterIsolationFixture fixture = new ResponseDependencyFilterIsolationFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNgWithBody(fixture, "getCurrentAuthUser");
		assertEquals(1, fixture.loginBodyChecks);
		assertEquals(1, fixture.currentUserExecutions);
		assertFilteredLog(fixture.jpostman.ctx().response().log(), "\"lastName\": \"Johnson\"", "firstName", "\"id\"",
				"gender");
	}

	@Test
	public void contextVerifyStatusCodeStillRunsWhenAssertionRulesApplyAfterDependencyRequest() throws Exception {
		VerifyStatusAfterAssertionsFixture fixture = new VerifyStatusAfterAssertionsFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);

		AssertionError error = assertThrows(AssertionError.class, () -> runTestNg(fixture, "filter1"));

		assertTrue(error.getMessage().contains("Status code mismatch: expected [401] but found [200]"),
				"Actual message: " + error.getMessage());
		assertEquals(1, fixture.tokenExecutions);
		assertEquals(1, fixture.profileExecutions);
	}

	private static void assertNumberEquals(int expected, Object actual) {
		assertTrue(actual instanceof Number, "Expected numeric response path but was: " + actual);
		assertEquals(expected, ((Number) actual).intValue());
	}

	private static void assertFilteredLog(String log, String expected, String... filteredOut) {
		assertTrue(log.contains(expected), "Expected filtered log to contain: " + expected + "\n" + log);
		for (String value : filteredOut) {
			assertFalse(log.contains(value), "Expected filtered log to remove: " + value + "\n" + log);
		}
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	private static void runTestNgWithBody(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
		method.invoke(fixture);
	}

	@JPostman.TestNG
	private static final class FilterDependencyFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int tokenExecutions;
		private int profileExecutions;

		@JPostman.Response(id = "auth", request = "Login user and get tokens", cache = CACHE_TOKEN)
		public String getToken(JPostman.Test test, JPostman.Info info) {
			tokenExecutions++;
			assertTrue(test.cache(CACHE_TOKEN) == null);
			return test.asserts().exists("accessToken", "Access token not found").verify().path("accessToken");
		}

		@JPostman.Request(id = TOKEN_API, request = "Get current auth user", dependsOn = "#auth")
		public void authRequest(JPostman.Test ctx, JPostmanInfo info) {
			info.sauth("oauth2", ctx.cache(CACHE_TOKEN));
		}

		@JPostman.Response(dependsOn = TOKEN_API, filter = { "id", "firstName" })
		@org.testng.annotations.Test
		public void filter1() {
		}

		@JPostman.Response(dependsOn = TOKEN_API, filter = { "firstName" })
		@org.testng.annotations.Test
		public void filter2() {
		}

		@JPostman.Response(dependsOn = TOKEN_API, filter = { "lastName" })
		@org.testng.annotations.Test
		public void filter3() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestLog = ctx.request().log();
			if (requestLog.contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\"}");
			}

			profileExecutions++;
			int execution = profileExecutions;
			String body = "{" + "\"id\":" + execution + "," + "\"firstName\":\"Emily-" + execution + "\","
					+ "\"lastName\":\"Johnson-" + execution + "\"," + "\"gender\":\"female-" + execution + "\"" + "}";
			return okExecutor(body);
		}
	}

	@JPostman.TestNG
	private static final class RuntimeNamespaceFilterDependencyFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int tokenExecutions;
		private int authExecutions;
		private int profileExecutions;

		@JPostman.Response(id = "auth", namespace = "test", request = "Login user and get tokens", cache = CACHE_TOKEN)
		public String getToken(JPostman.Test test, JPostman.Info info) {
			tokenExecutions++;
			assertTrue(test.cache(CACHE_TOKEN) == null);
			assertEquals("test", info.attr().namespace);
			return test.asserts().exists("accessToken", "Access token not found").verify().path("accessToken");
		}

		@JPostman.Request(id = TOKEN_API, namespace = "test", request = "Get current auth user", dependsOn = "#auth")
		public void authRequest(JPostman.Test ctx, JPostman.Info info) {
			authExecutions++;
			assertEquals("token-123", ctx.cache(CACHE_TOKEN),
					"The namespace=test request context should receive the cached access token.");
			info.sauth("oauth2", ctx.cache(CACHE_TOKEN));
			info.add().headers("METHOD", info.attr().method);
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, filter = { "id", "firstName" })
		@org.testng.annotations.Test
		public void filter1() {
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, filter = { "firstName" })
		@org.testng.annotations.Test
		public void filter2() {
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, filter = { "lastName" })
		@org.testng.annotations.Test
		public void filter3() {
		}

		@JPostman.Executor(namespace = "test")
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestLog = ctx.request().log();
			if (requestLog.contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\"}");
			}

			profileExecutions++;
			int execution = profileExecutions;
			String body = "{" + "\"id\":" + execution + "," + "\"firstName\":\"Emily-" + execution + "\","
					+ "\"lastName\":\"Johnson-" + execution + "\"," + "\"gender\":\"female-" + execution + "\"" + "}";
			return okExecutor(body);
		}
	}

	@JPostman.TestNG
	private static final class ResponseDependencyOverrideFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int profileExecutions;
		private int authResponseBodyChecks;

		@JPostman.Response(id = "auth", namespace = "test", request = "Login user and get tokens", cache = CACHE_TOKEN)
		public String getToken(JPostman.Test test, JPostman.Info info) {
			assertEquals(jpostman.ctx().response().log(),
					jpostman.ctx().response().filter(java.util.List.of("/**/")).log());
			return test.asserts().exists("accessToken", "Access token not found").verify().path("accessToken");
		}

		@JPostman.Request(id = "tokenReq", namespace = "test", request = "Get current auth user", dependsOn = "#auth")
		public void authRequest(JPostman.Test ctx, JPostman.Info info) {
			assertEquals("token-123", ctx.cache(CACHE_TOKEN));
			info.sauth("oauth2", ctx.cache(CACHE_TOKEN));
		}

		@JPostman.Response(id = TOKEN_API, dependsOn = "#tokenReq", filter = { "id", "firstName", "lastName", "gender",
				"date" })
		public void authRequestResponse(JPostman.Test test, JPostman.Info info) {
			authResponseBodyChecks++;
			assertEquals(jpostman.ctx().response().log(), jpostman.ctx().response()
					.filter(java.util.List.of("id", "firstName", "lastName", "gender", "date")).log());
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, filter = { "gender" })
		@org.testng.annotations.Test
		public void filter1() {
			assertEquals(jpostman.ctx().response().log(),
					jpostman.ctx().response().filter(java.util.List.of("gender")).log());
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, filter = { "firstName" })
		@org.testng.annotations.Test
		public void filter2() {
			assertEquals(jpostman.ctx().response().log(),
					jpostman.ctx().response().filter(java.util.List.of("firstName")).log());
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, filter = { "lastName" })
		@org.testng.annotations.Test
		public void filter3() {
			assertEquals(jpostman.ctx().response().log(),
					jpostman.ctx().response().filter(java.util.List.of("lastName")).log());
		}

		@JPostman.Executor(namespace = "test")
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestLog = ctx.request().log();
			if (requestLog.contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\",\"gender\":\"female-login\"}");
			}

			profileExecutions++;
			int execution = profileExecutions;
			String body = "{" + "\"id\":" + execution + "," + "\"firstName\":\"Emily-" + execution + "\","
					+ "\"lastName\":\"Johnson-" + execution + "\"," + "\"maidenName\":\"Smith\","
					+ "\"gender\":\"female-" + execution + "\"" + "}";
			return okExecutor(body);
		}
	}

	@JPostman.TestNG
	private static final class ResponseDependencyFilterIsolationFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int loginBodyChecks;
		private int currentUserExecutions;

		@JPostman.Response(id = "#login2", request = "Login user and get tokens", filter = "firstName")
		public void login(JPostman.Test test) {
			loginBodyChecks++;
			assertEquals(jpostman.ctx().response().log(),
					jpostman.ctx().response().filter(java.util.List.of("firstName")).log());
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = "#login2", filter = "lastName")
		@org.testng.annotations.Test
		public void getCurrentAuthUser() {
			assertEquals(jpostman.ctx().response().log(),
					jpostman.ctx().response().filter(java.util.List.of("lastName")).log());
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestLog = ctx.request().log();
			if (requestLog.contains("Login user and get tokens")) {
				return okExecutor(
						"{\"firstName\":\"Emily-login\",\"lastName\":\"Johnson-login\",\"gender\":\"female-login\"}");
			}

			currentUserExecutions++;
			return okExecutor("{\"id\":1,\"firstName\":\"Emily\",\"lastName\":\"Johnson\",\"gender\":\"female\"}");
		}
	}

	@JPostman.TestNG
	private static final class VerifyStatusAfterAssertionsFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", assertions = "classpath:verify-status-assertions.ini", verifyStatusCode = 401)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int tokenExecutions;
		private int profileExecutions;

		@JPostman.Response(id = "auth", namespace = "test", request = "Login user and get tokens", cache = CACHE_TOKEN, verify = 200)
		public String getToken(JPostman.Test test, JPostman.Info info) {
			tokenExecutions++;
			return test.asserts().exists("accessToken", "Access token not found").verify().path("accessToken");
		}

		@JPostman.Request(id = TOKEN_API, namespace = "test", request = "Get current auth user", dependsOn = "#auth")
		public void authRequest(JPostman.Test ctx, JPostman.Info info) {
			assertEquals("token-123", ctx.cache(CACHE_TOKEN));
			info.sauth("oauth2", ctx.cache(CACHE_TOKEN));
		}

		@JPostman.Response(namespace = "test", dependsOn = TOKEN_API, asserts = "status-ok", filter = { "firstName" })
		@org.testng.annotations.Test
		public void filter1() {
		}

		@JPostman.Executor(namespace = "test")
		public ApiExecutor testExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestLog = ctx.request().log();
			if (requestLog.contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\"}");
			}

			profileExecutions++;
			return okExecutor("{\"firstName\":\"Emily\"}");
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
