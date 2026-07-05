package io.jpostman.annotations.testng;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.testng.IAnnotationTransformer;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.ITestAnnotation;

import io.jpostman.annotations.runtime.JPostmanAnnotationEngine;
import io.jpostman.annotations.runtime.JPostmanAnnotationValidator;
import io.jpostman.annotations.runtime.JPostmanAnnotations;
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
public final class JPostmanTestNgAnnotationListener
		implements IInvokedMethodListener, IAnnotationTransformer, IHookable {

	private final Set<Object> prepared = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Object> reportedSetupFailures = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Object, Throwable> setupFailures = Collections.synchronizedMap(new IdentityHashMap<>());

	/**
	 * Validates TestNG @Test methods before TestNG attempts native parameter
	 * injection. This lets JPostman show a clear annotation error instead of
	 * TestNG's generic injection failure.
	 */
	@Override
	@SuppressWarnings("rawtypes")
	public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
		if (testMethod == null) {
			return;
		}

		if (!usesJPostmanAnnotations(testMethod.getDeclaringClass())) {
			return;
		}

		JPostmanAnnotationValidator.validateTestMethod(testMethod);
	}

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

		// Test-method annotation execution is handled by IHookable#run.
		// Running it here is too early for TestNG to reliably short-circuit the
		// actual test body when JPostman decides the test should be skipped, such as
		// @JPostmanRunner(folder=...) resolving zero collection requests.
	}

	/**
	 * Runs JPostman test annotations inside TestNG's hookable invocation path.
	 *
	 * <p>
	 * This is the reliable place to prevent the user test body from running when
	 * JPostman throws a framework skip, for example when @JPostmanRunner targets a
	 * folder that contains zero requests.
	 * </p>
	 */
	@Override
	public void run(IHookCallBack callBack, ITestResult testResult) {
		Object testInstance = testResult.getInstance();

		if (!usesJPostmanAnnotations(testInstance)) {
			callBack.runTestMethod(testResult);
			return;
		}

		Throwable setupFailure = setupOnce(testInstance);
		if (setupFailure != null) {
			RuntimeException failure = asRuntime(setupFailure);
			testResult.setThrowable(failure);
			testResult.setStatus(markSetupFailureReported(testInstance) ? ITestResult.FAILURE : ITestResult.SKIP);
			return;
		}

		Method testMethod = testResult.getMethod().getConstructorOrMethod().getMethod();
		boolean runnerMethod = JPostmanAnnotations.runner(testMethod) != null;
		boolean callMethod = JPostmanAnnotations.call(testMethod) != null;
		try {
			if (runnerMethod) {
				JPostmanAnnotationEngine.runTestNg(testInstance, testMethod,
						() -> runTestBodyWithAssertionCleanup(testInstance, testMethod, callBack, testResult));
			} else {
				JPostmanAnnotationEngine.runTestNg(testInstance, testMethod);
				runTestBodyWithAssertionCleanup(testInstance, testMethod, callBack, testResult);
				if (callMethod) {
					cleanCallMethodFailure(testInstance, testMethod, testResult);
				}
			}
		} catch (SkipException e) {
			if (isJPostmanSkip(e)) {
				testResult.setThrowable(null);
			} else {
				testResult.setThrowable(JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, e));
			}
			testResult.setStatus(ITestResult.SKIP);
		} catch (Throwable e) {
			AssertionError failure = JPostmanAnnotationEngine.cleanFailure(testInstance, testMethod, e);
			runTestBodyAfterAnnotationFailure(callBack, testResult, failure);
			testResult.setThrowable(failure);
			testResult.setStatus(ITestResult.FAILURE);
		} finally {
			TestNgContext.clearCurrent();
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

			// Do not rewrite normal test-method results here. @JPostman.Call is the
			// exception because assertions can happen after jpostman.request() returns,
			// so TestNG may store the original assertion failure directly on ITestResult.
			if (invokedMethod.isTestMethod()) {
				Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
				if (JPostmanAnnotations.call(javaMethod) != null) {
					cleanCallMethodFailure(testInstance, javaMethod, testResult);
				}
				return;
			}

			if (invokedMethod.isConfigurationMethod()) {
				@SuppressWarnings("unused")
				Class<?> testClass = invokedMethod.getTestMethod().getRealClass();
				Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
				testResult.setThrowable(JPostmanAnnotationEngine.cleanThrowable(testInstance, javaMethod, throwable));
			}
		} finally {
			if (invokedMethod.isTestMethod()) {
				TestNgContext.clearCurrent();
			}
		}
	}

	private void cleanCallMethodFailure(Object testInstance, Method testMethod, ITestResult testResult) {
		Throwable throwable = testResult.getThrowable();
		if (throwable == null) {
			return;
		}

		io.jpostman.annotations.JPostmanCall call = JPostmanAnnotations.call(testMethod);
		String localLog = call == null ? "" : call.log();

		if (throwable instanceof SkipException) {
			if (isJPostmanSkip((SkipException) throwable)) {
				testResult.setThrowable(null);
			} else {
				testResult.setThrowable(
						JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, throwable, localLog));
			}
			return;
		}

		Throwable root = JPostmanStackTraceCleaner.rootCause(throwable);
		if (root instanceof AssertionError) {
			testResult.setThrowable(
					JPostmanAnnotationEngine.cleanRuntimeFailure(testInstance, testMethod, throwable, localLog));
		} else {
			testResult.setThrowable(
					JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, throwable, localLog));
		}
	}

	private boolean isJPostmanSkip(SkipException e) {
		String message = e == null ? "" : String.valueOf(e.getMessage());
		return message.startsWith("JPostman request skipped.") || message.startsWith("JPostman call skipped.")
				|| message.startsWith("JPostman response skipped.") || message.startsWith("JPostman runner skipped.")
				|| message.startsWith("WARN JPostman runner found zero requests");
	}

	private void runTestBodyWithAssertionCleanup(Object testInstance, Method testMethod, IHookCallBack callBack,
			ITestResult testResult) {
		JPostmanAnnotationEngine.beginAssertionCleanup(testInstance, testMethod);
		try {
			callBack.runTestMethod(testResult);
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	private void runTestBodyAfterAnnotationFailure(IHookCallBack callBack, ITestResult testResult, Throwable failure) {
		try {
			Object testInstance = testResult.getInstance();
			Method testMethod = testResult.getMethod().getConstructorOrMethod().getMethod();
			runTestBodyWithAssertionCleanup(testInstance, testMethod, callBack, testResult);
		} catch (Throwable bodyFailure) {
			if (bodyFailure != null && bodyFailure != failure) {
				failure.addSuppressed(bodyFailure);
			}
		}

		Throwable reported = testResult.getThrowable();
		if (reported != null && reported != failure) {
			failure.addSuppressed(reported);
		}
	}

	private boolean usesJPostmanAnnotations(Object testInstance) {
		if (testInstance == null) {
			return false;
		}

		Class<?> type = testInstance.getClass();

		if (JPostmanAnnotations.hasTestNg(type)) {
			return true;
		}

		for (Field field : type.getDeclaredFields()) {
			if (JPostmanAnnotations.hasContext(field) || JPostmanAnnotations.hasTestContext(field)) {
				return true;
			}
		}

		for (Method method : type.getDeclaredMethods()) {
			if (JPostmanAnnotations.hasRequest(method) || JPostmanAnnotations.hasResponse(method)
					|| JPostmanAnnotations.hasCall(method) || JPostmanAnnotations.hasRunner(method)
					|| JPostmanAnnotations.hasExecutor(method)) {
				return true;
			}
		}

		return false;
	}

	private boolean usesJPostmanAnnotations(Class<?> type) {
		if (type == null) {
			return false;
		}

		Class<?> current = type;
		while (current != null && current != Object.class) {
			if (JPostmanAnnotations.hasTestNg(current)) {
				return true;
			}

			for (Field field : current.getDeclaredFields()) {
				if (JPostmanAnnotations.hasContext(field) || JPostmanAnnotations.hasTestContext(field)) {
					return true;
				}
			}

			for (Method method : current.getDeclaredMethods()) {
				if (JPostmanAnnotations.hasRequest(method) || JPostmanAnnotations.hasResponse(method)
						|| JPostmanAnnotations.hasCall(method) || JPostmanAnnotations.hasRunner(method)
						|| JPostmanAnnotations.hasExecutor(method)) {
					return true;
				}
			}

			current = current.getSuperclass();
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
}
