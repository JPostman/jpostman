package io.jpostman.annotations;

import java.lang.reflect.Method;

import io.jpostman.annotations.runtime.JPostmanAnnotationRunner;
import io.jpostman.annotations.runtime.JPostmanAnnotationValidator;
import io.jpostman.annotations.runtime.JPostmanStackTraceCleaner;
import io.jpostman.annotations.runtime.JUnitPostmanFramework;
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
		try {
			new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).run(testInstance, testMethod);
		} catch (Throwable e) {
			throw JPostmanStackTraceCleaner.cleanFailure(testInstance.getClass(), testMethod, e);
		}
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
		new JPostmanAnnotationRunner<>(new TestNgPostmanFramework()).run(testInstance, testMethod);
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
		Throwable root = JPostmanStackTraceCleaner.rootCause(error);
		if (root instanceof AssertionError) {
			return JPostmanStackTraceCleaner.cleanFailure(testInstance.getClass(), testMethod, error);
		}
		return JPostmanStackTraceCleaner.cleanThrowable(testInstance.getClass(), testMethod, error);
	}

}
