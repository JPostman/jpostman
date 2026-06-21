package io.jpostman.testng;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * TestNG listener that connects TestNG test execution to the optional JPostman
 * annotation engine.
 *
 * <p>
 * This listener is registered through Java ServiceLoader. It only runs for
 * classes annotated with {@link JPostmanTestNG}.
 * </p>
 *
 * <p>
 * This class intentionally does not directly import
 * {@code io.jpostman.annotations.JPostmanAnnotationEngine}. The annotation
 * module is optional, so the engine is loaded by class name.
 * </p>
 */
public final class JPostmanTestNgAnnotationListener implements IInvokedMethodListener {

	private static final String ENGINE_CLASS = "io.jpostman.annotations.JPostmanAnnotationEngine";

	@Override
	public void beforeInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
		if (!invokedMethod.isTestMethod()) {
			return;
		}

		Object testInstance = testResult.getInstance();

		if (!isJPostmanTest(testInstance)) {
			return;
		}

		try {
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
		if (!invokedMethod.isTestMethod()) {
			return;
		}

		Object testInstance = testResult.getInstance();

		if (isJPostmanTest(testInstance)) {
			TestNgContext.clearCurrent();
		}
	}

	private boolean isJPostmanTest(Object testInstance) {
		return testInstance != null && testInstance.getClass().isAnnotationPresent(JPostmanTestNG.class);
	}

	private void runAnnotationEngine(Object testInstance, Method testMethod) throws Exception {
		try {
			Class<?> engine = Class.forName(ENGINE_CLASS);
			Method run = engine.getMethod("runTestNg", Object.class, Method.class);
			run.invoke(null, testInstance, testMethod);
		} catch (ClassNotFoundException e) {
			// Annotation module is optional.
			// Without it, @JPostmanTestNG is just a marker and normal TestNG usage still
			// works.
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