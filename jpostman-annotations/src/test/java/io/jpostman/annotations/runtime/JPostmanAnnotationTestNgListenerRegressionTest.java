package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
		assertTrue(fixture.sawResponse, "The user test body should see the response created before verification failed.");
		assertEquals(ITestResult.FAILURE, status.get());
		assertNotNull(throwable.get());
		assertTrue(throwable.get().getMessage().contains("Status code mismatch: expected [401] but found [200]"),
				"Actual message: " + throwable.get().getMessage());
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

	private static IHookCallBack hookCallBack(Runnable body) {
		return (IHookCallBack) Proxy.newProxyInstance(JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
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
		return (ITestResult) Proxy.newProxyInstance(JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
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
		return (IInvokedMethod) Proxy.newProxyInstance(JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
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
		return (ITestNGMethod) Proxy.newProxyInstance(JPostmanAnnotationTestNgListenerRegressionTest.class.getClassLoader(),
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
