package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;

/**
 * Regression coverage for void @JPostman.Executor interceptors that run
 * immediately before each response execution.
 */
public class JPostmanAnnotationExecutorInterceptorRegressionTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";

	@Test
	public void voidExecutorRunsBeforeAutoExecutionMappedFailure() throws Exception {
		PreExecutionFailureFixture fixture = new PreExecutionFailureFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(500, fixture.jpostman.info().statusCode());
		assertTrue(fixture.jpostman.info().syntheticResponse());
		assertEquals(1, fixture.providerCalls);
		assertEquals(0, fixture.globalInterceptCalls);
		assertEquals(1, fixture.interceptCalls);
		assertTrue(fixture.interceptorRanBeforeExecution);
		assertTrue(fixture.responseExecutionStarted);
		assertEquals("test", fixture.interceptedNamespace);
		assertEquals("Get current auth user", fixture.interceptedRequest);
	}

	@Test
	public void voidExecutorCanProceedCurrentResponseAndContinueAfterResponse() throws Exception {
		RuntimeProceedFixture fixture = new RuntimeProceedFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.requestExecutions);
		assertEquals(1, fixture.callbackCalls);
		assertEquals(1, fixture.responseCallbackCalls);
		assertEquals(1, fixture.afterCallCount);
		assertTrue(fixture.callbackSawRequest);
		assertTrue(fixture.responseCallbackSawResponse);
		assertTrue(fixture.responseCallbackSawInfo);
		assertTrue(fixture.afterCallSawResponse);
	}

	@Test
	public void voidExecutorMapsIllegalStateExceptionToSynthetic500AndContinues() throws Exception {
		RuntimeProceedFailureFixture fixture = new RuntimeProceedFailureFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.requestExecutions);
		assertEquals(1, fixture.callbackCalls);
		assertEquals(1, fixture.responseCallbackCalls);
		assertTrue(fixture.responseCallbackSawSyntheticResponse);
		assertTrue(fixture.continuedAfterFailure);
		assertEquals(500, fixture.statusCode);
		assertTrue(fixture.syntheticResponse);
		assertEquals(IllegalStateException.class, fixture.errorType);
	}

	@Test
	public void wrappedConnectExceptionMapsToSynthetic503AndPreservesCause() throws Exception {
		RuntimeProceedConnectFailureFixture fixture = new RuntimeProceedConnectFailureFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");

		assertEquals(1, fixture.requestExecutions);
		assertTrue(fixture.continuedAfterFailure);
		assertEquals(503, fixture.statusCode);
		assertTrue(fixture.syntheticResponse);
		assertEquals(ConnectException.class, fixture.errorCauseType);
		assertTrue(fixture.responseLog.contains("\"synthetic\": true"), fixture.responseLog);
		assertTrue(fixture.responseLog.contains("java.net.ConnectException"), fixture.responseLog);
	}

	@Test
	public void commonStandardExceptionsHaveStableSyntheticMappings() {
		assertEquals(503, JPostmanHttpErrorMapper.map(new ConnectException("refused")).statusCode());
		assertEquals(504, JPostmanHttpErrorMapper.map(new SocketTimeoutException("timeout")).statusCode());
		assertEquals(502, JPostmanHttpErrorMapper.map(new UnknownHostException("host")).statusCode());
		assertEquals(400, JPostmanHttpErrorMapper.map(new IllegalArgumentException("bad input")).statusCode());
		assertEquals(403, JPostmanHttpErrorMapper.map(new SecurityException("forbidden")).statusCode());
		assertEquals(501, JPostmanHttpErrorMapper.map(new UnsupportedOperationException("unsupported")).statusCode());
		assertEquals(500, JPostmanHttpErrorMapper.map(new IllegalStateException("failed")).statusCode());
		assertEquals(500, JPostmanHttpErrorMapper.map(new RuntimeException("failed")).statusCode());
	}

	@Test
	public void callTestRuntimeCallIsShadowedByExecutorProceedWithoutRecursion() throws Exception {
		RuntimeProceedCallFixture fixture = new RuntimeProceedCallFixture();
		JPostmanAnnotationEngine.setupTestNg(fixture);

		runTestNg(fixture, "profile");
		fixture.profile();

		assertEquals(1, fixture.requestExecutions);
		assertEquals(1, fixture.interceptorCalls);
		assertEquals(1, fixture.testBodyAfterCallCount);
	}

	@Test
	public void voidExecutorRunsOnceForEveryRunnerRequest() throws Exception {
		RunnerPerRequestInterceptorFixture fixture = new RunnerPerRequestInterceptorFixture();

		JPostmanAnnotationEngine.setupTestNg(fixture);
		runTestNg(fixture, "runProductFolder");

		assertEquals(3, fixture.requestExecutions);
		assertEquals(3, fixture.interceptorCalls);
		assertEquals(List.of("Folder request one", "Folder request two", "Folder request three"),
				fixture.interceptedRequests);
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
	private static final class RuntimeProceedFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> runtime;

		private int requestExecutions;
		private int callbackCalls;
		private int responseCallbackCalls;
		private int afterCallCount;
		private boolean callbackSawRequest;
		private boolean responseCallbackSawResponse;
		private boolean responseCallbackSawInfo;
		private boolean afterCallSawResponse;

		@JPostman.Response(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return () -> {
				requestExecutions++;
				return new ApiResponse(403, "{\"message\":\"forbidden\"}", "{\"message\":\"forbidden\"}".getBytes(),
						Map.of());
			};
		}

		@JPostman.Executor
		public void intercept() {
			runtime.call((test, info) -> {
				callbackCalls++;
				callbackSawRequest = test.request() != null && "Get current auth user".equals(info.attr().request);
			}).response((test, info) -> {
				responseCallbackCalls++;
				responseCallbackSawResponse = test.response() != null && test.statusCode() == 403;
				responseCallbackSawInfo = info != null && "Get current auth user".equals(info.attr().request);
			});
			afterCallCount++;
			afterCallSawResponse = runtime.ctx() != null && runtime.ctx().response() != null;
		}
	}

	@JPostman.TestNG
	private static final class RuntimeProceedFailureFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> runtime;

		private int requestExecutions;
		private int callbackCalls;
		private int responseCallbackCalls;
		private boolean responseCallbackSawSyntheticResponse;
		private boolean continuedAfterFailure;
		private Integer statusCode;
		private boolean syntheticResponse;
		private Class<?> errorType;

		@JPostman.Response(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return () -> {
				requestExecutions++;
				throw new IllegalStateException("simulated runtime.call failure");
			};
		}

		@JPostman.Executor
		public void intercept() {
			runtime.call((test, info) -> callbackCalls++).response((test, info) -> {
				responseCallbackCalls++;
				responseCallbackSawSyntheticResponse = test.response() != null && test.statusCode() == 500
						&& info != null && info.syntheticResponse();
			});
			continuedAfterFailure = true;
			statusCode = runtime.info().statusCode();
			syntheticResponse = runtime.info().syntheticResponse();
			errorType = runtime.info().errorCause() == null ? null : runtime.info().errorCause().getClass();
		}
	}

	@JPostman.TestNG
	private static final class RuntimeProceedConnectFailureFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> runtime;

		private int requestExecutions;
		private boolean continuedAfterFailure;
		private Integer statusCode;
		private boolean syntheticResponse;
		private Class<?> errorCauseType;
		private String responseLog = "";

		@JPostman.Response(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return () -> {
				requestExecutions++;
				throw new IllegalStateException("Failed to execute request",
						new ConnectException("Connection refused"));
			};
		}

		@JPostman.Executor
		public void intercept() {
			runtime.call();
			continuedAfterFailure = true;
			statusCode = runtime.info().statusCode();
			syntheticResponse = runtime.info().syntheticResponse();
			errorCauseType = runtime.info().errorCause() == null ? null : runtime.info().errorCause().getClass();
			responseLog = runtime.ctx().response().log();
		}
	}

	@JPostman.TestNG
	private static final class RuntimeProceedCallFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> runtime;

		private int requestExecutions;
		private int interceptorCalls;
		private int testBodyAfterCallCount;

		@JPostman.Call(request = "Get current auth user", verify = 0)
		@org.testng.annotations.Test
		public void profile() {
			runtime.call();
			testBodyAfterCallCount++;
		}

		@JPostman.Executor
		public ApiExecutor provider() {
			return () -> {
				requestExecutions++;
				return new ApiResponse(200, "{\"message\":\"ok\"}", "{\"message\":\"ok\"}".getBytes(), Map.of());
			};
		}

		@JPostman.Executor
		public void intercept() {
			interceptorCalls++;
			runtime.call();
		}
	}

	@JPostman.TestNG
	private static final class RunnerPerRequestInterceptorFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-runner-per-request-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int requestExecutions;
		private int interceptorCalls;
		private final List<String> interceptedRequests = new ArrayList<>();

		@JPostman.Runner(folder = "Product", verify = 0)
		@org.testng.annotations.Test
		public void runProductFolder() {
		}

		@JPostman.Executor
		public ApiExecutor executor() {
			return () -> {
				requestExecutions++;
				return new ApiResponse(200, "{\"message\":\"ok\"}", "{\"message\":\"ok\"}".getBytes(), Map.of());
			};
		}

		@JPostman.Executor
		public void intercept(JPostman.Test test, JPostman.Info info) {
			interceptorCalls++;
			interceptedRequests.add(info.attr().request);
		}
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
	private static final class PreExecutionFailureFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int globalInterceptCalls;
		private int interceptCalls;
		private int providerCalls;
		private boolean interceptorRanBeforeExecution;
		private boolean responseExecutionStarted;
		private String interceptedNamespace;
		private String interceptedRequest;

		@JPostman.Response(namespace = "test", request = "Get current auth user", verify = 500)
		@org.testng.annotations.Test
		public void profile() {
		}

		@JPostman.Executor(namespace = "test")
		public void defaultIntercept(JPostman.Test test, JPostman.Info info) {
			interceptCalls++;
			interceptorRanBeforeExecution = !responseExecutionStarted;
			interceptedNamespace = info.attr().namespace;
			interceptedRequest = info.attr().request;
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			providerCalls++;
			return () -> {
				responseExecutionStarted = true;
				throw new IllegalStateException("simulated execution failure");
			};
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
