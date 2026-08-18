package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.junit.JPostmanJUnitExtension;

/**
 * Regression coverage for standalone JUnit Response methods that return cache
 * values.
 */
public class JPostmanJUnitExternalResponseRegressionTest {

	@Test
	public void returningResponseRunsExternallyAndCachesValue() throws Exception {
		Fixture fixture = new Fixture();
		JPostmanAnnotationEngine.setupJUnit(fixture);

		Method response = Fixture.class.getDeclaredMethod("getAccessToken");
		JPostmanAnnotationEngine.runJUnitExternalResponse(fixture, response);

		assertEquals(1, fixture.responseMethodCalls);
		assertEquals("token-123", fixture.api.cache("accessToken"));
		JPostmanReport report = (JPostmanReport) fixture.report;
		assertEquals(1, report.total());
		assertNotNull(report.execution("getAccessToken"));
	}

	@Test
	public void voidResponseCachesCompletedResponseSnapshot() throws Exception {
		VoidFixture fixture = new VoidFixture();
		JPostmanAnnotationEngine.setupJUnit(fixture);

		Method response = VoidFixture.class.getDeclaredMethod("getAccessToken");
		JPostmanAnnotationEngine.runJUnitExternalResponse(fixture, response);

		assertEquals(1, fixture.responseMethodCalls);
		Object cached = fixture.api.cache("accessToken");
		assertNotNull(cached);
		assertEquals("token-123", ((JPostman.Test) cached).path("accessToken"));
		JPostmanReport report = (JPostmanReport) fixture.report;
		assertEquals(1, report.total());
		assertNotNull(report.execution("getAccessToken"));
	}

	@Test
	public void junitExtensionLeavesResponseTestBodyToJUnit() throws Throwable {
		FailureAttributionFixture fixture = new FailureAttributionFixture();
		JPostmanJUnitExtension extension = new JPostmanJUnitExtension();
		AtomicInteger proceedCalls = new AtomicInteger();

		Method response = FailureAttributionFixture.class.getDeclaredMethod("getAccessToken");
		extension.interceptTestMethod(invocation(proceedCalls), reflectiveInvocation(response),
				extensionContext(fixture));

		assertEquals(1, proceedCalls.get(), "JPostman must leave normal void @Response @Test invocation to JUnit.");
		assertEquals(0, fixture.passingResponseCalls,
				"The test proxy does not invoke the Java body; JPostman must not invoke it externally.");
		assertEquals(0, fixture.failingResponseCalls, "Other Response tests must not be pre-executed as a group.");
	}

	@JPostman.JUnit
	private static final class Fixture {

		@JPostman.Context(config = "", collection = "classpath:junit-external-response-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.TestContext
		private JPostman.Test api;

		@JPostman.ReportContext(details = true)
		private JPostman.Report report;

		private int responseMethodCalls;

		@JPostman.Response(id = "auth", request = "Login", cache = "accessToken")
		@Test
		public String getAccessToken() {
			responseMethodCalls++;
			return runtime.test().path("accessToken");
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			return () -> {
				String json = "{\"accessToken\":\"token-123\"}";
				return new ApiResponse(200, json, json.getBytes(), Map.of());
			};
		}
	}

	@JPostman.JUnit
	private static final class VoidFixture {

		@JPostman.Context(config = "", collection = "classpath:junit-external-response-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.TestContext
		private JPostman.Test api;

		@JPostman.ReportContext(details = true)
		private JPostman.Report report;

		private int responseMethodCalls;

		@JPostman.Response(id = "auth", request = "Login", cache = "accessToken")
		@Test
		public void getAccessToken() {
			responseMethodCalls++;
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			return () -> {
				String json = "{\"accessToken\":\"token-123\"}";
				return new ApiResponse(200, json, json.getBytes(), Map.of());
			};
		}
	}

	@JPostman.JUnit
	private static final class FailureAttributionFixture {

		@JPostman.Context(config = "", collection = "classpath:junit-external-response-collection.json", verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		@JPostman.ReportContext(details = true)
		private JPostman.Report report;

		private int passingResponseCalls;
		private int failingResponseCalls;

		@JPostman.Response(request = "Login")
		@Test
		public void getAccessToken() {
			passingResponseCalls++;
		}

		@JPostman.Response(request = "Login")
		@Test
		public void refreshAuthSessionToken() {
			failingResponseCalls++;
			throw new AssertionError("beta response failure");
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor() {
			return () -> {
				String json = "{\"accessToken\":\"token-123\"}";
				return new ApiResponse(200, json, json.getBytes(), Map.of());
			};
		}
	}

	@SuppressWarnings("unchecked")
	private static Invocation<Void> invocation(AtomicInteger proceedCalls) {
		return (Invocation<Void>) Proxy.newProxyInstance(
				JPostmanJUnitExternalResponseRegressionTest.class.getClassLoader(), new Class<?>[] { Invocation.class },
				(proxy, method, args) -> {
					if ("proceed".equals(method.getName())) {
						proceedCalls.incrementAndGet();
						return null;
					}
					return defaultValue(method.getReturnType());
				});
	}

	@SuppressWarnings("unchecked")
	private static ReflectiveInvocationContext<Method> reflectiveInvocation(Method javaMethod) {
		return (ReflectiveInvocationContext<Method>) Proxy.newProxyInstance(
				JPostmanJUnitExternalResponseRegressionTest.class.getClassLoader(),
				new Class<?>[] { ReflectiveInvocationContext.class }, (proxy, method, args) -> {
					if ("getExecutable".equals(method.getName())) {
						return javaMethod;
					}
					if ("getArguments".equals(method.getName())) {
						return List.of();
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static ExtensionContext extensionContext(Object instance) {
		return (ExtensionContext) Proxy.newProxyInstance(
				JPostmanJUnitExternalResponseRegressionTest.class.getClassLoader(),
				new Class<?>[] { ExtensionContext.class }, (proxy, method, args) -> {
					if ("getRequiredTestInstance".equals(method.getName())) {
						return instance;
					}
					if ("getTestInstance".equals(method.getName())) {
						return Optional.of(instance);
					}
					if ("getRequiredTestClass".equals(method.getName())) {
						return instance.getClass();
					}
					if ("getTestClass".equals(method.getName())) {
						return Optional.of(instance.getClass());
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static Object defaultValue(Class<?> type) {
		if (type == null || !type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return '\0';
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
		return null;
	}

}
