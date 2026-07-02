package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Regression coverage for dependency resolution by annotation id using
 * dependsOn = "#id".
 */
public class JPostmanAnnotationDependencyIdRegressionTest {

	@Test
	public void requestAndResponseDependenciesCanBeResolvedById() throws Exception {
		DependencyByIdFixture fixture = new DependencyByIdFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "newMouseProduct");

		assertEquals(0, fixture.methodNameLoginCalls,
				"dependsOn = \"#login\" must resolve the annotation id, not a method named login.");
		assertEquals(1, fixture.tokenMethodCalls);
		assertEquals(1, fixture.authRequestCalls);
		assertEquals(1, fixture.newProductCalls);
		assertEquals(1, fixture.loginExecutorCalls);
		assertEquals(1, fixture.productExecutorCalls);
	}

	@Test
	public void annotationIdsMayReuseHashPrefixedConstants() throws Exception {
		HashPrefixedIdConstantFixture fixture = new HashPrefixedIdConstantFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "profile");

		assertEquals(1, fixture.tokenCalls);
		assertEquals(1, fixture.profileCalls);
		assertEquals("token", fixture.tokenInfoId);
		assertEquals("profile", fixture.profileInfoId);
	}

	@Test
	public void dependencyIdRequiresHashPrefix() throws Exception {
		IdRequiresHashFixture fixture = new IdRequiresHashFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		AssertionError error = assertThrows(AssertionError.class, () -> runTestNg(fixture, "newMouseProduct"));
		String message = error.getMessage();
		assertTrue(message.contains("Dependency method not found: login"), "Actual message: " + message);
		assertTrue(message.contains("Found JPostman annotation id \"login\""), "Actual message: " + message);
		assertTrue(message.contains("dependsOn = \"#login\""), "Actual message: " + message);
	}

	@Test
	public void executorCanBeResolvedByIdWithHashPrefix() throws Exception {
		ExecutorByIdFixture fixture = new ExecutorByIdFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "loginResponse");

		assertEquals(1, fixture.idExecutorCalls);
		assertEquals(0, fixture.methodNameExecutorCalls,
				"executor = \"#http\" must resolve the annotation id, not a method named http.");
	}

	@Test
	public void executorIdRequiresHashPrefix() throws Exception {
		ExecutorIdRequiresHashFixture fixture = new ExecutorIdRequiresHashFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		AssertionError error = assertThrows(AssertionError.class, () -> runTestNg(fixture, "loginResponse"));
		String message = error.getMessage();
		assertTrue(message.contains("Executor method not found: http"), "Actual message: " + message);
		assertTrue(message.contains("Found @JPostmanExecutor id \"http\""), "Actual message: " + message);
		assertTrue(message.contains("executor = \"#http\""), "Actual message: " + message);
	}

	@Test
	public void annotationIdsMustBeUniqueAcrossRequestResponseAndExecutor() {
		DuplicateAnnotationIdFixture fixture = new DuplicateAnnotationIdFixture();

		AssertionError error = assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.setupTestNg(fixture));
		String message = error.getMessage();
		assertTrue(message.contains("Duplicate JPostman annotation ids found."), "Actual message: " + message);
		assertTrue(message.contains("id=\"shared\""), "Actual message: " + message);
		assertTrue(message.contains("@JPostmanRequest"), "Actual message: " + message);
		assertTrue(message.contains("@JPostmanResponse"), "Actual message: " + message);
		assertTrue(message.contains("@JPostmanExecutor"), "Actual message: " + message);
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class DependencyByIdFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int tokenMethodCalls;
		private int authRequestCalls;
		private int newProductCalls;
		private int methodNameLoginCalls;
		private int loginExecutorCalls;
		private int productExecutorCalls;

		@JPostman.Request
		public void login(TestNgContext ctx, JPostmanInfo info) {
			methodNameLoginCalls++;
			throw new AssertionError("Method named login should not be called when dependsOn uses #login.");
		}

		@JPostman.Response(id = "login", request = "Login user and get tokens", cache = "token")
		public String getToken(TestNgContext ctx, JPostmanInfo info) {
			tokenMethodCalls++;
			assertEquals("login", info.id);
			assertNotNull(ctx.response(), "getToken should see the executed login response.");
			return ctx.path("accessToken");
		}

		@JPostman.Request(id = "auth", dependsOn = "#login")
		public void authRequest(TestNgContext ctx, JPostmanInfo info) {
			authRequestCalls++;
			assertEquals("auth", info.id);
			assertEquals("token-123", ctx.cache("token"));
			assertTrue(ctx.request().log().contains("Get current auth user"),
					"Request helper should receive the inherited product request, not the login request.");
			info.sauth("oauth2", ctx.cache("token"));
		}

		@JPostman.Request(id = "new-product", namespace = "product", request = "Get current auth user", dependsOn = "#auth")
		public void newProduct(TestNgContext ctx, JPostmanInfo info) {
			newProductCalls++;
			assertEquals("new-product", info.id);
			assertEquals("product", info.namespace);
			assertEquals("Get current auth user", info.request);
		}

		@JPostman.Response(tags = "mouse", dependsOn = "#new-product", verify = 200)
		@org.testng.annotations.Test
		public void newMouseProduct() {
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
	}

	@JPostman.TestNG
	private static final class HashPrefixedIdConstantFixture {
		private static final String TOKEN = "#token";
		private static final String PROFILE = "#profile";

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int tokenCalls;
		private int profileCalls;
		private String tokenInfoId;
		private String profileInfoId;

		@JPostman.Response(id = TOKEN, request = "Login user and get tokens", cache = "token")
		public String token(TestNgContext ctx, JPostmanInfo info) {
			tokenCalls++;
			tokenInfoId = info.id;
			return ctx.path("accessToken");
		}

		@JPostman.Request(id = PROFILE, dependsOn = TOKEN)
		public void profileRequest(TestNgContext ctx, JPostmanInfo info) {
			profileCalls++;
			profileInfoId = info.id;
			assertEquals("token-123", ctx.cache("token"));
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = PROFILE, verify = 200)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			if (ctx.request().log().contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\"}");
			}
			return okExecutor("{\"id\":1}");
		}
	}

	@JPostman.TestNG
	private static final class IdRequiresHashFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Response(id = "login", request = "Login user and get tokens", cache = "token")
		public String getToken(TestNgContext ctx, JPostmanInfo info) {
			return ctx.path("accessToken");
		}

		@JPostman.Response(dependsOn = "login", verify = 200)
		@org.testng.annotations.Test
		public void newMouseProduct() {
		}
	}

	@JPostman.TestNG
	private static final class ExecutorByIdFixture {
		private static final String HTTP = "#http";

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int idExecutorCalls;
		private int methodNameExecutorCalls;

		@JPostman.Response(request = "Login user and get tokens", executor = HTTP, verify = 200)
		@org.testng.annotations.Test
		public void loginResponse() {
		}

		@JPostman.Executor(id = HTTP)
		public ApiExecutor httpExecutor(TestNgContext ctx, JPostmanInfo info) {
			idExecutorCalls++;
			assertEquals("http", info.id);
			return okExecutor("{\"accessToken\":\"token-123\"}");
		}

		@JPostman.Executor
		public ApiExecutor http(TestNgContext ctx, JPostmanInfo info) {
			methodNameExecutorCalls++;
			throw new AssertionError("Method named http should not be called when executor uses #http.");
		}
	}

	@JPostman.TestNG
	private static final class ExecutorIdRequiresHashFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Response(request = "Login user and get tokens", executor = "http", verify = 200)
		@org.testng.annotations.Test
		public void loginResponse() {
		}

		@JPostman.Executor(id = "http")
		public ApiExecutor httpExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{\"accessToken\":\"token-123\"}");
		}
	}

	@JPostman.TestNG
	private static final class DuplicateAnnotationIdFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Request(id = "#shared")
		public void requestById(TestNgContext ctx, JPostmanInfo info) {
		}

		@JPostman.Response(id = "shared", request = "Login user and get tokens")
		@org.testng.annotations.Test
		public void responseById() {
		}

		@JPostman.Executor(id = "#shared")
		public ApiExecutor executorById(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}");
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
