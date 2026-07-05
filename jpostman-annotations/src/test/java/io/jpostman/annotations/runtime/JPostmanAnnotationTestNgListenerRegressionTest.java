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
	public void listenerRunsTestBodyAfterAnnotationVerificationFailureForDiagnostics() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();
		VerificationFailureBodyFixture fixture = new VerificationFailureBodyFixture();
		Method method = VerificationFailureBodyFixture.class.getDeclaredMethod("printContextAfterFailure");
		AtomicReference<Throwable> throwable = new AtomicReference<>();
		AtomicInteger status = new AtomicInteger(ITestResult.SUCCESS);
		ITestResult result = testResult(fixture, method, throwable, status);

		listener.run(hookCallBack(() -> invoke(fixture, method)), result);

		assertEquals(1, fixture.bodyCalls, "The user test body should still run so it can print the prepared context.");
		assertTrue(fixture.sawResponse,
				"The user test body should see the response created before verification failed.");
		assertEquals(ITestResult.FAILURE, status.get());
		assertNotNull(throwable.get());
		assertTrue(throwable.get().getMessage().contains("Status code mismatch: expected [401] but found [200]"),
				"Actual message: " + throwable.get().getMessage());
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
