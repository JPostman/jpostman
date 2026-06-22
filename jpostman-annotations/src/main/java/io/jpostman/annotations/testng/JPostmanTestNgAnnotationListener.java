package io.jpostman.annotations.testng;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import io.jpostman.annotations.JPostmanAnnotationEngine;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.testng.TestNgContext;

/**
 * TestNG lifecycle bridge for the JPostman annotation engine.
 *
 * <p>
 * This listener lives in {@code jpostman-annotations}. When the annotations jar
 * is on the TestNG classpath, it can be loaded through ServiceLoader from
 * {@code META-INF/services/org.testng.ITestNGListener}. The listener is safe as
 * a global listener because it only runs for classes that actually use JPostman
 * annotations.
 * </p>
 *
 * <p>
 * Setup runs before the first TestNG invocation for each test instance. This
 * includes configuration methods such as {@code @BeforeClass}, so injected
 * {@code @JPostmanContext} and {@code @JPostmanTestContext} fields are
 * available in lifecycle methods.
 * </p>
 */
public final class JPostmanTestNgAnnotationListener implements IInvokedMethodListener {

	private final Set<Object> prepared = Collections.newSetFromMap(new IdentityHashMap<>());

	/**
	 * Prepares and runs JPostman annotations before TestNG invokes a test or
	 * configuration method.
	 *
	 * @param invokedMethod TestNG method descriptor
	 * @param testResult    TestNG test result for the current invocation
	 */
	@Override
	public void beforeInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
		Object testInstance = testResult.getInstance();

		if (!usesJPostmanAnnotations(testInstance)) {
			return;
		}

		setupOnce(testInstance);

		if (!invokedMethod.isTestMethod()) {
			return;
		}

		try {
			Method testMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
			JPostmanAnnotationEngine.runTestNg(testInstance, testMethod);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to run JPostman annotation engine.", e);
		}
	}

	/**
	 * Clears the current TestNG context after a JPostman-managed test method.
	 *
	 * @param invokedMethod TestNG method descriptor
	 * @param testResult    TestNG test result for the current invocation
	 */
	@Override
	public void afterInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
		Object testInstance = testResult.getInstance();

		if (invokedMethod.isTestMethod() && usesJPostmanAnnotations(testInstance)) {
			TestNgContext.clearCurrent();
		}
	}

	private boolean usesJPostmanAnnotations(Object testInstance) {
		if (testInstance == null) {
			return false;
		}

		Class<?> type = testInstance.getClass();

		if (type.isAnnotationPresent(JPostmanTestNgAnnotations.class)) {
			return true;
		}

		for (Field field : type.getDeclaredFields()) {
			if (field.isAnnotationPresent(JPostmanContext.class)
					|| field.isAnnotationPresent(JPostmanTestContext.class)) {
				return true;
			}
		}

		for (Method method : type.getDeclaredMethods()) {
			if (method.isAnnotationPresent(JPostmanRequest.class) || method.isAnnotationPresent(JPostmanResponse.class)
					|| method.isAnnotationPresent(JPostmanExecutor.class)) {
				return true;
			}
		}

		return false;
	}

	private void setupOnce(Object testInstance) {
		synchronized (prepared) {
			if (prepared.contains(testInstance)) {
				return;
			}

			try {
				JPostmanAnnotationEngine.setupTestNg(testInstance);
				prepared.add(testInstance);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException("Failed to set up JPostman annotation engine.", e);
			}
		}
	}
}
