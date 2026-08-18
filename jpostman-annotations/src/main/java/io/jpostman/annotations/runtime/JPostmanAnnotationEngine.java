package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanCall;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.annotations.testng.TestNgPostmanFramework;

/**
 * Public entry point for JPostman annotation setup and execution.
 *
 * <p>
 * Framework integrations call this class from JUnit or TestNG lifecycle hooks.
 * The engine keeps annotation behavior in the {@code jpostman-annotations}
 * module while framework modules provide only the small lifecycle bridge.
 * </p>
 */
public final class JPostmanAnnotationEngine {

	/**
	 * Creates an annotation engine instance.
	 *
	 * <p>
	 * The engine currently exposes static entry points, so callers normally do not
	 * need to instantiate this class.
	 * </p>
	 */
	public JPostmanAnnotationEngine() {
	}

	/**
	 * Prepares JPostman annotation support for a JUnit test instance.
	 *
	 * <p>
	 * This injects fields such as {@link JPostmanContext} and
	 * {@link JPostmanTestContext} before JUnit lifecycle methods, including
	 * {@code @BeforeAll}, access them.
	 * </p>
	 *
	 * @param testInstance JUnit test instance to prepare
	 * @throws Exception when collection, environment, rules, or field injection
	 *                   fails
	 */
	public static void setupJUnit(Object testInstance) throws Exception {
		try {
			JPostmanAnnotationValidator.validateTestClass(testInstance.getClass());
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).setup(testInstance);
		} catch (Exception | Error e) {
			JPostmanDebugFile.failure(testInstance, null, "setup", "", e);
			throw e;
		}
	}

	/**
	 * Completes class-level report facilities after user class teardown. Assertion
	 * verification is request-scoped and is not performed here.
	 *
	 * @param testInstance completed test instance
	 */
	public static void completeTestClass(Object testInstance) {
		if (testInstance == null) {
			return;
		}

		try {
			Class<?> current = testInstance.getClass();
			while (current != null && current != Object.class) {
				for (Field field : current.getDeclaredFields()) {
					field.setAccessible(true);
					if (!JPostmanAnnotations.hasReportContext(field)) {
						continue;
					}
					try {
						Object value = field.get(testInstance);
						if (value instanceof JPostmanReport) {
							((JPostmanReport) value).summary();
						}
					} catch (IllegalAccessException e) {
						throw new IllegalStateException(e);
					}
				}
				current = current.getSuperclass();
			}
		} finally {
			JPostmanTestProxy.clearRuntimeValues(testInstance);
		}
	}

	/**
	 * Verifies and clears every injected soft assertion context on the supplied
	 * test instance. Calling {@link JPostman.Assert#verify()} manually consumes the
	 * same collector, so this automatic fallback is a no-op when the user already
	 * verified it.
	 *
	 * @param testInstance active test instance
	 */
	public static void verifySoftAssertContexts(Object testInstance) {
		if (testInstance == null) {
			return;
		}

		AssertionError failure = null;
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				io.jpostman.annotations.JPostmanAssertContext annotation = JPostmanAnnotations.assertContext(field);
				if (annotation == null || !annotation.soft()) {
					continue;
				}
				try {
					field.setAccessible(true);
					Object value = field.get(testInstance);
					if (value instanceof JPostman.Assert) {
						((JPostman.Assert) value).verify();
					}
				} catch (AssertionError error) {
					if (failure == null) {
						failure = error;
					} else {
						failure.addSuppressed(error);
					}
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
			current = current.getSuperclass();
		}

		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * Verifies every soft assertion collector owned by the current annotated
	 * request. Explicit context-created soft facades and injected soft
	 * {@code AssertContext} fields are both consumed even when one of them fails.
	 *
	 * @param testInstance active test instance
	 * @param testMethod   active test method
	 */
	public static void verifyRequestAssertions(Object testInstance, Method testMethod) {
		AssertionError failure = null;
		try {
			verifyExplicitSoftAssertions(testMethod);
		} catch (AssertionError error) {
			failure = error;
		}

		if (testMethod != null && JPostmanAnnotations.response(testMethod) != null) {
			try {
				verifySoftAssertContexts(testInstance);
			} catch (AssertionError error) {
				if (failure == null) {
					failure = error;
				} else {
					failure.addSuppressed(error);
				}
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * Runs JPostman annotation support for a JUnit test method.
	 *
	 * <p>
	 * This executes annotations such as {@link JPostmanRequest},
	 * {@link JPostmanResponse}, and {@link JPostmanExecutor} around the supplied
	 * JUnit test method.
	 * </p>
	 *
	 * @param testInstance JUnit test instance
	 * @param testMethod   current JUnit test method
	 * @throws Exception when annotation execution fails
	 */
	public static void runJUnit(Object testInstance, Method testMethod) throws Exception {
		runJUnit(testInstance, testMethod, null);
	}

	/**
	 * Executes a standalone JUnit-facing {@code @JPostman.Response} even when the
	 * Java method returns a value and therefore cannot be scheduled as a native
	 * JUnit {@code @Test} method.
	 *
	 * @param testInstance active JUnit test instance
	 * @param testMethod   standalone response method
	 * @throws Exception when response execution fails
	 */
	public static void runJUnitExternalResponse(Object testInstance, Method testMethod) throws Exception {
		beginVerificationOutcome();
		beginAssertionCleanup(testInstance, testMethod);
		try {
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).runExternalResponse(testInstance, testMethod);
			verifyRequestAssertions(testInstance, testMethod);
		} catch (Throwable e) {
			JPostmanDebugFile.failure(testInstance, debugInfo(testMethod), "debug", "", e);
			Throwable root = JPostmanStackTraceCleaner.rootCause(e);
			if (JPostmanStackTraceCleaner.isJUnitSkip(root)) {
				throw asException(cleanThrowable(testInstance, testMethod, root));
			}
			throw cleanFailure(testInstance, testMethod, e);
		} finally {
			endAssertionCleanup();
			clearVerificationOutcome();
		}
	}

	/**
	 * Runs JPostman annotation support for a JUnit test method and optionally
	 * invokes a callback around each top-level @JPostmanRunner request.
	 *
	 * @param testInstance               JUnit test instance
	 * @param testMethod                 current JUnit test method
	 * @param afterRunnerRequestCallback callback used by runner lifecycle handling:
	 *                                   lifecycle=true invokes it after each
	 *                                   attempted request; lifecycle=false invokes
	 *                                   it once when the whole runner reaches
	 *                                   completion
	 * @throws Exception when annotation execution fails
	 */
	public static void runJUnit(Object testInstance, Method testMethod, Runnable afterRunnerRequestCallback)
			throws Exception {
		try {
			JPostmanAnnotationValidator.validateTestMethod(testMethod);
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework(), afterRunnerRequestCallback).run(testInstance,
					testMethod);
		} catch (Throwable e) {
			JPostmanDebugFile.failure(testInstance, debugInfo(testMethod), "debug", "", e);
			Throwable root = JPostmanStackTraceCleaner.rootCause(e);
			if (JPostmanStackTraceCleaner.isJUnitSkip(root)) {
				throw asException(cleanThrowable(testInstance, testMethod, root));
			}
			throw cleanFailure(testInstance, testMethod, e);
		}
	}

	/**
	 * Registers assertion cleanup for facade assertions executed from the user test
	 * body. The cleanup uses the current @JPostman.Context debug setting.
	 *
	 * @param testInstance current test instance
	 * @param testMethod   current test method
	 */
	public static void beginAssertionCleanup(Object testInstance, Method testMethod) {
		JPostmanAssertionCleanup.register(testInstance, testMethod);
	}

	/** Clears assertion cleanup for the current test body. */
	public static void endAssertionCleanup() {
		JPostmanAssertionCleanup.clear();
	}

	/** Starts a fresh verification outcome for the current framework test. */
	public static void beginVerificationOutcome() {
		JPostmanVerificationOutcome.clear();
	}

	/**
	 * Returns {@code true} when a completed request used {@code verify = 1} and the
	 * otherwise successful framework test must be reported as skipped.
	 */
	public static boolean verificationSkipRequested() {
		return JPostmanVerificationOutcome.requested();
	}

	/** Returns the framework skip message for a {@code verify = 1} outcome. */
	public static String verificationSkipMessage(Method testMethod) {
		return JPostmanVerificationOutcome.message(testMethod);
	}

	/** Clears the verification outcome after the framework finalizes the test. */
	public static void clearVerificationOutcome() {
		JPostmanVerificationOutcome.clear();
	}

	/**
	 * Verifies and clears explicit context-created soft assertion facades recorded
	 * while the current test body continued executing.
	 */
	public static void verifyExplicitSoftAssertions(Method testMethod) {
		if (testMethod == null || (JPostmanAnnotations.response(testMethod) == null
				&& JPostmanAnnotations.call(testMethod) == null && JPostmanAnnotations.runner(testMethod) == null)) {
			return;
		}
		JPostmanAssertionCleanup.verifyExplicitSoft();
	}

	public static AssertionError takeImmediateAssertionFailure() {
		return JPostmanAssertionCleanup.takeImmediateFailure();
	}

	/** Returns the latest test method that used the injected assertion facade. */
	public static Method lastAssertionMethod(Object testInstance) {
		return JPostmanAssertionCleanup.lastMethod(testInstance);
	}

	/** Clears retained assertion-origin metadata after class completion. */
	public static void clearAssertionMethod(Object testInstance) {
		JPostmanAssertionCleanup.clear(testInstance);
	}

	/**
	 * Returns true when the supplied throwable is the internal runner body control
	 * signal used to stop a fluent runner method after the active phase has been
	 * handled. Framework integrations use this to avoid reporting the control
	 * signal as a normal test failure.
	 *
	 * @param throwable throwable to inspect
	 * @return true when the throwable contains the runner body completion signal
	 */
	public static boolean isRunnerBodyComplete(Throwable throwable) {
		return JPostmanRuntimeRunner.isRunnerBodyComplete(throwable);
	}

	private static JPostmanInfo debugInfo(Method method) {
		return method == null ? null : new JPostmanInfo("@JPostman", method.getName(), "", "", "");
	}

	private static Exception asException(Throwable throwable) {
		if (throwable instanceof Exception) {
			return (Exception) throwable;
		}

		if (throwable instanceof Error) {
			throw (Error) throwable;
		}

		return new RuntimeException(throwable);
	}

	/**
	 * Prepares JPostman annotation support for a TestNG test instance.
	 *
	 * <p>
	 * This injects fields such as {@link JPostmanContext} and
	 * {@link JPostmanTestContext} before TestNG configuration methods, including
	 * {@code @BeforeClass}, access them.
	 * </p>
	 *
	 * @param testInstance TestNG test instance to prepare
	 * @throws Exception when collection, environment, rules, or field injection
	 *                   fails
	 */
	public static void setupTestNg(Object testInstance) throws Exception {
		try {
			JPostmanAnnotationValidator.validateTestClass(testInstance.getClass());
			new JPostmanAnnotationRunner<>(new TestNgPostmanFramework()).setup(testInstance);
		} catch (Exception | Error e) {
			JPostmanDebugFile.failure(testInstance, null, "setup", "", e);
			throw e;
		}
	}

	/**
	 * Runs JPostman annotation support for a TestNG test method.
	 *
	 * <p>
	 * This executes annotations such as {@link JPostmanRequest},
	 * {@link JPostmanResponse}, and {@link JPostmanExecutor} around the supplied
	 * TestNG test method.
	 * </p>
	 *
	 * @param testInstance TestNG test instance
	 * @param testMethod   current TestNG test method
	 * @throws Exception when annotation execution fails
	 */
	public static void runTestNg(Object testInstance, Method testMethod) throws Exception {
		runTestNg(testInstance, testMethod, null);
	}

	/**
	 * Runs JPostman annotation support for a TestNG test method and optionally
	 * invokes a callback around each top-level @JPostmanRunner request.
	 *
	 * @param testInstance               TestNG test instance
	 * @param testMethod                 current TestNG test method
	 * @param afterRunnerRequestCallback callback used by runner lifecycle handling:
	 *                                   lifecycle=true invokes it after each
	 *                                   attempted request; lifecycle=false invokes
	 *                                   it once when the whole runner reaches
	 *                                   completion
	 * @throws Exception when annotation execution fails
	 */
	public static void runTestNg(Object testInstance, Method testMethod, Runnable afterRunnerRequestCallback)
			throws Exception {
		try {
			JPostmanAnnotationValidator.validateTestMethod(testMethod);
			new JPostmanAnnotationRunner<>(new TestNgPostmanFramework(), afterRunnerRequestCallback).run(testInstance,
					testMethod);
		} catch (Exception | Error e) {
			JPostmanDebugFile.failure(testInstance, debugInfo(testMethod), "debug", "", e);
			throw e;
		}
	}

	/** Records the final framework result after deferred assertions are flushed. */
	public static void recordFinalFailure(Object testInstance, Method testMethod) {
		recordFinalFailure(testInstance, testMethod, null);
	}

	/** Records the final framework failure and applies report failure options. */
	public static void recordFinalFailure(Object testInstance, Method testMethod, Throwable failure) {
		try {
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).recordFinalFailure(testInstance, testMethod,
					failure);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Result reporting must never replace the original test failure.
		}
	}

	/**
	 * Records a deferred assertion as a normal failed execution when possible. If
	 * the report status does not change, records one configuration failure so the
	 * displayed totals can still absorb it into the failed-test count.
	 */
	public static void recordFinalFailureOrConfiguration(Object testInstance, Method testMethod) {
		java.util.List<JPostmanReport> reports = reports(testInstance);
		int[] passedBefore = new int[reports.size()];
		int[] failedBefore = new int[reports.size()];
		for (int index = 0; index < reports.size(); index++) {
			passedBefore[index] = reports.get(index).passed.size();
			failedBefore[index] = reports.get(index).failed.size();
		}

		if (testMethod != null) {
			recordFinalFailure(testInstance, testMethod);
		}

		for (int index = 0; index < reports.size(); index++) {
			JPostmanReport report = reports.get(index);
			boolean statusChanged = report.passed.size() != passedBefore[index]
					|| report.failed.size() != failedBefore[index];
			if (!statusChanged) {
				report.configurationFailed();
			}
		}
	}

	/**
	 * ReportContext no longer provides a full-error-trace option. This
	 * compatibility method therefore always returns {@code false}; use method-level
	 * {@code debug = "error"} for deferred error output.
	 *
	 * @param testInstance current test instance
	 * @return always {@code false}
	 */
	public static boolean defersFailureTrace(Object testInstance) {
		return false;
	}

	/**
	 * Returns {@code true} when a report context is declared for the test class.
	 * Once present, the report's {@code details} and {@code fail} settings own all
	 * automatic failure output. This prevents JUnit's optional
	 * {@code printFailures} bridge from printing an immediate duplicate or from
	 * bypassing {@code fail = "ignore"}.
	 *
	 * @param testInstance current test instance
	 * @return {@code true} when a compact or standalone report context is declared
	 */
	public static boolean reportControlsFailureOutput(Object testInstance) {
		if (testInstance == null) {
			return false;
		}
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (JPostmanAnnotations.hasReportContext(field)) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
	}

	private static java.util.List<JPostmanReport> reports(Object testInstance) {
		java.util.List<JPostmanReport> reports = new java.util.ArrayList<>();
		if (testInstance == null) {
			return reports;
		}

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!JPostmanAnnotations.hasReportContext(field)) {
					continue;
				}
				try {
					field.setAccessible(true);
					Object value = field.get(testInstance);
					if (value instanceof JPostmanReport) {
						reports.add((JPostmanReport) value);
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Report discovery must never replace the original failure.
				}
			}
			current = current.getSuperclass();
		}
		return reports;
	}

	/** Records a final framework skip after annotation execution. */
	public static void recordFinalSkip(Object testInstance, Method testMethod) {
		try {
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).recordFinalSkip(testInstance, testMethod);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Result reporting must never replace the original test outcome.
		}
	}

	/**
	 * Creates the same short stack-trace failure used by TestNG for JUnit failures.
	 *
	 * <p>
	 * The JUnit bridge calls this method through reflection so the junit module
	 * does not need a compile-time dependency on {@code jpostman-annotations}.
	 * </p>
	 *
	 * @param testInstance JUnit test instance
	 * @param testMethod   current JUnit test method
	 * @param error        original failure
	 * @return throwable with cleaned stack trace
	 */
	public static Throwable cleanJUnitFailure(Object testInstance, Method testMethod, Throwable error) {
		if (testMethod == null) {
			return JPostmanStackTraceCleaner.rootCause(error);
		}

		Throwable root = JPostmanStackTraceCleaner.rootCause(error);
		if (root instanceof AssertionError) {
			JPostmanCall call = JPostmanAnnotations.call(testMethod);
			if (call != null && JPostmanRuntimeCall.hasFailureSource()) {
				return cleanRuntimeFailure(testInstance, testMethod, error, call.debug());
			}
			return cleanFailure(testInstance, testMethod, error);
		}
		return cleanThrowable(testInstance, testMethod, error);
	}

	/**
	 * Creates the same configured failure display used by JUnit for TestNG.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @param error        original failure
	 * @return cleaned assertion failure
	 */
	public static AssertionError cleanFailure(Object testInstance, Method testMethod, Throwable error) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		return JPostmanStackTraceCleaner.cleanFailure(testInstance.getClass(), testMethod, error,
				options.minimumErrorOutput(error), options.failureDiagnostics(error));
	}

	/**
	 * Creates the same configured failure display using a local debug override.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @param error        original failure
	 * @param localDebug   local annotation debug setting
	 * @return cleaned assertion failure
	 */
	public static AssertionError cleanFailure(Object testInstance, Method testMethod, Throwable error,
			String localDebug) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		options.markFailure(error, localDebug);
		return JPostmanStackTraceCleaner.cleanFailure(testInstance.getClass(), testMethod, error,
				options.minimumErrorOutput(localDebug), options.failureDiagnostics(error));
	}

	/**
	 * Creates a runtime-call failure display that points at the actual assertion
	 * line inside the test body.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @param error        original failure
	 * @param localDebug   local annotation debug setting
	 * @return cleaned assertion failure
	 */
	public static AssertionError cleanRuntimeFailure(Object testInstance, Method testMethod, Throwable error,
			String localDebug) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		Throwable stackSource = JPostmanRuntimeCall.failureSource(error);
		Throwable display = runtimeDisplayError(testMethod, error, stackSource);
		options.markFailure(display, localDebug);
		return JPostmanStackTraceCleaner.cleanRuntimeFailure(testInstance.getClass(), testMethod, display,
				options.minimumErrorOutput(localDebug), options.failureDiagnostics(error));
	}

	private static Throwable runtimeDisplayError(Method testMethod, Throwable error, Throwable stackSource) {
		if (error == null) {
			return null;
		}

		String message = runtimeDisplayMessage(testMethod, error);
		Throwable source = stackSource == null ? error : stackSource;
		if (source == error && value(error.getMessage()).equals(message)) {
			return error;
		}

		AssertionError display = new AssertionError(message);
		display.setStackTrace(source.getStackTrace());
		copySuppressed(error, display);
		if (source != error) {
			copySuppressed(source, display);
		}
		return display;
	}

	private static String runtimeDisplayMessage(Method testMethod, Throwable error) {
		String message = value(error == null ? null : error.getMessage()).stripTrailing();
		if (message.contains("(@JPostmanCall")) {
			return message;
		}

		JPostmanCall call = testMethod == null ? null : JPostmanAnnotations.call(testMethod);
		if (call == null) {
			return message;
		}

		return JPostmanErrors.message(JPostmanErrors.info(call), message).stripTrailing();
	}

	private static void copySuppressed(Throwable source, Throwable target) {
		if (source == null || target == null) {
			return;
		}
		for (Throwable suppressed : source.getSuppressed()) {
			if (suppressed != null) {
				target.addSuppressed(suppressed);
			}
		}
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Creates the same configured throwable display used by JUnit for TestNG.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test or configuration method
	 * @param error        original failure
	 * @return cleaned throwable
	 */
	public static Throwable cleanThrowable(Object testInstance, Method testMethod, Throwable error) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		return JPostmanStackTraceCleaner.cleanThrowable(testInstance.getClass(), testMethod, error,
				options.minimumErrorOutput(error));
	}

	/**
	 * Creates the same configured throwable display using a local debug override.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test or configuration method
	 * @param error        original failure
	 * @param localDebug   local annotation debug setting
	 * @return cleaned throwable
	 */
	public static Throwable cleanThrowable(Object testInstance, Method testMethod, Throwable error, String localDebug) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		options.markFailure(error, localDebug);
		return JPostmanStackTraceCleaner.cleanThrowable(testInstance.getClass(), testMethod, error,
				options.minimumErrorOutput(localDebug));
	}

}
