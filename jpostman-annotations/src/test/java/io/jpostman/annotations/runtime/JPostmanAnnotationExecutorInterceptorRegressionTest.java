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

/**
 * Regression coverage for void @JPostman.Executor post-response interceptors.
 */
public class JPostmanAnnotationExecutorInterceptorRegressionTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";

	@Test
	public void voidExecutorRunsAfterDefaultExecutorAndBeforeVerificationFailure() throws Exception {
		InterceptBeforeVerifyFixture fixture = new InterceptBeforeVerifyFixture();
		InterceptBeforeVerifyFixture.UnauthorizedExecutor.applyCount = 0;

		JPostmanAnnotationEngine.setupTestNg(fixture);

		AssertionError error = assertThrows(AssertionError.class, () -> runTestNg(fixture, "profile"));

		assertTrue(error.getMessage().contains("Status code mismatch"), "Actual message: " + error.getMessage());
		assertEquals(1, InterceptBeforeVerifyFixture.UnauthorizedExecutor.applyCount);
		assertEquals(0, fixture.globalInterceptCalls);
		assertEquals(1, fixture.interceptCalls);
		assertEquals("unauthorized", fixture.interceptedMessage);
		assertEquals("test", fixture.interceptedNamespace);
		assertEquals("Get current auth user", fixture.interceptedRequest);
	}

	@Test
	public void contextDefaultVerifyStatusCodeCanBeDisabledOrChanged() throws Exception {
		NoDefaultVerifyFixture noVerify = new NoDefaultVerifyFixture();
		JPostmanAnnotationEngine.setupTestNg(noVerify);
		runTestNg(noVerify, "profile");
		assertEquals(1, noVerify.defaultExecutorCalls);

		ZeroDefaultVerifyFixture zeroVerify = new ZeroDefaultVerifyFixture();
		JPostmanAnnotationEngine.setupTestNg(zeroVerify);
		runTestNg(zeroVerify, "profile");
		assertEquals(1, zeroVerify.defaultExecutorCalls);

		UnauthorizedDefaultVerifyFixture unauthorized = new UnauthorizedDefaultVerifyFixture();
		JPostmanAnnotationEngine.setupTestNg(unauthorized);
		runTestNg(unauthorized, "profile");
		assertEquals(1, unauthorized.defaultExecutorCalls);
	}

	@Test
	public void verifyStatusCodeRejectsInvalidPositiveValuesBelowOneHundred() throws Exception {
		InvalidDefaultVerifyFixture invalidDefault = new InvalidDefaultVerifyFixture();
		JPostmanAnnotationEngine.setupTestNg(invalidDefault);

		AssertionError defaultError = assertThrows(AssertionError.class, () -> runTestNg(invalidDefault, "profile"));

		assertTrue(defaultError.getMessage().contains(
				"verify status code must be 0 to pass without status verification, 1 to mark the completed test skipped, -1 to use the context default, or between 100 and 599"),
				"Actual message: " + defaultError.getMessage());

		InvalidResponseVerifyFixture invalidResponse = new InvalidResponseVerifyFixture();
		JPostmanAnnotationEngine.setupTestNg(invalidResponse);

		AssertionError responseError = assertThrows(AssertionError.class, () -> runTestNg(invalidResponse, "profile"));

		assertTrue(responseError.getMessage().contains(
				"verify status code must be 0 to pass without status verification, 1 to mark the completed test skipped, -1 to use the context default, or between 100 and 599"),
				"Actual message: " + responseError.getMessage());
	}

	@Test
	public void responseAndRunnerVerifyZeroSkipContextDefaultVerification() throws Exception {
		ResponseVerifyZeroFixture response = new ResponseVerifyZeroFixture();
		JPostmanAnnotationEngine.setupTestNg(response);
		runTestNg(response, "profile");
		assertEquals(1, response.defaultExecutorCalls);

		RunnerVerifyZeroFixture runner = new RunnerVerifyZeroFixture();
		JPostmanAnnotationEngine.setupTestNg(runner);
		runTestNg(runner, "runProfile");
		assertEquals(1, runner.defaultExecutorCalls);
	}

	@Test
	public void singleNamedExecutorProviderIsUsedAsDefault() throws Exception {
		SingleNamedProviderFixture fixture = new SingleNamedProviderFixture();
		SingleNamedProviderFixture.FallbackExecutor.applyCalls = 0;
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.providerCalls);
		assertEquals(0, SingleNamedProviderFixture.FallbackExecutor.applyCalls);
	}

	@Test
	public void multipleNamedExecutorProvidersRequireSelection() throws Exception {
		MultipleNamedProvidersFixture fixture = new MultipleNamedProvidersFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		AssertionError error = assertThrows(AssertionError.class, () -> runTestNg(fixture, "profile"));

		assertTrue(error.getMessage().contains("Multiple named @JPostmanExecutor providers found"),
				"Actual message: " + error.getMessage());
	}

	@Test
	public void singleNamedVoidExecutorIsUsedAsDefaultInterceptor() throws Exception {
		SingleNamedInterceptorFixture fixture = new SingleNamedInterceptorFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.providerCalls);
		assertEquals(1, fixture.interceptorCalls);
	}

	@Test
	public void unnamedVoidExecutorIsDefaultWhenMultipleInterceptorsExist() throws Exception {
		UnnamedInterceptorDefaultFixture fixture = new UnnamedInterceptorDefaultFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.defaultCalls);
		assertEquals(0, fixture.namedCalls);
	}

	@Test
	public void explicitExecutorIdSelectsNamedVoidInterceptor() throws Exception {
		ExplicitInterceptorFixture fixture = new ExplicitInterceptorFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.providerCalls);
		assertEquals(1, fixture.auditCalls);
		assertEquals(0, fixture.otherCalls);
	}

	@Test
	public void multipleNamedVoidInterceptorsRequireExecutorSelection() throws Exception {
		MultipleNamedInterceptorsFixture fixture = new MultipleNamedInterceptorsFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		AssertionError error = assertThrows(AssertionError.class, () -> runTestNg(fixture, "profile"));

		assertTrue(error.getMessage().contains("Multiple named @JPostmanExecutor interceptors found"),
				"Actual message: " + error.getMessage());
	}

	private static void runTestNg(Object fixture, String methodName) throws Exception {
		Method method = fixture.getClass().getDeclaredMethod(methodName);
		JPostmanAnnotationEngine.runTestNg(fixture, method);
	}

	@JPostman.TestNG
	private static final class SingleNamedProviderFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200, executorClass = FallbackExecutor.class)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int providerCalls;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor(id = "named")
		public ApiExecutor namedProvider() {
			providerCalls++;
			return okResponseExecutor(200, "{\"message\":\"ok\"}");
		}

		public static final class FallbackExecutor {
			private static int applyCalls;

			@SuppressWarnings("unused")
			public static ApiExecutor apply(Object request) {
				applyCalls++;
				return okResponseExecutor(200, "{\"message\":\"fallback\"}");
			}
		}
	}

	@JPostman.TestNG
	private static final class MultipleNamedProvidersFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor(id = "first")
		public ApiExecutor first() {
			return okResponseExecutor(200, "{\"message\":\"first\"}");
		}

		@JPostman.Executor(id = "second")
		public ApiExecutor second() {
			return okResponseExecutor(200, "{\"message\":\"second\"}");
		}
	}

	@JPostman.TestNG
	private static final class SingleNamedInterceptorFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int providerCalls;
		private int interceptorCalls;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			providerCalls++;
			return okResponseExecutor(200, "{\"message\":\"ok\"}");
		}

		@JPostman.Executor(id = "audit")
		public void audit() {
			interceptorCalls++;
		}
	}

	@JPostman.TestNG
	private static final class UnnamedInterceptorDefaultFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int defaultCalls;
		private int namedCalls;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return okResponseExecutor(200, "{\"message\":\"ok\"}");
		}

		@JPostman.Executor
		public void defaultInterceptor() {
			defaultCalls++;
		}

		@JPostman.Executor(id = "named")
		public void namedInterceptor() {
			namedCalls++;
		}
	}

	@JPostman.TestNG
	private static final class ExplicitInterceptorFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int providerCalls;
		private int auditCalls;
		private int otherCalls;

		@JPostman.Response(request = "Get current auth user", executor = "#audit")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			providerCalls++;
			return okResponseExecutor(200, "{\"message\":\"ok\"}");
		}

		@JPostman.Executor(id = "audit")
		public void audit() {
			auditCalls++;
		}

		@JPostman.Executor(id = "other")
		public void other() {
			otherCalls++;
		}
	}

	@JPostman.TestNG
	private static final class MultipleNamedInterceptorsFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return okResponseExecutor(200, "{\"message\":\"ok\"}");
		}

		@JPostman.Executor(id = "first")
		public void first() {
		}

		@JPostman.Executor(id = "second")
		public void second() {
		}
	}

	@JPostman.TestNG
	private static final class InterceptBeforeVerifyFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int globalInterceptCalls;
		private int interceptCalls;
		private String interceptedMessage;
		private String interceptedNamespace;
		private String interceptedRequest;

		@JPostman.Response(namespace = "test", request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor(namespace = "test")
		public void defaultIntercept(JPostman.Test test, JPostman.Info info) {
			interceptCalls++;
			interceptedMessage = test.path("message");
			assertEquals("unauthorized", jpostman.ctx().path("message"));
			interceptedNamespace = info.attr().namespace;
			interceptedRequest = info.attr().request;
			assertNotNull(test.log());
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			UnauthorizedExecutor.applyCount++;
			return okResponseExecutor(401, "{\"message\":\"unauthorized\"}");
		}

		public static final class UnauthorizedExecutor {
			private static int applyCount;

			@SuppressWarnings("unused")
			public static ApiExecutor apply(Object request) {
				applyCount++;
				return () -> new ApiResponse(401, "{\"message\":\"unauthorized\"}",
						"{\"message\":\"unauthorized\"}".getBytes(), Map.of());
			}
		}

		@JPostman.Executor
		public void globalIntercept(JPostman.Info info) {
			globalInterceptCalls++;
		}
	}

	@JPostman.TestNG
	private static final class NoDefaultVerifyFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = -1)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int defaultExecutorCalls;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			defaultExecutorCalls++;
			return okResponseExecutor(401, "{\"message\":\"not checked\"}");
		}
	}

	@JPostman.TestNG
	private static final class ZeroDefaultVerifyFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int defaultExecutorCalls;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			defaultExecutorCalls++;
			return okResponseExecutor(401, "{\"message\":\"not checked\"}");
		}
	}

	@JPostman.TestNG
	private static final class ResponseVerifyZeroFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int defaultExecutorCalls;

		@JPostman.Response(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			defaultExecutorCalls++;
			return okResponseExecutor(201, "{\"message\":\"created but not verified\"}");
		}
	}

	@JPostman.TestNG
	private static final class RunnerVerifyZeroFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int defaultExecutorCalls;

		@JPostman.Runner(include = { "Get current auth user" }, verify = 0)
		@org.testng.annotations.Test
		public void runProfile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			defaultExecutorCalls++;
			return okResponseExecutor(201, "{\"message\":\"created but not verified\"}");
		}
	}

	@JPostman.TestNG
	private static final class InvalidDefaultVerifyFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 99)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			return okResponseExecutor(99, "{\"message\":\"invalid\"}");
		}
	}

	@JPostman.TestNG
	private static final class InvalidResponseVerifyFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Response(request = "Get current auth user", verify = 99)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			return okResponseExecutor(99, "{\"message\":\"invalid\"}");
		}
	}

	@JPostman.TestNG
	private static final class UnauthorizedDefaultVerifyFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 401)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int defaultExecutorCalls;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			defaultExecutorCalls++;
			return okResponseExecutor(401, "{\"message\":\"unauthorized\"}");
		}
	}

	private static ApiExecutor okResponseExecutor(int status, String json) {
		return () -> new ApiResponse(status, json, json.getBytes(), Map.of());
	}
}
