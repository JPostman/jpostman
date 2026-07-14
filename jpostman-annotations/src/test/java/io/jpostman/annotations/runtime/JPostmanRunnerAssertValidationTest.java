package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.junit.JUnitContext;
import io.jpostman.testng.TestNgContext;

public class JPostmanRunnerAssertValidationTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";

	@Test
	public void junitRunnerVerifyOverrideFailsFastOnStatusMismatch() throws Exception {
		JUnitRunnerVerifyOverrideFixture fixture = new JUnitRunnerVerifyOverrideFixture();
		Method method = JUnitRunnerVerifyOverrideFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertStatusOnlyFailure(error, "400");
	}

	@Test
	public void junitRunnerUsesContextVerifyStatusWhenVerifyDefault() throws Exception {
		JUnitContextVerifyDefaultFixture fixture = new JUnitContextVerifyDefaultFixture();
		Method method = JUnitContextVerifyDefaultFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertStatusOnlyFailure(error, "400");
	}

	@Test
	public void junitRunnerVerifyZeroAssertContextFailsFastWithoutStatusCheck() throws Exception {
		JUnitAssertContextHardRunnerFixture fixture = new JUnitAssertContextHardRunnerFixture();
		Method method = JUnitAssertContextHardRunnerFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertHardAssertionOnlyFailure(error, "Error1 ==> expected: <true> but was: <false>");
	}

	@Test
	public void junitRunnerVerifyZeroRuntimeHardAssertFailsFastWithoutStatusCheck() throws Exception {
		JUnitRuntimeHardAssertRunnerFixture fixture = new JUnitRuntimeHardAssertRunnerFixture();
		Method method = JUnitRuntimeHardAssertRunnerFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertHardAssertionOnlyFailure(error, "Error1 ==> expected: <true> but was: <false>");
	}

	@Test
	public void junitRunnerLocalSoftAssertReportsMultipleRequestFailures() throws Exception {
		JUnitLocalSoftFixture fixture = new JUnitLocalSoftFixture();
		Method method = JUnitLocalSoftFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 ==> expected: <true> but was: <false>",
				"Error2 ==> expected: <true> but was: <false>");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
	}

	@Test
	public void junitRunnerHardModeLocalSoftAssertVerifiesCollectedFailures() throws Exception {
		JUnitLocalSoftHardRunnerFixture fixture = new JUnitLocalSoftHardRunnerFixture();
		Method method = JUnitLocalSoftHardRunnerFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 ==> expected: <true> but was: <false>",
				"Error2 ==> expected: <true> but was: <false>");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
	}

	@Test
	public void junitRunnerAssertContextReportsMultipleRequestFailures() throws Exception {
		JUnitAssertContextFixture fixture = new JUnitAssertContextFixture();
		Method method = JUnitAssertContextFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 ==> expected: <true> but was: <false>",
				"Error2 ==> expected: <true> but was: <false>");
		assertRunnerFailureFormat(error);
	}

	@Test
	public void junitRunnerLocalSoftAndVerifyStatusFailuresAreBothReportedInSoftMode() throws Exception {
		JUnitLocalSoftVerifyStatusFixture fixture = new JUnitLocalSoftVerifyStatusFixture();
		Method method = JUnitLocalSoftVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 ==> expected: <true> but was: <false>",
				"request=Login user and get tokens", "Status code mismatch",
				"Error2 ==> expected: <true> but was: <false>", "request=Get current auth user",
				"Status code mismatch");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
		assertEquals(2, countOccurrences(error.getMessage(), "Status code mismatch"));
		assertTrue(error.getMessage().contains("404"));
	}

	@Test
	public void junitRunnerLocalSoftVerifyOverrideDoesNotFlushDefaultStatusNoise() throws Exception {
		JUnitLocalSoftCreatedVerifyStatusFixture fixture = new JUnitLocalSoftCreatedVerifyStatusFixture();
		Method method = JUnitLocalSoftCreatedVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		String message = error.getMessage();
		assertRunnerFailureFormat(error);
		assertEquals(2, countOccurrences(message, "Status code mismatch"), message);
		assertTrue(message.contains("404"), message);
		assertTrue(message.contains("201"), message);
		assertFalse(message.contains("<200>"), message);
		assertFalse(message.contains("[200]"), message);
	}

	@Test
	public void junitRunnerAssertContextAndVerifyStatusFailuresAreBothReportedInSoftMode() throws Exception {
		JUnitAssertContextVerifyStatusFixture fixture = new JUnitAssertContextVerifyStatusFixture();
		Method method = JUnitAssertContextVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 ==> expected: <true> but was: <false>",
				"request=Login user and get tokens", "Status code mismatch",
				"Error2 ==> expected: <true> but was: <false>", "request=Get current auth user",
				"Status code mismatch");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
		assertEquals(2, countOccurrences(error.getMessage(), "Status code mismatch"));
		assertTrue(error.getMessage().contains("404"));
	}

	@Test
	public void junitRunnerAssertContextVerifyOverrideDoesNotFlushDefaultStatusNoise() throws Exception {
		JUnitAssertContextCreatedVerifyStatusFixture fixture = new JUnitAssertContextCreatedVerifyStatusFixture();
		Method method = JUnitAssertContextCreatedVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runJUnit(fixture, method, () -> invokeUnchecked(fixture, method)));

		String message = error.getMessage();
		assertRunnerFailureFormat(error);
		assertEquals(2, countOccurrences(message, "Status code mismatch"), message);
		assertTrue(message.contains("404"), message);
		assertTrue(message.contains("201"), message);
		assertFalse(message.contains("<200>"), message);
		assertFalse(message.contains("[200]"), message);
	}

	@Test
	public void testNgRunnerVerifyOverrideFailsFastOnStatusMismatch() throws Exception {
		TestNgRunnerVerifyOverrideFixture fixture = new TestNgRunnerVerifyOverrideFixture();
		Method method = TestNgRunnerVerifyOverrideFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertStatusOnlyFailure(error, "400");
	}

	@Test
	public void testNgRunnerUsesContextVerifyStatusWhenVerifyDefault() throws Exception {
		TestNgContextVerifyDefaultFixture fixture = new TestNgContextVerifyDefaultFixture();
		Method method = TestNgContextVerifyDefaultFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertStatusOnlyFailure(error, "400");
	}

	@Test
	public void testNgRunnerVerifyZeroAssertContextFailsFastWithoutStatusCheck() throws Exception {
		TestNgAssertContextHardRunnerFixture fixture = new TestNgAssertContextHardRunnerFixture();
		Method method = TestNgAssertContextHardRunnerFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertHardAssertionOnlyFailure(error, "Error1 expected [true] but found [false]");
	}

	@Test
	public void testNgRunnerVerifyZeroRuntimeHardAssertFailsFastWithoutStatusCheck() throws Exception {
		TestNgRuntimeHardAssertRunnerFixture fixture = new TestNgRuntimeHardAssertRunnerFixture();
		Method method = TestNgRuntimeHardAssertRunnerFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertHardAssertionOnlyFailure(error, "Error1 expected [true] but found [false]");
	}

	@Test
	public void testNgRunnerLocalSoftAssertReportsMultipleRequestFailures() throws Exception {
		TestNgLocalSoftFixture fixture = new TestNgLocalSoftFixture();
		Method method = TestNgLocalSoftFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeReflective(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 expected [true] but found [false]",
				"Error2 expected [true] but found [false]");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
	}

	@Test
	public void testNgRunnerHardModeLocalSoftAssertVerifiesCollectedFailures() throws Exception {
		TestNgLocalSoftHardRunnerFixture fixture = new TestNgLocalSoftHardRunnerFixture();
		Method method = TestNgLocalSoftHardRunnerFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 expected [true] but found [false]",
				"Error2 expected [true] but found [false]");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
	}

	@Test
	public void testNgRunnerHardModeLocalSoftAssertIgnoresPendingStatusVerifyZero() throws Exception {
		TestNgLocalSoftHardRunnerStatusFixture fixture = new TestNgLocalSoftHardRunnerStatusFixture();
		Method method = TestNgLocalSoftHardRunnerStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 expected [true] but found [false]",
				"Error2 expected [true] but found [false]");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
		assertFalse(error.getMessage().contains("Status code mismatch"));
	}

	@Test
	public void testNgRunnerLocalSoftAndVerifyStatusFailuresAreBothReportedInSoftMode() throws Exception {
		TestNgLocalSoftVerifyStatusFixture fixture = new TestNgLocalSoftVerifyStatusFixture();
		Method method = TestNgLocalSoftVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 expected [true] but found [false]",
				"request=Login user and get tokens", "Status code mismatch", "Error2 expected [true] but found [false]",
				"request=Get current auth user", "Status code mismatch");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
		assertEquals(2, countOccurrences(error.getMessage(), "Status code mismatch"));
		assertTrue(error.getMessage().contains("404"));
	}

	@Test
	public void testNgRunnerLocalSoftVerifyOverrideDoesNotFlushDefaultStatusNoise() throws Exception {
		TestNgLocalSoftCreatedVerifyStatusFixture fixture = new TestNgLocalSoftCreatedVerifyStatusFixture();
		Method method = TestNgLocalSoftCreatedVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		String message = error.getMessage();
		assertRunnerFailureFormat(error);
		assertEquals(2, countOccurrences(message, "Status code mismatch"), message);
		assertTrue(message.contains("404"), message);
		assertTrue(message.contains("201"), message);
		assertFalse(message.contains("<200>"), message);
		assertFalse(message.contains("[200]"), message);
	}

	@Test
	public void testNgRunnerAssertContextAndVerifyStatusFailuresAreBothReportedInSoftMode() throws Exception {
		TestNgAssertContextVerifyStatusFixture fixture = new TestNgAssertContextVerifyStatusFixture();
		Method method = TestNgAssertContextVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 expected [true] but found [false]",
				"request=Login user and get tokens", "Status code mismatch", "Error2 expected [true] but found [false]",
				"request=Get current auth user", "Status code mismatch");
		assertRunnerFailureFormat(error, "JPostmanRunnerAssertValidationTest.java");
		assertEquals(2, countOccurrences(error.getMessage(), "Status code mismatch"));
		assertTrue(error.getMessage().contains("404"));
	}

	@Test
	public void testNgRunnerAssertContextVerifyOverrideDoesNotFlushDefaultStatusNoise() throws Exception {
		TestNgAssertContextCreatedVerifyStatusFixture fixture = new TestNgAssertContextCreatedVerifyStatusFixture();
		Method method = TestNgAssertContextCreatedVerifyStatusFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeUnchecked(fixture, method)));

		String message = error.getMessage();
		assertRunnerFailureFormat(error);
		assertEquals(2, countOccurrences(message, "Status code mismatch"), message);
		assertTrue(message.contains("404"), message);
		assertTrue(message.contains("201"), message);
		assertFalse(message.contains("<200>"), message);
		assertFalse(message.contains("[200]"), message);
	}

	@Test
	public void testNgRunnerAssertContextReportsMultipleRequestFailures() throws Exception {
		TestNgAssertContextFixture fixture = new TestNgAssertContextFixture();
		Method method = TestNgAssertContextFixture.class.getDeclaredMethod("runProducts");

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.runTestNg(fixture, method, () -> invokeReflective(fixture, method)));

		assertContainsInOrder(error.getMessage(), "Error1 expected [true] but found [false]",
				"Error2 expected [true] but found [false]");
		assertRunnerFailureFormat(error);
	}

	@Test
	public void injectedAssertVerifyDelegatesToActiveContextWithoutReplacingSoftAssertions() {
		ResetAwareAssertContext context = new ResetAwareAssertContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context);

		asserts.verify();

		assertEquals(1, context.verifyCalls);
		assertEquals(0, context.assertsCalls);
		assertFalse(context.softPending);
	}

	@Test
	public void injectedAssertVerifyStatusDelegatesToActiveContextAndUsesRequestedStatus() {
		ResetAwareAssertContext context = new ResetAwareAssertContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context);

		asserts.verify(201);

		assertEquals(1, context.verifyCalls);
		assertEquals(201, context.statusCode);
		assertEquals(0, context.assertsCalls);
		assertFalse(context.softPending);
	}

	@Test
	public void localSoftFacadePrefersRunnerFailureOverContextVerifyNoise() {
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> new TestNgNoisySoftContext());
		JPostman.Assert soft = asserts.soft(false);

		JPostmanRuntimeRunner.begin(java.util.List.of("Get all products"));
		try {
			JPostmanRuntimeRunner.request(0, "Get all products");
			soft.isTrue(false, "Error1");

			AssertionError error = assertThrows(AssertionError.class, soft::verify);

			assertTrue(error.getMessage().contains("Error1 expected [true] but found [false]"));
			assertFalse(error.getMessage().contains("Status code mismatch"));
		} finally {
			JPostmanRuntimeRunner.clear();
		}
	}

	@Test
	public void runnerFailureFormatOmitsEclipseJUnitRunnerLocationWhenNoUserFrame() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		AssertionError statusFailure = new AssertionError(
				"(@JPostmanRunner: tags=, namespace=product, folder=Product, request=Sort products, executor=<default>)"
						+ System.lineSeparator() + "Status code mismatch: ==> expected: <400> but was: <200>");
		statusFailure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("org.eclipse.jdt.internal.junit5.runner.JUnit5TestReference", "run",
						"JUnit5TestReference.java", 100),
				new StackTraceElement("org.junit.platform.engine.support.hierarchical.NodeTestTask", "execute",
						"NodeTestTask.java", 142) });

		Method method = JPostmanAnnotationRunner.class.getDeclaredMethod("combinedRunnerError", Object.class,
				java.util.List.class);
		method.setAccessible(true);
		AssertionError error = (AssertionError) method.invoke(runner, new Object(), java.util.List.of(statusFailure));

		String message = error.getMessage();
		assertTrue(message.contains("JPostman runner failed for 1 request."), message);
		assertTrue(message.contains("Status code mismatch"), message);
		assertFalse(message.contains("org.eclipse."), message);
		assertFalse(message.contains("org.junit."), message);
	}

	private static void assertStatusOnlyFailure(AssertionError error, String expectedStatus) {
		String message = error.getMessage();
		assertTrue(message.contains("Status code mismatch"), message);
		assertTrue(message.contains(expectedStatus), message);
		assertFalse(message.contains("JPostman runner failed"), message);
		assertFalse(message.contains("Error1"), message);
	}

	private static void assertHardAssertionOnlyFailure(AssertionError error, String expectedMessage) {
		String message = error.getMessage();
		assertTrue(message.contains(expectedMessage), message);
		assertFalse(message.contains("JPostman runner failed"), message);
		assertFalse(message.contains("Status code mismatch"), message);
	}

	private static void assertRunnerFailureFormat(AssertionError error) {
		assertRunnerFailureFormat(error, null);
	}

	private static void assertRunnerFailureFormat(AssertionError error, String part) {
		String message = error.getMessage();
		assertTrue(message.contains("JPostman runner failed for 2 requests."));
		assertFalse(message.contains("Multiple Failures"));
		assertFalse(message.contains("org.opentest4j.AssertionFailedError"));
		assertFalse(message.contains("java.lang.AssertionError:"));
		assertFalse(message.contains("The following asserts failed:"));
		assertFalse(message.contains("InvocationTargetException"));
		if (part != null && message.contains(part))
			assertTrue(countOccurrences(message, part) >= 1, message);
	}

	private static void applyFirstHardRunnerRule(JPostman.Runtime<JPostman.Test> jpostman, JPostman.Assert asserts) {
		jpostman.runner().has("Login user and get tokens").then(test -> {
			asserts.isTrue(false, "Error1");
		}).has("Get current auth user").then((test, info) -> {
			asserts.isTrue(false, "Error2");
		});
	}

	private static void applyRunnerRules(JPostman.Runtime<JPostman.Test> jpostman, JPostman.Assert asserts) {
		jpostman.runner().has("Login user and get tokens").then(test -> {
			asserts.isTrue(false, "Error1");
		}).has("Get current auth user").then((test, info) -> {
			asserts.isTrue(false, "Error2");
		}).end(test -> {
			asserts.verify();
		});
	}

	private static void invokeUnchecked(Object target, Method method) {
		try {
			method.setAccessible(true);
			method.invoke(target);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void invokeReflective(Object target, Method method) {
		try {
			method.setAccessible(true);
			method.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void assertContainsInOrder(String value, String... parts) {
		int index = 0;
		for (String part : parts) {
			int next = value.indexOf(part, index);
			assertTrue(next >= 0, "Expected to find \"" + part + "\" after index " + index + " in:\n" + value);
			index = next + part.length();
		}
	}

	private static ApiExecutor okExecutor() {
		return () -> new ApiResponse(200, "{}", new byte[0], Map.of());
	}

	private static ApiExecutor createdExecutor() {
		return () -> new ApiResponse(201, "{}", new byte[0], Map.of());
	}

	private static final class JUnitRunnerVerifyOverrideFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 400, enabled = true, soft = false, executor = "#junitVerifyOverride")
		void runProducts() {
			// Status verification should fail before any custom assertions are needed.
		}

		@JPostmanExecutor(id = "junitVerifyOverride")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitContextVerifyDefaultFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 400)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = -1, enabled = true, soft = false, executor = "#junitContextVerifyDefault")
		void runProducts() {
			// Runner verify=-1 should use the context verifyStatusCode value.
		}

		@JPostmanExecutor(id = "junitContextVerifyDefault")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitAssertContextHardRunnerFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 400)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#junitAssertContextHardRunner")
		void runProducts() {
			applyFirstHardRunnerRule(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitAssertContextHardRunner")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitRuntimeHardAssertRunnerFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 400)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#junitRuntimeHardAssertRunner")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().asserts();
			applyFirstHardRunnerRule(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitRuntimeHardAssertRunner")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitLocalSoftFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = true, executor = "#junitLocalSoft")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitLocalSoft")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitAssertContextFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 0, enabled = true, soft = true, executor = "#junitAssertContext")
		void runProducts() {
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitAssertContext")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitLocalSoftHardRunnerFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#junitLocalSoftHardRunner")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitLocalSoftHardRunner")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitLocalSoftVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#junitLocalSoftVerifyStatus")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitLocalSoftVerifyStatus")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitLocalSoftCreatedVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#junitLocalSoftCreatedVerifyStatus")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitLocalSoftCreatedVerifyStatus")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return createdExecutor();
		}
	}

	private static final class JUnitAssertContextVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#junitAssertContextVerifyStatus")
		void runProducts() {
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitAssertContextVerifyStatus")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class JUnitAssertContextCreatedVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#junitAssertContextCreatedVerifyStatus")
		void runProducts() {
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "junitAssertContextCreatedVerifyStatus")
		ApiExecutor executor(JUnitContext context) {
			assertNotNull(context.request());
			return createdExecutor();
		}
	}

	private static final class TestNgRunnerVerifyOverrideFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 400, enabled = true, soft = false, executor = "#testNgVerifyOverride")
		void runProducts() {
			// Status verification should fail before any custom assertions are needed.
		}

		@JPostmanExecutor(id = "testNgVerifyOverride")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgContextVerifyDefaultFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 400)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = -1, enabled = true, soft = false, executor = "#testNgContextVerifyDefault")
		void runProducts() {
			// Runner verify=-1 should use the context verifyStatusCode value.
		}

		@JPostmanExecutor(id = "testNgContextVerifyDefault")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgAssertContextHardRunnerFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 400)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#testNgAssertContextHardRunner")
		void runProducts() {
			applyFirstHardRunnerRule(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgAssertContextHardRunner")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgRuntimeHardAssertRunnerFixture {
		@JPostman.Context(config = "", collection = COLLECTION, verifyStatusCode = 400)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#testNgRuntimeHardAssertRunner")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().asserts();
			applyFirstHardRunnerRule(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgRuntimeHardAssertRunner")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgLocalSoftFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = true, executor = "#testNgLocalSoft")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgLocalSoft")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgAssertContextFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 0, enabled = true, soft = true, executor = "#testNgAssertContext")
		void runProducts() {
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgAssertContext")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgLocalSoftHardRunnerFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#testNgLocalSoftHardRunner")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgLocalSoftHardRunner")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgLocalSoftHardRunnerStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 0, enabled = true, soft = false, executor = "#testNgLocalSoftHardRunnerStatus")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgLocalSoftHardRunnerStatus")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return createdExecutor();
		}
	}

	private static final class TestNgLocalSoftVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#testNgLocalSoftVerifyStatus")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgLocalSoftVerifyStatus")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgLocalSoftCreatedVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#testNgLocalSoftCreatedVerifyStatus")
		void runProducts() {
			JPostman.Assert asserts = jpostman.ctx().soft();
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgLocalSoftCreatedVerifyStatus")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return createdExecutor();
		}
	}

	private static final class TestNgAssertContextVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#testNgAssertContextVerifyStatus")
		void runProducts() {
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgAssertContextVerifyStatus")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return okExecutor();
		}
	}

	private static final class TestNgAssertContextCreatedVerifyStatusFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.AssertContext
		private JPostman.Assert asserts;

		@JPostman.Runner(verify = 404, enabled = true, soft = true, executor = "#testNgAssertContextCreatedVerifyStatus")
		void runProducts() {
			applyRunnerRules(jpostman, asserts);
		}

		@JPostmanExecutor(id = "testNgAssertContextCreatedVerifyStatus")
		ApiExecutor executor(TestNgContext context) {
			assertNotNull(context.request());
			return createdExecutor();
		}
	}

	private static final class ResetAwareAssertContext {
		private int verifyCalls;
		private int assertsCalls;
		private int statusCode = -1;
		private boolean softPending = true;

		@SuppressWarnings("unused")
		public ResetAwareAssertions asserts() {
			assertsCalls++;
			return new ResetAwareAssertions(this);
		}

		@SuppressWarnings("unused")
		public ResetAwareAssertContext verify() {
			verifyCalls++;
			softPending = false;
			return this;
		}

		@SuppressWarnings("unused")
		public ResetAwareAssertContext verify(int expectedStatus) {
			verifyCalls++;
			statusCode = expectedStatus;
			softPending = false;
			return this;
		}
	}

	private static final class ResetAwareAssertions {
		private final ResetAwareAssertContext context;

		private ResetAwareAssertions(ResetAwareAssertContext context) {
			this.context = context;
		}

		@SuppressWarnings("unused")
		public ResetAwareAssertContext verify() {
			context.softPending = false;
			return context;
		}

		@SuppressWarnings("unused")
		public ResetAwareAssertContext verify(int expectedStatus) {
			context.statusCode = expectedStatus;
			context.softPending = false;
			return context;
		}
	}

	private static final class TestNgNoisySoftContext {
		// localSoftFacadePrefersRunnerFailureOverContextVerifyNoise
		@SuppressWarnings("unused")
		public TestNgNoisySoftAssertions soft(boolean log) {
			return new TestNgNoisySoftAssertions();
		}
	}

	private static final class TestNgNoisySoftAssertions {
		// localSoftFacadePrefersRunnerFailureOverContextVerifyNoise
		@SuppressWarnings("unused")
		public TestNgNoisySoftAssertions isTrue(boolean actual, String message) {
			return this;
		}
	}

	private static int countOccurrences(String value, String part) {
		int count = 0;
		int index = 0;
		while (true) {
			int next = value.indexOf(part, index);
			if (next < 0) {
				return count;
			}
			count++;
			index = next + part.length();
		}
	}
}
