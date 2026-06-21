package io.jpostman.annotations;

import java.lang.reflect.Method;

import io.jpostman.annotations.runtime.JPostmanAnnotationRunner;
import io.jpostman.annotations.runtime.JUnitPostmanFramework;
import io.jpostman.annotations.runtime.TestNgPostmanFramework;

/**
 * Public entry point for JPostman annotation execution.
 */
public final class JPostmanAnnotationEngine {

	public JPostmanAnnotationEngine() {
	}

	/**
	 * Runs annotation support for a JUnit test method.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @throws Exception when annotation execution fails
	 */
	public static void runJUnit(Object testInstance, Method testMethod) throws Exception {
		new JPostmanAnnotationRunner<>(new JUnitPostmanFramework()).run(testInstance, testMethod);
	}

	/**
	 * Runs annotation support for a TestNG test method.
	 *
	 * @param testInstance test instance
	 * @param testMethod   current test method
	 * @throws Exception when annotation execution fails
	 */
	public static void runTestNg(Object testInstance, Method testMethod) throws Exception {
		new JPostmanAnnotationRunner<>(new TestNgPostmanFramework()).run(testInstance, testMethod);
	}
}
