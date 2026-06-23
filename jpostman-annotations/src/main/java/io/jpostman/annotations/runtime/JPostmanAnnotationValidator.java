package io.jpostman.annotations.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;

/**
 * Validates annotation-based test classes before any test method executes.
 */
public final class JPostmanAnnotationValidator {

	private static final String JUNIT_TEST = "org.junit.jupiter.api.Test";
	private static final String TESTNG_TEST = "org.testng.annotations.Test";

	private JPostmanAnnotationValidator() {
	}

	/**
	 * Validates all JPostman annotation usage in the supplied test class.
	 *
	 * <p>
	 * {@code @JPostmanRequest} and {@code @JPostmanExecutor} methods are helper
	 * methods. They are called by JPostman and must not also be framework test
	 * methods.
	 * </p>
	 *
	 * @param testClass test class to validate
	 */
	public static void validateTestClass(Class<?> testClass) {
		List<Method> invalid = new ArrayList<>();

		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (isInvalidTestMethod(method)) {
					invalid.add(method);
				}
			}
			current = current.getSuperclass();
		}

		if (!invalid.isEmpty()) {
			throw validationError(invalid);
		}
	}

	private static boolean isInvalidTestMethod(Method method) {
		boolean jpostmanHelper = method.isAnnotationPresent(JPostmanRequest.class)
				|| method.isAnnotationPresent(JPostmanExecutor.class);

		return jpostmanHelper && isTestMethod(method);
	}

	private static boolean isTestMethod(Method method) {
		for (Annotation annotation : method.getAnnotations()) {
			String name = annotation.annotationType().getName();
			if (JUNIT_TEST.equals(name) || TESTNG_TEST.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static AssertionError validationError(List<Method> invalid) {
		StringBuilder message = new StringBuilder();

		message.append("Invalid JPostman annotation usage.").append(System.lineSeparator())
				.append(System.lineSeparator())
				.append("@JPostmanRequest and @JPostmanExecutor methods must not be annotated with @Test.")
				.append(System.lineSeparator())
				.append("They are helper methods used by @JPostmanResponse, @JPostmanRunner, or dependsOn.")
				.append(System.lineSeparator()).append(System.lineSeparator()).append("Invalid methods:")
				.append(System.lineSeparator());

		for (Method method : invalid) {
			message.append("- ").append(method.getDeclaringClass().getSimpleName()).append(".").append(method.getName())
					.append(System.lineSeparator());
		}

		AssertionError error = new AssertionError(message.toString());
		error.setStackTrace(
				invalid.stream().map(JPostmanAnnotationValidator::testFrame).toArray(StackTraceElement[]::new));

		return error;
	}

	private static StackTraceElement testFrame(Method method) {
		Class<?> type = method.getDeclaringClass();
		String fileName = type.getSimpleName() + ".java";
		int line = JPostmanStackTraceCleaner.findSourceLine(type, method.getName());

		return new StackTraceElement(type.getName(), method.getName(), fileName, line);
	}
}
