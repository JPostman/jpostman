package io.jpostman.annotations.testng;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.SkipException;

import io.jpostman.annotations.JPostmanAnnotationEngine;
import io.jpostman.annotations.JPostmanAssert;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanRunner;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.annotations.runtime.JPostmanStackTraceCleaner;
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
	private final Set<Object> reportedSetupFailures = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Object, Throwable> setupFailures = Collections.synchronizedMap(new IdentityHashMap<>());

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

		Throwable setupFailure = setupOnce(testInstance);
		if (setupFailure != null) {
			if (markSetupFailureReported(testInstance)) {
				throw asRuntime(setupFailure);
			}

			throw new SkipException("Skipped because JPostman annotation setup failed.");
		}

		if (!invokedMethod.isTestMethod()) {
			return;
		}

		try {
			Method testMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
			JPostmanAnnotationEngine.runTestNg(testInstance, testMethod);
		} catch (Throwable e) {
			throw cleanFailure(invokedMethod, e);
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

		if (!usesJPostmanAnnotations(testInstance)) {
			return;
		}

		try {
			Throwable throwable = testResult.getThrowable();
			if (throwable == null) {
				return;
			}

			// Do not rewrite skipped test results caused by a failed configuration
			// method. TestNG may reuse the same configuration throwable for those skipped
			// results, and cleaning it with the skipped test method would make the original
			// @BeforeClass failure point to the wrong line.
			if (invokedMethod.isTestMethod() && testResult.getStatus() == ITestResult.SKIP) {
				return;
			}

			if (invokedMethod.isTestMethod() || invokedMethod.isConfigurationMethod()) {
				Class<?> testClass = invokedMethod.getTestMethod().getRealClass();
				Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
				testResult.setThrowable(JPostmanStackTraceCleaner.cleanThrowable(testClass, javaMethod, throwable));
			}
		} finally {
			if (invokedMethod.isTestMethod()) {
				TestNgContext.clearCurrent();
			}
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
					|| method.isAnnotationPresent(JPostmanRunner.class)
					|| method.isAnnotationPresent(JPostmanExecutor.class)
					|| method.isAnnotationPresent(JPostmanAssert.class)) {
				return true;
			}
		}

		return false;
	}

	private Throwable setupOnce(Object testInstance) {
		synchronized (prepared) {
			if (prepared.contains(testInstance)) {
				return null;
			}

			Throwable setupFailure = setupFailures.get(testInstance);
			if (setupFailure != null) {
				return setupFailure;
			}

			try {
				JPostmanAnnotationEngine.setupTestNg(testInstance);
				prepared.add(testInstance);
				return null;
			} catch (Throwable e) {
				Throwable failure = JPostmanStackTraceCleaner.rootCause(e);
				setupFailures.put(testInstance, failure);
				return failure;
			}
		}
	}

	private boolean markSetupFailureReported(Object testInstance) {
		synchronized (prepared) {
			return reportedSetupFailures.add(testInstance);
		}
	}

	private static RuntimeException asRuntime(Throwable throwable) {
		if (throwable instanceof RuntimeException) {
			return (RuntimeException) throwable;
		}
		if (throwable instanceof Error) {
			throw (Error) throwable;
		}
		return new IllegalStateException(
				throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage(),
				throwable);
	}

	private static AssertionError cleanFailure(IInvokedMethod invokedMethod, Throwable error) {
		Class<?> testClass = invokedMethod.getTestMethod().getRealClass();
		Method testMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
		return JPostmanStackTraceCleaner.cleanFailure(testClass, testMethod, error);
	}

}
