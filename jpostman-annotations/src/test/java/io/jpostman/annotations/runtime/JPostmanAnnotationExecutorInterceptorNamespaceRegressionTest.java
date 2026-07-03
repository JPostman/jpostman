package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for request-helper namespace isolation and namespace
 * scoped void executor interceptors.
 */
public class JPostmanAnnotationExecutorInterceptorNamespaceRegressionTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";
	private static final String CACHE_TOKEN = "accessToken";
	private static final String TOKEN_API = "#token";
	private static final String NAMESPACE = "test";

	@Test
	public void requestDependencyKeepsOwnDefaultNamespaceButParentResponseInterceptorUsesParentNamespace()
			throws Exception {
		DependencyNamespaceFixture fixture = new DependencyNamespaceFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "filter");

		assertEquals("", fixture.tokenNamespace);
		assertEquals("", fixture.authRequestNamespace);
		assertEquals(1, fixture.intercepts);
		assertEquals(NAMESPACE, fixture.interceptNamespace);
		assertEquals("defaultIntercept", fixture.interceptMethod);
		assertEquals("Get current auth user", fixture.interceptRequest);
		assertTrue(fixture.interceptMethods.contains("defaultExecutor(#auth)"),
				"Actual methods: " + fixture.interceptMethods);
		assertTrue(fixture.interceptMethods.contains("defaultExecutor(#token)"),
				"Actual methods: " + fixture.interceptMethods);
		assertTrue(fixture.interceptMethods.contains("defaultIntercept"),
				"Actual methods: " + fixture.interceptMethods);
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class DependencyNamespaceFixture {

		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private String tokenNamespace;
		private String authRequestNamespace;
		private int intercepts;
		private String interceptNamespace;
		private String interceptMethod;
		private String interceptRequest;
		private String interceptMethods;

		@JPostman.Response(id = "auth", request = "Login user and get tokens", cache = CACHE_TOKEN)
		public String getToken(JPostman.Test test, JPostman.Info info) {
			tokenNamespace = info.attr().namespace;
			return test.path("accessToken");
		}

		@JPostman.Request(id = TOKEN_API, request = "Get current auth user", dependsOn = "#auth")
		public void authRequest(JPostman.Test ctx, JPostman.Info info) {
			authRequestNamespace = info.attr().namespace;
			info.sauth("oauth2", ctx.cache(CACHE_TOKEN));
			info.add().headers("METHOD", info.attr().method);
		}

		@JPostman.Response(dependsOn = TOKEN_API, namespace = NAMESPACE, verify = 200)
		@org.testng.annotations.Test
		public void filter() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			String requestLog = ctx.request().log();
			if (requestLog.contains("Login user and get tokens")) {
				return okExecutor("{\"accessToken\":\"token-123\"}");
			}
			return okExecutor("{\"id\":1,\"firstName\":\"Emily\"}");
		}

		@JPostman.Executor(namespace = NAMESPACE)
		public void defaultIntercept(JPostman.Test test, JPostman.Info info) {
			intercepts++;
			interceptNamespace = info.attr().namespace;
			interceptMethod = info.attr().method;
			interceptRequest = info.attr().request;
			interceptMethods = info.attr().methods.toString();
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
