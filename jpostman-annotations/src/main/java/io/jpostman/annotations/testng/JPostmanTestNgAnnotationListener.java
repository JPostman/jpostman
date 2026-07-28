package io.jpostman.annotations.testng;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.IAnnotationTransformer;
import org.testng.IClassListener;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestClass;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.ITestAnnotation;

import io.jpostman.annotations.JPostmanOutputs;
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
		implements IInvokedMethodListener, IAnnotationTransformer, IHookable, IClassListener, ITestListener {

	private final Set<Object> prepared = Collections
			.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
	private final Set<Object> reportedSetupFailures = Collections
			.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
	private final Set<Object> completedClasses = Collections
			.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
	private final Map<Object, Throwable> setupFailures = Collections.synchronizedMap(new IdentityHashMap<>());
	private final Map<Object, Map<Method, ITestResult>> testResults = Collections
			.synchronizedMap(new IdentityHashMap<>());
	private final Map<Object, Set<Method>> completedAfterClassMethods = Collections
			.synchronizedMap(new IdentityHashMap<>());

	/** Completes report output after user {@code @AfterClass} methods. */
	@Override
	public void onAfterClass(ITestClass testClass) {
		if (testClass == null) {
			return;
		}
		Class<?> realClass = testClass.getRealClass();

		Object[] instances;
		synchronized (prepared) {
			instances = prepared.stream().filter(instance -> instance != null && realClass.isInstance(instance))
					.toArray(Object[]::new);
		}
		for (Object instance : instances) {
			if (instance == null || !usesJPostmanAnnotations(instance)) {
				continue;
			}

			/*
			 * TestNG invokes this callback before user @AfterClass methods. Defer report
			 * completion until the final configuration method has finished.
			 */
			if (testClass.getAfterClassMethods() != null && testClass.getAfterClassMethods().length > 0) {
				continue;
			}
			completeClass(instance);
		}
	}

	private void completeClass(Object instance) {
		if (instance == null || !completedClasses.add(instance)) {
			return;
		}
		try {
			JPostmanAnnotationEngine.completeTestClass(instance);
		} finally {
			writeCompletedClassReport(instance);
			JPostmanAnnotationEngine.clearAssertionMethod(instance);
			testResults.remove(instance);
			completedAfterClassMethods.remove(instance);
		}
	}

	private ITestResult lastTestResult(Object testInstance) {
		Map<Method, ITestResult> results = testResults.get(testInstance);
		if (results == null || results.isEmpty()) {
			return null;
		}
		ITestResult last = null;
		for (ITestResult result : results.values()) {
			if (result != null && (last == null || result.getEndMillis() >= last.getEndMillis())) {
				last = result;
			}
		}
		return last;
	}

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
		rememberTestResult(testInstance, testMethod, testResult);
		boolean runnerMethod = JPostmanAnnotations.runner(testMethod) != null;
		boolean callMethod = JPostmanAnnotations.call(testMethod) != null;
		try {
			if (runnerMethod) {
				JPostmanAnnotationEngine.runTestNg(testInstance, testMethod,
						() -> runTestBodyWithAssertionCleanup(testInstance, testMethod, callBack, testResult, true));
				/*
				 * A runner may complete successfully without invoking the user callback (for
				 * example, when the runner itself owns all request iterations). TestNG requires
				 * an IHookable invocation to either invoke the callback or explicitly
				 * transition the result out of STARTED. Mark successful framework-owned
				 * completion here.
				 */
				if (testResult.getStatus() == ITestResult.STARTED) {
					testResult.setStatus(ITestResult.SUCCESS);
				}
			} else {
				JPostmanAnnotationEngine.runTestNg(testInstance, testMethod);
				runTestBodyWithAssertionCleanup(testInstance, testMethod, callBack, testResult, false);
				if (callMethod) {
					cleanCallMethodFailure(testInstance, testMethod, testResult);
				}
			}
		} catch (TestBodyFailureException e) {
			Throwable cause = e.getCause();
			if (cause instanceof SkipException) {
				if (isJPostmanSkip((SkipException) cause)) {
					testResult.setThrowable(null);
				} else {
					testResult.setThrowable(JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, cause));
				}
				testResult.setStatus(ITestResult.SKIP);
				JPostmanAnnotationEngine.recordFinalSkip(testInstance, testMethod);
			} else {
				JPostmanAnnotationEngine.recordFinalFailure(testInstance, testMethod, cause);
				if (callMethod) {
					testResult.setThrowable(cause);
					cleanCallMethodFailure(testInstance, testMethod, testResult);
				} else {
					AssertionError failure = JPostmanAnnotationEngine.cleanFailure(testInstance, testMethod, cause);
					testResult.setThrowable(failure);
				}
				testResult.setStatus(ITestResult.FAILURE);
			}
		} catch (SkipException e) {
			if (isJPostmanSkip(e)) {
				testResult.setThrowable(null);
			} else {
				testResult.setThrowable(JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, e));
			}
			testResult.setStatus(ITestResult.SKIP);
			JPostmanAnnotationEngine.recordFinalSkip(testInstance, testMethod);
		} catch (Throwable e) {
			JPostmanAnnotationEngine.recordFinalFailure(testInstance, testMethod, e);
			AssertionError failure = JPostmanAnnotationEngine.cleanFailure(testInstance, testMethod, e);
			/* Annotation verification is fail-fast and completes before the user body. */
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

		/* Retain results so the completed class report can summarize this instance. */
		if (invokedMethod != null && invokedMethod.isTestMethod()) {
			Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
			rememberTestResult(testInstance, javaMethod, testResult);
		}

		try {
			Throwable throwable = testResult.getThrowable();
			if (throwable == null) {
				return;
			}

			// Do not rewrite normal test-method results here. @JPostman.Call is the
			// exception because assertions can happen after jpostman.call() returns,
			// so TestNG may store the original assertion failure directly on ITestResult.
			if (invokedMethod.isTestMethod()) {
				Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
				if (testResult.getStatus() == ITestResult.FAILURE) {
					JPostmanAnnotationEngine.recordFinalFailure(testInstance, javaMethod, throwable);
				} else if (testResult.getStatus() == ITestResult.SKIP) {
					JPostmanAnnotationEngine.recordFinalSkip(testInstance, javaMethod);
				}
				if (JPostmanAnnotations.call(javaMethod) != null) {
					cleanCallMethodFailure(testInstance, javaMethod, testResult);
				}
				return;
			}

			if (invokedMethod.isConfigurationMethod()) {
				Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
				Throwable cleaned = JPostmanAnnotationEngine.cleanThrowable(testInstance, javaMethod, throwable);
				testResult.setThrowable(cleaned);

			}
		} finally {
			if (invokedMethod.isConfigurationMethod() && invokedMethod.getTestMethod().isAfterClassConfiguration()) {
				Method javaMethod = invokedMethod.getTestMethod().getConstructorOrMethod().getMethod();
				Set<Method> completed = completedAfterClassMethods.computeIfAbsent(testInstance, key -> Collections
						.synchronizedSet(Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())));
				if (javaMethod != null) {
					completed.add(javaMethod);
				}
				ITestClass testClass = invokedMethod.getTestMethod().getTestClass();
				int expected = testClass == null || testClass.getAfterClassMethods() == null ? 1
						: Math.max(1, testClass.getAfterClassMethods().length);
				if (completed.size() >= expected) {
					completeClass(testInstance);
				}
			}

			if (invokedMethod.isTestMethod()) {
				TestNgContext.clearCurrent();
			}
		}
	}

	private void writeCompletedClassReport(Object testInstance) {
		if (!JPostmanOutputs.isInstalled()) {
			return;
		}

		ITestResult retained = lastTestResult(testInstance);
		if (retained == null || retained.getTestContext() == null) {
			return;
		}

		ITestContext context = retained.getTestContext();
		List<ITestResult> passed = resultsFor(context.getPassedTests().getAllResults(), testInstance);
		List<ITestResult> failed = resultsFor(context.getFailedTests().getAllResults(), testInstance);
		List<ITestResult> skipped = resultsFor(context.getSkippedTests().getAllResults(), testInstance);
		List<ITestResult> failedConfigurations = resultsFor(context.getFailedConfigurations().getAllResults(),
				testInstance);
		List<ITestResult> skippedConfigurations = resultsFor(context.getSkippedConfigurations().getAllResults(),
				testInstance);

		StringBuilder output = new StringBuilder("=== Completed TestNG report: ")
				.append(testInstance.getClass().getSimpleName()).append(" ===\n");
		appendResults(output, "PASSED", passed, false);
		appendResults(output, "FAILED", failed, true);
		appendResults(output, "FAILED CONFIGURATION", failedConfigurations, true);
		appendResults(output, "SKIPPED", skipped, false);

		int testsCompleted = passed.size() + failed.size() + skipped.size();
		int reportedFailures = failed.size() + failedConfigurations.size();
		int reportedPasses = Math.max(0, testsCompleted - reportedFailures - skipped.size());

		output.append("Tests completed: ").append(testsCompleted).append('\n').append("Tests passed: ")
				.append(reportedPasses).append('\n').append("Tests failed: ").append(reportedFailures).append('\n')
				.append("Tests skipped: ").append(skipped.size()).append('\n').append("Configuration failures: 0\n")
				.append("Configuration skips: ").append(skippedConfigurations.size()).append('\n')
				.append("Reported failures: ").append(reportedFailures).append('\n')
				.append("=== End TestNG report ===\n");

		JPostmanOutputs.writeOrTrace(output.toString());
	}

	private List<ITestResult> resultsFor(Set<ITestResult> results, Object testInstance) {
		List<ITestResult> values = new ArrayList<>();
		for (ITestResult result : results) {
			if (result != null && result.getInstance() == testInstance) {
				values.add(result);
			}
		}
		values.sort(Comparator.comparingLong(ITestResult::getStartMillis)
				.thenComparing(result -> result.getName() == null ? "" : result.getName()));
		return values;
	}

	private void appendResults(StringBuilder output, String status, List<ITestResult> results, boolean includeFailure) {
		for (ITestResult result : results) {
			output.append(status).append(": ");
			if ("FAILED CONFIGURATION".equals(status) && result.getMethod() != null
					&& result.getMethod().isAfterClassConfiguration()) {
				output.append("@AfterClass ");
			}
			output.append(resultIdentifier(result)).append('\n');
			if (includeFailure && result.getThrowable() != null) {
				appendThrowable(output, result.getThrowable());
			}
			output.append('\n');
		}
	}

	private String resultIdentifier(ITestResult result) {
		Object instance = result.getInstance();
		String className = instance == null ? result.getTestClass().getRealClass().getSimpleName()
				: instance.getClass().getSimpleName();
		String methodName = result.getMethod() == null ? result.getName() : result.getMethod().getMethodName();
		return className + "." + methodName;
	}

	private void appendThrowable(StringBuilder output, Throwable throwable) {
		output.append(throwable.getClass().getName()).append(": ").append(String.valueOf(throwable.getMessage()))
				.append('\n');
		for (StackTraceElement element : throwable.getStackTrace()) {
			output.append("\tat ").append(element).append('\n');
		}
	}

	private void cleanCallMethodFailure(Object testInstance, Method testMethod, ITestResult testResult) {
		Throwable throwable = testResult.getThrowable();
		if (throwable == null) {
			return;
		}

		io.jpostman.annotations.JPostmanCall call = JPostmanAnnotations.call(testMethod);
		String localDebug = call == null ? "" : call.debug();

		if (throwable instanceof SkipException) {
			if (isJPostmanSkip((SkipException) throwable)) {
				testResult.setThrowable(null);
			} else {
				testResult.setThrowable(
						JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, throwable, localDebug));
			}
			return;
		}

		Throwable root = JPostmanStackTraceCleaner.rootCause(throwable);
		if (root instanceof AssertionError) {
			testResult.setThrowable(
					JPostmanAnnotationEngine.cleanRuntimeFailure(testInstance, testMethod, throwable, localDebug));
		} else {
			testResult.setThrowable(
					JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, throwable, localDebug));
		}
	}

	private boolean isJPostmanSkip(SkipException e) {
		String message = e == null ? "" : String.valueOf(e.getMessage());
		return message.startsWith("JPostman request skipped.") || message.startsWith("JPostman call skipped.")
				|| message.startsWith("JPostman response skipped.") || message.startsWith("JPostman runner skipped.")
				|| message.startsWith("WARN JPostman runner found zero requests");
	}

	private void runTestBodyWithAssertionCleanup(Object testInstance, Method testMethod, IHookCallBack callBack,
			ITestResult testResult, boolean verifyExplicitSoft) {
		JPostmanAnnotationEngine.beginAssertionCleanup(testInstance, testMethod);
		Throwable bodyFailure = null;
		Throwable verificationFailure = null;
		try {
			try {
				callBack.runTestMethod(testResult);
			} catch (Throwable error) {
				bodyFailure = error instanceof TestBodyFailureException && error.getCause() != null ? error.getCause()
						: error;
			}

			if (JPostmanAnnotationEngine.isRunnerBodyComplete(bodyFailure)
					|| JPostmanAnnotationEngine.isRunnerBodyComplete(testResult.getThrowable())) {
				testResult.setThrowable(null);
				return;
			}

			AssertionError immediateFailure = JPostmanAnnotationEngine.takeImmediateAssertionFailure();
			if (immediateFailure != null) {
				if (bodyFailure == null) {
					bodyFailure = immediateFailure;
				} else if (immediateFailure != bodyFailure) {
					bodyFailure.addSuppressed(immediateFailure);
				}
			}

			Throwable reportedFailure = testResult.getThrowable();
			if (reportedFailure != null) {
				if (bodyFailure == null) {
					bodyFailure = reportedFailure;
				} else if (reportedFailure != bodyFailure) {
					bodyFailure.addSuppressed(reportedFailure);
				}
			}

			try {
				if (verifyExplicitSoft) {
					JPostmanAnnotationEngine.verifyExplicitSoftAssertions(testMethod);
				} else {
					JPostmanAnnotationEngine.verifyRequestAssertions(testInstance, testMethod);
				}
			} catch (Throwable error) {
				verificationFailure = error;
			}
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}

		if (bodyFailure != null) {
			if (verificationFailure != null && verificationFailure != bodyFailure) {
				bodyFailure.addSuppressed(verificationFailure);
			}
			throw new TestBodyFailureException(bodyFailure);
		}
		if (verificationFailure != null) {
			throw new TestBodyFailureException(verificationFailure);
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
			if (JPostmanAnnotations.hasContext(field) || JPostmanAnnotations.hasTestContext(field)
					|| JPostmanAnnotations.hasAssertContext(field) || JPostmanAnnotations.hasReportContext(field)) {
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
				if (JPostmanAnnotations.hasContext(field) || JPostmanAnnotations.hasTestContext(field)
						|| JPostmanAnnotations.hasAssertContext(field) || JPostmanAnnotations.hasReportContext(field)) {
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

	private void rememberTestResult(Object instance, Method method, ITestResult result) {
		if (instance == null || method == null || result == null) {
			return;
		}
		synchronized (testResults) {
			testResults.computeIfAbsent(instance, key -> new java.util.LinkedHashMap<>()).put(method, result);
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

	private static final class TestBodyFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private TestBodyFailureException(Throwable cause) {
			super(cause);
		}
	}
}
