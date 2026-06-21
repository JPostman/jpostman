package io.jpostman.testng;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Thin TestNG adapter for optional JPostman annotation support.
 *
 * <p>
 * This class intentionally does not import {@code io.jpostman.annotations} so
 * {@code jpostman-testng} can compile and run without the annotation module.
 * When {@code jpostman-annotations} is present on the test classpath, the
 * annotation engine is loaded by class name and executes the shared annotation
 * runtime.
 * </p>
 */
public final class JPostmanTestNgAnnotationListener implements IInvokedMethodListener {

	private static final String ENGINE_CLASS = "io.jpostman.annotations.JPostmanAnnotationEngine";

	@Override
	public void beforeInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
		if (!invokedMethod.isTestMethod()) {
			return;
		}

		try {
			Object testInstance = testResult.getInstance();
			Method testMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
			runAnnotationEngine(testInstance, testMethod);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to run JPostman annotation engine.", e);
		}
	}

	@Override
	public void afterInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
		if (invokedMethod.isTestMethod()) {
			TestNgContext.clearCurrent();
		}
	}

	private void runAnnotationEngine(Object testInstance, Method testMethod) throws Exception {
		try {
			Class<?> engine = Class.forName(ENGINE_CLASS);
			Method run = engine.getMethod("runTestNg", Object.class, Method.class);
			run.invoke(null, testInstance, testMethod);
		} catch (ClassNotFoundException e) {
			// Annotation module is optional. Without it, @JPostmanTestNG still supports
			// the regular TestNG context/fluent API features.
			TestNgContext.clearCurrent();
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw e;
		}
	}
}
