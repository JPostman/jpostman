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
import io.jpostman.annotations.runtime.JPostmanAnnotationEngine;
import io.jpostman.annotations.runtime.JPostmanAnnotations;

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

	/** Clears the current JUnit context after the class completes. */
	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		JUnitContext.clearCurrent();
	}

	/**
	 * Runs JPostman request/response/runner annotations before the user test body.
	 */
	@Override
	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {

		Object testInstance = extensionContext.getRequiredTestInstance();
		Method testMethod = invocationContext.getExecutable();

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
				JPostmanAnnotationEngine.beginAssertionCleanup(testInstance, testMethod);
				try {
					invocation.proceed();
				} finally {
					JPostmanAnnotationEngine.endAssertionCleanup();
				}
			}
		} catch (Throwable error) {
			Throwable cleaned = JPostmanAnnotationEngine.cleanJUnitFailure(testInstance, testMethod, error);
			if (!(cleaned instanceof TestAbortedException)) {
				printFailure(extensionContext, cleaned);
			}
			throw cleaned;
		} finally {
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
				&& isEmpty(runner.filter()) && isEmpty(runner.asserts()) && runner.verify() == -1 && !runner.soft()
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
			printFailure(extensionContext, cleaned);
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
			printFailure(extensionContext, cleaned);
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
			Throwable cleaned = JPostmanAnnotationEngine.cleanJUnitFailure(testInstance, firstMethod(testInstance),
					error);
			printFailure(context, cleaned);
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
		return JPostmanAnnotationEngine.cleanJUnitFailure(instance, method, error);
	}

	private Method firstMethod(Object testInstance) {
		Method[] methods = testInstance.getClass().getDeclaredMethods();
		return methods.length == 0 ? null : methods[0];
	}

	private void printFailure(ExtensionContext context, Throwable failure) {
		if (!printFailures(context)) {
			return;
		}

		System.err.println(failure.getMessage());
		for (StackTraceElement element : failure.getStackTrace()) {
			System.err.println("\tat " + element);
		}
		System.err.println();
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

	private void invokeTestBodyWithAssertionCleanup(Invocation<Void> invocation, Object testInstance, Method testMethod,
			boolean[] proceeded) {
		JPostmanAnnotationEngine.beginAssertionCleanup(testInstance, testMethod);
		try {
			if (proceeded != null && proceeded.length > 0 && !proceeded[0]) {
				proceeded[0] = true;
				invocation.proceed();
			} else {
				testMethod.setAccessible(true);
				testMethod.invoke(testInstance);
			}
		} catch (InvocationTargetException error) {
			throw new TestBodyFailureException(error.getCause());
		} catch (Throwable error) {
			throw new TestBodyFailureException(error);
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	private static final class TestBodyFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private TestBodyFailureException(Throwable cause) {
			super(cause);
		}
	}
}
