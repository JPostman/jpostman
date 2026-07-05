package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;

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
		JPostmanAnnotationValidator.validateTestClass(testInstance.getClass());
		new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).setup(testInstance);
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
		JPostmanAnnotationValidator.validateTestMethod(testMethod);
		try {
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).run(testInstance, testMethod);
		} catch (Throwable e) {
			Throwable root = JPostmanStackTraceCleaner.rootCause(e);
			if (JPostmanStackTraceCleaner.isJUnitSkip(root)) {
				throw asException(cleanThrowable(testInstance, testMethod, root));
			}
			throw cleanFailure(testInstance, testMethod, e);
		}
	}

	/**
	 * Registers assertion cleanup for facade assertions executed from the user test
	 * body. The cleanup uses the current @JPostman.Context logs setting.
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
		JPostmanAnnotationValidator.validateTestClass(testInstance.getClass());
		new JPostmanAnnotationRunner<>(new TestNgPostmanFramework()).setup(testInstance);
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
	 * invokes a callback after each top-level @JPostmanRunner request completes.
	 *
	 * @param testInstance               TestNG test instance
	 * @param testMethod                 current TestNG test method
	 * @param afterRunnerRequestCallback callback invoked after each runner request,
	 *                                   or null when no per-request callback is
	 *                                   needed
	 * @throws Exception when annotation execution fails
	 */
	public static void runTestNg(Object testInstance, Method testMethod, Runnable afterRunnerRequestCallback)
			throws Exception {
		JPostmanAnnotationValidator.validateTestMethod(testMethod);
		new JPostmanAnnotationRunner<>(new TestNgPostmanFramework(), afterRunnerRequestCallback).run(testInstance,
				testMethod);
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
				return cleanRuntimeFailure(testInstance, testMethod, error, call.log());
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
	 * Creates the same configured failure display using a local log override.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @param error        original failure
	 * @param localLog     local annotation log mode
	 * @return cleaned assertion failure
	 */
	public static AssertionError cleanFailure(Object testInstance, Method testMethod, Throwable error,
			String localLog) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		options.markFailure(error, localLog);
		return JPostmanStackTraceCleaner.cleanFailure(testInstance.getClass(), testMethod, error,
				options.minimumErrorOutput(localLog), options.failureDiagnostics(error));
	}

	/**
	 * Creates a runtime-call failure display that points at the actual assertion
	 * line inside the test body.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @param error        original failure
	 * @param localLog     local annotation log mode
	 * @return cleaned assertion failure
	 */
	public static AssertionError cleanRuntimeFailure(Object testInstance, Method testMethod, Throwable error,
			String localLog) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		Throwable stackSource = JPostmanRuntimeCall.failureSource(error);
		Throwable display = runtimeDisplayError(testMethod, error, stackSource);
		options.markFailure(display, localLog);
		return JPostmanStackTraceCleaner.cleanRuntimeFailure(testInstance.getClass(), testMethod, display,
				options.minimumErrorOutput(localLog), options.failureDiagnostics(error));
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
	 * Creates the same configured throwable display using a local log override.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test or configuration method
	 * @param error        original failure
	 * @param localLog     local annotation log mode
	 * @return cleaned throwable
	 */
	public static Throwable cleanThrowable(Object testInstance, Method testMethod, Throwable error, String localLog) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		options.markFailure(error, localLog);
		return JPostmanStackTraceCleaner.cleanThrowable(testInstance.getClass(), testMethod, error,
				options.minimumErrorOutput(localLog));
	}

}
