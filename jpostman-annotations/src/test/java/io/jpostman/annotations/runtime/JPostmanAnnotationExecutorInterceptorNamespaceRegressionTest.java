package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Regression coverage for namespace-scoped void executor interceptors and the
 * namespace supplied by the selected/default interceptor.
 */
public class JPostmanAnnotationExecutorInterceptorNamespaceRegressionTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";
	private static final String CACHE_TOKEN = "accessToken";
	private static final String TOKEN_API = "#token";
	private static final String NAMESPACE = "test";

	@Test
	public void singleInterceptorNamespaceIsTheDefaultForResponseAndRequestDependencies() throws Exception {
		DependencyNamespaceFixture fixture = new DependencyNamespaceFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "filter");

		assertEquals(NAMESPACE, fixture.tokenNamespace);
		assertEquals(NAMESPACE, fixture.authRequestNamespace);
		assertEquals(2, fixture.intercepts);
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

	@Test
	public void defaultExecutorNamespaceFlowsThroughNestedDependencyChain() throws Exception {
		DefaultNamespaceDependencyFixture fixture = new DefaultNamespaceDependencyFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "profile");

		assertEquals(2, fixture.interceptorCalls);
		assertEquals(List.of("restage", "restage"), fixture.interceptorNamespaces);
		assertEquals(List.of("Login user and get tokens", "Get current auth user"), fixture.interceptorRequests);
	}

	@Test
	public void singleDefaultInterceptorNamespaceIsAppliedBeforeRequestLookup() throws Exception {
		DefaultInterceptorNamespaceFixture fixture = new DefaultInterceptorNamespaceFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "profile");

		assertEquals(1, fixture.providerCalls);
		assertEquals(1, fixture.interceptorCalls);
		assertEquals("restage", fixture.interceptorNamespace);
		assertEquals("Get current auth user", fixture.interceptorRequest);
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class DefaultNamespaceDependencyFixture {

		@JPostman.Context(config = "classpath:executor-default-namespace.properties", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int interceptorCalls;
		private final List<String> interceptorNamespaces = new ArrayList<>();
		private final List<String> interceptorRequests = new ArrayList<>();

		@JPostman.Response(id = "Ref1", request = "Login user and get tokens")
		public void login() {
		}

		@JPostman.Request(id = "Ref2", dependsOn = "#Ref1")
		public void auth(JPostman.Info info) {
			info.headers("Authorization", "Bearer token-123");
		}

		@JPostman.Response(request = "Get current auth user", dependsOn = "#Ref2")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return okExecutor("{\"accessToken\":\"token-123\",\"id\":1}");
		}

		@JPostman.Executor(namespace = "restage")
		public void restageInterceptor(JPostman.Info info) {
			interceptorCalls++;
			interceptorNamespaces.add(info.attr().namespace);
			interceptorRequests.add(info.attr().request);
		}
	}

	@JPostman.TestNG
	private static final class DefaultInterceptorNamespaceFixture {

		@JPostman.Context(config = "classpath:executor-default-namespace.properties", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int providerCalls;
		private int interceptorCalls;
		private String interceptorNamespace;
		private String interceptorRequest;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			providerCalls++;
			return okExecutor("{\"id\":1,\"firstName\":\"Emily\"}");
		}

		@JPostman.Executor(namespace = "restage")
		public void restageInterceptor(JPostman.Test test, JPostman.Info info) {
			interceptorCalls++;
			interceptorNamespace = info.attr().namespace;
			interceptorRequest = info.attr().request;
		}
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
			info.headers("METHOD", info.attr().method);
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
