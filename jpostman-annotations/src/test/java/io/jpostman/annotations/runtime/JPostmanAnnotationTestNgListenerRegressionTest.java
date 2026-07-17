package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.testng.IHookCallBack;
import org.testng.IInvokedMethod;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.ConstructorOrMethod;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotationListener;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for TestNG hook behavior when annotation response
 * execution fails before the user test body runs.
 */
public class JPostmanAnnotationTestNgListenerRegressionTest {

	@Test
	public void listenerDoesNotRunTestBodyAfterHardAnnotationVerificationFailure() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		VerificationFailureBodyFixture fixture = new VerificationFailureBodyFixture();
		Method method = VerificationFailureBodyFixture.class.getDeclaredMethod("printContextAfterFailure");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(0, fixture.bodyCalls, "A hard status verification failure must stop before the user test body.");
		assertTrue(!fixture.sawResponse,
				"The body did not run, so it must not observe the response through user code.");
		assertEquals(ITestResult.FAILURE, status.get());
		assertNotNull(throwable.get());
		assertTrue(throwable.get().getMessage().contains("Status code mismatch: expected [401] but found [200]"),
				"Actual message: " + throwable.get().getMessage());
	}

	@Test
	public void listenerRunsTestBodyWhenAnnotationVerificationIsSoft() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		SoftVerificationBodyFixture fixture = new SoftVerificationBodyFixture();
		Method method = SoftVerificationBodyFixture.class.getDeclaredMethod("inspectResponseAfterSoftVerification");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(1, fixture.bodyCalls, "soft=true must allow the user test body to inspect the executed response.");
		assertTrue(fixture.sawResponse, "The soft response body should see the prepared response.");
		assertEquals(ITestResult.FAILURE, status.get(),
				"The collected soft verification must be flushed after the same method body exits.");
		assertNotNull(throwable.get());
		assertTrue(throwable.get().getMessage().contains("method=inspectResponseAfterSoftVerification"),
				"Actual message: " + throwable.get().getMessage());
		assertTrue(throwable.get().getMessage().contains("Status code mismatch: expected [401] but found [200]"),
				"Actual message: " + throwable.get().getMessage());
	}

	@Test
	public void automaticSoftVerificationIsResetBeforeTheNextTestMethod() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		SoftVerificationBodyFixture fixture = new SoftVerificationBodyFixture();

		Method failingMethod = SoftVerificationBodyFixture.class
				.getDeclaredMethod("inspectResponseAfterSoftVerification");
		AtomicReference<Throwable> firstThrowable = new AtomicReference<>();
		AtomicInteger firstStatus = new AtomicInteger(ITestResult.SUCCESS);
		listener.run(hookCallBack(() -> invoke(fixture, failingMethod)),
				testResult(fixture, failingMethod, firstThrowable, firstStatus));

		Method passingMethod = SoftVerificationBodyFixture.class
				.getDeclaredMethod("inspectResponseAfterSuccessfulSoftVerification");
		AtomicReference<Throwable> secondThrowable = new AtomicReference<>();
		AtomicInteger secondStatus = new AtomicInteger(ITestResult.SUCCESS);
		listener.run(hookCallBack(() -> invoke(fixture, passingMethod)),
				testResult(fixture, passingMethod, secondThrowable, secondStatus));

		assertEquals(ITestResult.FAILURE, firstStatus.get());
		assertEquals(ITestResult.SUCCESS, secondStatus.get(),
				"The first method's soft failure must be consumed and reset at method exit.");
		assertEquals(null, secondThrowable.get());
		assertEquals(2, fixture.bodyCalls);
	}

	@Test
	public void injectedAssertVerifyFlushesAndResetsAutomaticSoftStatusVerification() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		SoftVerifyResetFixture fixture = new SoftVerifyResetFixture();
		Method method = SoftVerifyResetFixture.class.getDeclaredMethod("verifyCreatedResponse");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(1, fixture.bodyCalls);
		assertEquals(ITestResult.SUCCESS, status.get());
		assertEquals(null, throwable.get(),
				"asserts.verify() must flush the existing verify=201 soft collector instead of creating a hard default-200 assertion.");
	}

	@Test
	public void listenerDoesNotRunResponseBodyWhenRequestPreparationFails() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		RequestPreparationFailureFixture fixture = new RequestPreparationFailureFixture();
		Method method = RequestPreparationFailureFixture.class.getDeclaredMethod("missingRequest");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(0, fixture.bodyCalls, "The response body must not run when no request/response was prepared.");
		assertEquals(ITestResult.FAILURE, status.get());
		assertNotNull(throwable.get());
		assertTrue(throwable.get().getMessage().contains("Request not found"),
				"Actual message: " + throwable.get().getMessage());
	}

	@Test
	public void listenerDoesNotRunRunnerBodyAfterRunnerStatusFailure() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		RunnerStatusFailureFixture fixture = new RunnerStatusFailureFixture();
		Method method = RunnerStatusFailureFixture.class.getDeclaredMethod("runProducts");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(0, fixture.bodyCalls,
				"Runner body should not run after a fail-fast runner status verification failure.");
		assertEquals(ITestResult.FAILURE, status.get());
		assertNotNull(throwable.get());
		assertTrue(throwable.get().getMessage().contains("Status code mismatch: expected [400] but found [200]"),
				"Actual message: " + throwable.get().getMessage());
		assertTrue(!throwable.get().getMessage().contains("Error1"),
				"Runner body local assertions should not replace the status failure: " + throwable.get().getMessage());
	}

	@Test
	public void listenerRunsTestBodyAfterSuccessfulAnnotationRunnerForDiagnostics() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		SuccessfulRunnerBodyFixture fixture = new SuccessfulRunnerBodyFixture();
		Method method = SuccessfulRunnerBodyFixture.class.getDeclaredMethod("printLatestRunnerInfo");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(2, fixture.bodyCalls,
				"The user test body should run after each successful JPostman runner request.");
		assertEquals(2, fixture.executorCalls, "The runner should execute each discovered collection request.");
		assertEquals(List.of("Login user and get tokens", "Get current auth user"), fixture.bodyRequests,
				"Each body call should see the current runner request info, not only the final request.");
		assertTrue(fixture.sawLatestInfo,
				"The user test body should see the current runner JPostmanInfo after each request.");
		assertTrue(fixture.sawLatestResponse,
				"The user test body should see the latest active response context after each request.");
		assertEquals(ITestResult.SUCCESS, status.get());
		assertEquals(null, throwable.get());
	}

	@Test
	public void successfulFrameworkOwnedRunnerTransitionsStartedResultToSuccess() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		SuccessfulRunnerBodyFixture fixture = new SuccessfulRunnerBodyFixture();
		Method method = SuccessfulRunnerBodyFixture.class.getDeclaredMethod("printLatestRunnerInfo");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.STARTED);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(ITestResult.SUCCESS, status.get(),
				"A successful IHookable runner must not remain STARTED after framework execution.");
		assertEquals(null, throwable.get());
	}

	@Test
	public void listenerInvokesReusableRunnerLauncherThroughTestNgHook() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		ReusableRunnerLauncherFixture fixture = new ReusableRunnerLauncherFixture();
		Method method = ReusableRunnerLauncherFixture.class.getDeclaredMethod("testProductsRunner2");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		AtomicInteger hookCalls = new AtomicInteger();
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> {
			hookCalls.incrementAndGet();
			invoke(fixture, method);
		}), result);

		assertEquals(1, hookCalls.get(), "The reusable-runner launcher must execute through TestNG's IHookCallBack.");
		assertEquals(1, fixture.reusableBodyCalls);
		assertEquals(1, fixture.launcherBodyCalls);
		assertEquals(1, fixture.executorCalls);
		assertEquals(ITestResult.SUCCESS, status.get());
		assertEquals(null, throwable.get());
	}

	@Test
	public void listenerKeepsRuntimeCallErrorTraceBeyondUserFrame() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		RuntimeCallErrorTraceFixture fixture = new RuntimeCallErrorTraceFixture();
		Method method = RuntimeCallErrorTraceFixture.class.getDeclaredMethod("newMouseProduct2");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> {
			throwable.set(JPostmanRuntimeCall.clean(runtimeCallAssertionFailure(method)));
			status.set(ITestResult.FAILURE);
		}), result);

		Throwable cleaned = throwable.get();

		assertEquals(ITestResult.FAILURE, status.get());
		assertNotNull(cleaned);
		assertTrue(cleaned instanceof AssertionError);
		assertTrue(cleaned.getMessage().contains("@JPostmanCall"), "Actual message: " + cleaned.getMessage());
		assertTrue(cleaned.getStackTrace().length >= 2, stackTraceText(cleaned));
		assertEquals(RuntimeCallErrorTraceFixture.class.getName(), cleaned.getStackTrace()[0].getClassName());
		assertEquals("newMouseProduct2", cleaned.getStackTrace()[0].getMethodName());
	}

	@Test
	public void junitFailureCleanupKeepsRuntimeCallAssertionLineAfterProxyCleanup() throws Exception {
		RuntimeCallErrorTraceFixture fixture = new RuntimeCallErrorTraceFixture();
		Method method = RuntimeCallErrorTraceFixture.class.getDeclaredMethod("newMouseProduct2");

		try {
			JPostmanRuntimeCall.register(fixture, TestNgContext.class, action -> null,
					error -> JPostmanAnnotationEngine.cleanRuntimeFailure(fixture, method, error, "error"));

			Throwable proxyCleaned = JPostmanRuntimeCall.clean(runtimeCallAssertionFailure(method));
			Throwable junitCleaned = JPostmanAnnotationEngine.cleanJUnitFailure(fixture, method, proxyCleaned);

			assertTrue(junitCleaned instanceof AssertionError);
			assertTrue(junitCleaned.getMessage().contains("@JPostmanCall"),
					"Actual message: " + junitCleaned.getMessage());
			assertTrue(junitCleaned.getStackTrace().length >= 2, stackTraceText(junitCleaned));
			assertEquals(RuntimeCallErrorTraceFixture.class.getName(), junitCleaned.getStackTrace()[0].getClassName());
			assertEquals("newMouseProduct2", junitCleaned.getStackTrace()[0].getMethodName());
			assertEquals(123, junitCleaned.getStackTrace()[0].getLineNumber());
		} finally {
			JPostmanRuntimeCall.clear(fixture, TestNgContext.class);
		}
	}

	@JPostman.TestNG
	private static final class SoftVerifyResetFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		private int bodyCalls;

		@JPostman.Response(request = "Get current auth user", verify = 201, soft = true, log = "none")
		@org.testng.annotations.Test
		public void verifyCreatedResponse() {
			bodyCalls++;
			asserts.verify();
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			return () -> new ApiResponse(201, "{\"id\":1}", "{\"id\":1}".getBytes(), Map.of());
		}
	}

	@JPostman.TestNG
	private static final class RequestPreparationFailureFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int bodyCalls;

		@JPostman.Response(request = "Missing request", verify = 0)
		@org.testng.annotations.Test
		public void missingRequest() {
			bodyCalls++;
		}
	}

	@JPostman.TestNG
	private static final class VerificationFailureBodyFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 401)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int bodyCalls;
		private boolean sawResponse;

		@JPostman.Response(request = "Get current auth user")
		@org.testng.annotations.Test
		public void printContextAfterFailure() {
			bodyCalls++;
			sawResponse = jpostman.ctx().response() != null;
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{\"id\":1,\"firstName\":\"Emily\"}");
		}
	}

	@JPostman.TestNG
	private static final class SoftVerificationBodyFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 0)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int bodyCalls;
		private boolean sawResponse;

		@JPostman.Response(request = "Get current auth user", verify = 401, soft = true)
		@org.testng.annotations.Test
		public void inspectResponseAfterSoftVerification() {
			bodyCalls++;
			sawResponse = jpostman.ctx().response() != null;
		}

		@JPostman.Response(request = "Get current auth user", verify = 200, soft = true)
		@org.testng.annotations.Test
		public void inspectResponseAfterSuccessfulSoftVerification() {
			bodyCalls++;
			sawResponse = jpostman.ctx().response() != null;
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{\"id\":1,\"firstName\":\"Emily\"}");
		}
	}

	@JPostman.TestNG
	private static final class SuccessfulRunnerBodyFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int executorCalls;
		private int bodyCalls;
		private final List<String> bodyRequests = new ArrayList<>();
		private boolean sawLatestInfo;
		private boolean sawLatestResponse;

		@JPostman.Runner(verify = 0)
		@org.testng.annotations.Test
		public void printLatestRunnerInfo() {
			bodyCalls++;
			JPostmanInfo info = jpostman.info().attr();
			if (info != null) {
				bodyRequests.add(info.attr().request);
			}
			sawLatestInfo = info != null && info.methods.contains("defaultExecutor(\"" + info.request + "\")");
			sawLatestResponse = jpostman.ctx() != null && jpostman.ctx().response() != null;
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			executorCalls++;
			return okExecutor("{\"id\":" + executorCalls + ",\"request\":\"" + info.request + "\"}");
		}
	}

	@JPostman.TestNG
	private static final class ReusableRunnerLauncherFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int reusableBodyCalls;
		private int launcherBodyCalls;
		private int executorCalls;

		@JPostman.Runner(id = "runner1", include = "Login user and get tokens", verify = 0)
		@org.testng.annotations.Test
		public void testProductsRunner1() {
			reusableBodyCalls++;
		}

		@JPostman.Runner(dependsOn = "#runner1")
		@org.testng.annotations.Test
		public void testProductsRunner2() {
			launcherBodyCalls++;
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			executorCalls++;
			return okExecutor("{\"id\":1}");
		}
	}

	@JPostman.TestNG
	private static final class RunnerStatusFailureFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json")
		private JPostman.Runtime<JPostman.Test> jpostman;

		private int bodyCalls;

		@JPostman.Runner(verify = 400, enabled = true, soft = false)
		@org.testng.annotations.Test
		public void runProducts() {
			bodyCalls++;
			JPostman.Assert asserts = jpostman.ctx().soft();
			asserts.isTrue(false, "Error1");
			asserts.verify();
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{\"id\":1}");
		}
	}

	@JPostman.TestNG
	private static final class RuntimeCallErrorTraceFixture {

		@JPostman.Context(config = "", collection = "classpath:annotation-test-collection.json", logs = "debug", debug = "none")
		private JPostman.Runtime<TestNgContext> jpostman;

		@JPostman.Call(tags = { "mouse", "product=mouse" }, log = "error")
		@org.testng.annotations.Test
		public void newMouseProduct2() {
			// The hook callback supplies the runtime assertion failure for this regression.
		}
	}

	private static AssertionError runtimeCallAssertionFailure(Method testMethod) {
		AssertionError error = new AssertionError("Status code mismatch: expected [200] but found [201]");
		error.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("io.jpostman.secure.JPostmanAssertionError", "wrap",
						"JPostmanAssertionError.java", 52),
				new StackTraceElement("io.jpostman.testng.TestNgAssertions", "wrap", "TestNgAssertions.java", 430),
				new StackTraceElement("io.jpostman.testng.TestNgAssertions", "assertWithLog", "TestNgAssertions.java",
						418),
				new StackTraceElement("io.jpostman.testng.TestNgAssertions", "statusCode", "TestNgAssertions.java",
						200),
				new StackTraceElement(testMethod.getDeclaringClass().getName(), testMethod.getName(),
						testMethod.getDeclaringClass().getSimpleName() + ".java", 123),
				new StackTraceElement("jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0",
						"NativeMethodAccessorImpl.java", -2),
				new StackTraceElement("jdk.internal.reflect.NativeMethodAccessorImpl", "invoke",
						"NativeMethodAccessorImpl.java", 77),
				new StackTraceElement("jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke",
						"DelegatingMethodAccessorImpl.java", 43),
				new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 568),
				new StackTraceElement("org.testng.internal.invokers.MethodInvocationHelper", "invokeMethod",
						"MethodInvocationHelper.java", 141),
				new StackTraceElement("org.testng.remote.RemoteTestNG", "main", "RemoteTestNG.java", 91) });
		return error;
	}

	private static String stackTraceText(Throwable throwable) {
		StringBuilder result = new StringBuilder("Expected at least 2 cleaned stack frames but got ")
				.append(throwable == null ? 0 : throwable.getStackTrace().length).append(':');
		if (throwable != null) {
			for (StackTraceElement element : throwable.getStackTrace()) {
				result.append(System.lineSeparator()).append("\tat ").append(element);
			}
		}
		return result.toString();
	}

	private static IHookCallBack hookCallBack(Runnable body) {
		return (IHookCallBack) Proxy.newProxyInstance(
				JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
				new Class<?>[] { IHookCallBack.class }, (proxy, method, args) -> {
					if ("runTestMethod".equals(method.getName())) {
						body.run();
						return null;
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static ITestResult testResult(Object instance, Method javaMethod, AtomicReference<Throwable> throwable,
			AtomicInteger status) {
		return (ITestResult) Proxy.newProxyInstance(
				JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
				new Class<?>[] { ITestResult.class }, (proxy, method, args) -> {
					if ("getInstance".equals(method.getName())) {
						return instance;
					}
					if ("getMethod".equals(method.getName())) {
						return testNgMethod(javaMethod);
					}
					if ("getThrowable".equals(method.getName())) {
						return throwable.get();
					}
					if ("setThrowable".equals(method.getName())) {
						throwable.set((Throwable) args[0]);
						return null;
					}
					if ("getStatus".equals(method.getName())) {
						return status.get();
					}
					if ("setStatus".equals(method.getName())) {
						status.set((Integer) args[0]);
						return null;
					}
					return defaultValue(method.getReturnType());
				});
	}

	@SuppressWarnings("unused")
	private static IInvokedMethod invokedMethod(Method javaMethod, boolean testMethod, boolean configurationMethod) {
		ITestNGMethod testNgMethod = testNgMethod(javaMethod);
		return (IInvokedMethod) Proxy.newProxyInstance(
				JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
				new Class<?>[] { IInvokedMethod.class }, (proxy, method, args) -> {
					if ("isTestMethod".equals(method.getName())) {
						return testMethod;
					}
					if ("isConfigurationMethod".equals(method.getName())) {
						return configurationMethod;
					}
					if ("getTestMethod".equals(method.getName())) {
						return testNgMethod;
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static ITestNGMethod testNgMethod(Method javaMethod) {
		return (ITestNGMethod) Proxy.newProxyInstance(
				JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
				new Class<?>[] { ITestNGMethod.class }, (proxy, method, args) -> {
					if ("getRealClass".equals(method.getName())) {
						return javaMethod.getDeclaringClass();
					}
					if ("getConstructorOrMethod".equals(method.getName())) {
						return new ConstructorOrMethod(javaMethod);
					}
					if ("getMethodName".equals(method.getName())) {
						return javaMethod.getName();
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static void invoke(Object target, Method method) {
		try {
			method.setAccessible(true);
			method.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == char.class) {
			return (char) 0;
		}
		return null;
	}
}
