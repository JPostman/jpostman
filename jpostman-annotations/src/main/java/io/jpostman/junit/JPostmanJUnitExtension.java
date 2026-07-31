package io.jpostman.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.opentest4j.TestAbortedException;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
import io.jpostman.annotations.runtime.JPostmanAnnotationEngine;
import io.jpostman.annotations.runtime.JPostmanAnnotations;
import io.jpostman.annotations.runtime.JPostmanStackTraceCleaner;

/**
 * JUnit 5 bridge for JPostman annotation execution.
 */
public final class JPostmanJUnitExtension
		implements TestInstancePostProcessor, BeforeAllCallback, AfterAllCallback, InvocationInterceptor {

	private final Set<Object> prepared = Collections.newSetFromMap(new IdentityHashMap<>());

	/**
	 * Injects JPostman fields before JUnit calls non-static {@code @BeforeAll}.
	 */
	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
		setup(testInstance, context);
	}

	/**
	 * Fallback for JUnit engines that call beforeAll before post processing. With
	 * {@code @TestInstance(PER_CLASS)}, the test instance is normally available.
	 */
	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		Object testInstance = context.getTestInstance().orElse(null);
		if (testInstance != null) {
			setup(testInstance, context);
		}
	}

	/** Completes report output after user {@code @AfterAll} methods. */
	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		try {
			Object direct = context.getTestInstance().orElse(null);
			Set<Object> instances = Collections.newSetFromMap(new IdentityHashMap<>());
			if (direct != null) {
				instances.add(direct);
			}
			synchronized (prepared) {
				for (Object candidate : prepared) {
					if (candidate != null && context.getRequiredTestClass().isInstance(candidate)) {
						instances.add(candidate);
					}
				}
			}
			for (Object instance : instances) {
				JPostmanAnnotationEngine.completeTestClass(instance);
			}
		} finally {
			synchronized (prepared) {
				for (Object instance : prepared) {
					JPostmanAnnotationEngine.clearAssertionMethod(instance);
				}
			}
			JUnitContext.clearCurrent();
		}
	}

	/**
	 * Runs JPostman request/response/runner annotations before the user test body.
	 */
	@Override
	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {

		Object testInstance = extensionContext.getRequiredTestInstance();
		Method testMethod = invocationContext.getExecutable();

		JPostmanAnnotationEngine.beginVerificationOutcome();
		try {
			setup(testInstance, extensionContext);
			if (JPostmanAnnotations.runner(testMethod) != null) {
				boolean[] proceeded = new boolean[1];
				JPostmanAnnotationEngine.runJUnit(testInstance, testMethod,
						() -> invokeTestBodyWithAssertionCleanup(invocation, testInstance, testMethod, proceeded));
				if (!proceeded[0] && !hasRunnerDependencyLauncher(testMethod)) {
					invokeTestBodyWithAssertionCleanup(invocation, testInstance, testMethod, proceeded);
				}
			} else {
				JPostmanAnnotationEngine.runJUnit(testInstance, testMethod);
				invokeTestBodyAndVerifyRequest(invocation, testInstance, testMethod);
			}
			if (JPostmanAnnotationEngine.verificationSkipRequested()) {
				throw new TestAbortedException(JPostmanAnnotationEngine.verificationSkipMessage(testMethod));
			}
			printPassed(extensionContext, testMethod);
		} catch (Throwable error) {
			if (!(JPostmanStackTraceCleaner.rootCause(error) instanceof TestAbortedException)) {
				JPostmanAnnotationEngine.recordFinalFailure(testInstance, testMethod, error);
			}
			Throwable cleaned = JPostmanAnnotationEngine.cleanJUnitFailure(testInstance, testMethod, error);
			if (cleaned instanceof TestAbortedException) {
				JPostmanAnnotationEngine.recordFinalSkip(testInstance, testMethod);
			}
			if (!(cleaned instanceof TestAbortedException)
					&& !JPostmanAnnotationEngine.reportControlsFailureOutput(testInstance)) {
				printFailure(extensionContext, testMethod, cleaned, null);
			}
			throw cleaned;
		} finally {
			JPostmanAnnotationEngine.clearVerificationOutcome();
			JUnitContext.clearCurrent();
		}
	}

	private boolean hasRunnerDependencyLauncher(Method testMethod) {
		io.jpostman.annotations.JPostmanRunner runner = JPostmanAnnotations.runner(testMethod);
		if (!isRunnerDependencyLauncher(runner)) {
			return false;
		}
		return findRunnerDependency(testMethod.getDeclaringClass(), firstDependency(runner)) != null;
	}

	private boolean isRunnerDependencyLauncher(io.jpostman.annotations.JPostmanRunner runner) {
		return runner != null && dependencies(runner).length == 1 && isBlank(runner.namespace())
				&& isEmpty(runner.folder()) && isBlank(runner.rule()) && isBlank(runner.executor())
				&& isBlank(runner.data()) && isEmpty(runner.include()) && isEmpty(runner.exclude())
				&& isEmpty(runner.filter()) && isEmpty(runner.asserts()) && runner.verify() == -1
				&& !runner.lifecycle();
	}

	private String firstDependency(io.jpostman.annotations.JPostmanRunner runner) {
		String[] dependencies = dependencies(runner);
		return dependencies.length == 0 ? "" : dependencies[0];
	}

	private String[] dependencies(io.jpostman.annotations.JPostmanRunner runner) {
		if (runner == null || runner.dependsOn() == null) {
			return new String[0];
		}
		return java.util.Arrays.stream(runner.dependsOn()).filter(v -> v != null && !v.isBlank()).map(String::trim)
				.toArray(String[]::new);
	}

	private Method findRunnerDependency(Class<?> type, String reference) {
		String value = reference == null ? "" : reference.trim();
		if (value.isBlank()) {
			return null;
		}
		boolean idReference = value.startsWith("#");
		String id = idReference ? value.substring(1).trim() : value;
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				io.jpostman.annotations.JPostmanRunner runner = JPostmanAnnotations.runner(method);
				if (runner == null) {
					continue;
				}
				if ((idReference && id.equals(annotationId(runner.id())))
						|| (!idReference && value.equals(method.getName()))) {
					return method;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private String annotationId(String value) {
		String id = value == null ? "" : value.trim();
		return id.startsWith("#") ? id.substring(1).trim() : id;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private boolean isEmpty(String[] values) {
		if (values == null || values.length == 0) {
			return true;
		}
		for (String value : values) {
			if (!isBlank(value)) {
				return false;
			}
		}
		return true;
	}

	/** Cleans and prints failures thrown by {@code @BeforeAll}. */
	@Override
	public void interceptBeforeAllMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {

		Object testInstance = extensionContext.getTestInstance().orElse(null);
		Method method = invocationContext.getExecutable();

		try {
			if (testInstance != null) {
				setup(testInstance, extensionContext);
			}
			invocation.proceed();
		} catch (Throwable error) {
			Throwable cleaned = cleanLifecycleFailure(extensionContext, testInstance, method, error);
			printFailure(extensionContext, method, cleaned, "@BeforeAll");
			throw cleaned;
		}
	}

	/** Cleans and prints failures thrown by {@code @AfterAll}. */
	@Override
	public void interceptAfterAllMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {

		Object testInstance = extensionContext.getTestInstance().orElse(null);
		Method method = invocationContext.getExecutable();

		try {
			invocation.proceed();
		} catch (Throwable error) {
			Throwable cleaned = cleanLifecycleFailure(extensionContext, testInstance, method, error);
			printFailure(extensionContext, method, cleaned, "@AfterAll");
			throw cleaned;
		} finally {
			JUnitContext.clearCurrent();
		}
	}

	private void setup(Object testInstance, ExtensionContext context) throws Exception {
		if (testInstance == null || prepared.contains(testInstance)) {
			return;
		}

		try {
			JPostmanAnnotationEngine.setupJUnit(testInstance);
			prepared.add(testInstance);
		} catch (Throwable error) {
			Method method = firstMethod(testInstance);
			Throwable cleaned = JPostmanAnnotationEngine.cleanJUnitFailure(testInstance, method, error);
			printFailure(context, method, cleaned, "@BeforeAll");
			if (cleaned instanceof Exception) {
				throw (Exception) cleaned;
			}
			if (cleaned instanceof Error) {
				throw (Error) cleaned;
			}
			throw new RuntimeException(cleaned);
		}
	}

	private Throwable cleanLifecycleFailure(ExtensionContext context, Object testInstance, Method method,
			Throwable error) {
		Object instance = testInstance;
		if (instance == null) {
			instance = context.getTestInstance().orElse(null);
		}
		if (instance == null) {
			return error;
		}
		Method origin = JPostmanAnnotationEngine.lastAssertionMethod(instance);
		Method failureMethod = JPostmanStackTraceCleaner.rootCause(error) instanceof AssertionError && origin != null
				? origin
				: method;
		return JPostmanAnnotationEngine.cleanJUnitFailure(instance, failureMethod, error);
	}

	private Method firstMethod(Object testInstance) {
		Method[] methods = testInstance.getClass().getDeclaredMethods();
		return methods.length == 0 ? null : methods[0];
	}

	private void printPassed(ExtensionContext context, Method method) {
		if (!printFailures(context) || method == null) {
			return;
		}

		String text = "PASSED: " + context.getRequiredTestClass().getSimpleName() + "." + method.getName() + "\n";
		JPostmanOutputs.writeOrTrace(text);
	}

	private void printFailure(ExtensionContext context, Method method, Throwable failure, String lifecycle) {
		if (!printFailures(context) || failure == null) {
			return;
		}

		String className = context.getRequiredTestClass().getSimpleName();
		String methodName = method == null ? "JPostman automatic assertion verification" : method.getName();

		StringBuilder output = new StringBuilder();
		if (lifecycle == null || lifecycle.isBlank()) {
			output.append("FAILED: ");
		} else {
			output.append("FAILED CONFIGURATION: ").append(lifecycle).append(' ');
		}

		output.append(className).append('.').append(methodName).append('\n').append(failure.getClass().getName())
				.append(": ").append(String.valueOf(failure.getMessage())).append('\n');

		if (method == null) {
			String source = context.getRequiredTestClass().getSimpleName() + ".java";
			output.append("\tat ").append(className).append('.').append(methodName).append('(').append(source)
					.append(")\n");
		} else {
			for (StackTraceElement element : failure.getStackTrace()) {
				output.append("\tat ").append(element).append('\n');
			}
		}

		output.append('\n');
		JPostmanOutputs.writeOrTrace(output.toString());
	}

	private boolean printFailures(ExtensionContext context) {
		Class<?> testClass = context.getRequiredTestClass();

		JPostmanJUnit oldAnnotation = testClass.getAnnotation(JPostmanJUnit.class);
		if (oldAnnotation != null) {
			return oldAnnotation.printFailures();
		}

		JPostman.JUnit compactAnnotation = testClass.getAnnotation(JPostman.JUnit.class);
		return compactAnnotation != null && compactAnnotation.printFailures();
	}

	private void invokeTestBodyAndVerifyRequest(Invocation<Void> invocation, Object testInstance, Method testMethod)
			throws Throwable {
		JPostmanAnnotationEngine.beginAssertionCleanup(testInstance, testMethod);
		Throwable bodyFailure = null;
		Throwable verificationFailure = null;
		try {
			try {
				invocation.proceed();
			} catch (Throwable error) {
				bodyFailure = error;
			}

			try {
				JPostmanAnnotationEngine.verifyRequestAssertions(testInstance, testMethod);
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
			throw bodyFailure;
		}
		if (verificationFailure != null) {
			throw verificationFailure;
		}
	}

	private void invokeTestBodyWithAssertionCleanup(Invocation<Void> invocation, Object testInstance, Method testMethod,
			boolean[] proceeded) {
		JPostmanAnnotationEngine.beginAssertionCleanup(testInstance, testMethod);
		Throwable bodyFailure = null;
		Throwable verificationFailure = null;
		try {
			try {
				if (proceeded != null && proceeded.length > 0 && !proceeded[0]) {
					proceeded[0] = true;
					invocation.proceed();
				} else {
					testMethod.setAccessible(true);
					testMethod.invoke(testInstance);
				}
			} catch (InvocationTargetException error) {
				bodyFailure = error.getCause();
			} catch (Throwable error) {
				bodyFailure = error;
			}

			try {
				JPostmanAnnotationEngine.verifyExplicitSoftAssertions(testMethod);
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

	private static final class TestBodyFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private TestBodyFailureException(Throwable cause) {
			super(cause);
		}
	}
}
