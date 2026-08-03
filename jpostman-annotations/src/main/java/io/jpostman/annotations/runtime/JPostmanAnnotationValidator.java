package io.jpostman.annotations.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
	 * Only {@code @JPostmanRequest} methods are prohibited from also being
	 * framework {@code @Test} methods. Request methods are helper methods invoked
	 * by JPostman. Response, Runner, Call, and Executor annotations may be used on
	 * framework test methods when their respective runtime contracts allow it.
	 * </p>
	 *
	 * @param testClass test class to validate
	 */
	public static void validateTestClass(Class<?> testClass) {
		List<Method> invalidRequests = new ArrayList<>();

		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				collectInvalidMethod(method, invalidRequests);
			}
			current = current.getSuperclass();
		}

		throwIfInvalid(invalidRequests);
	}

	/**
	 * Validates a single framework test method.
	 *
	 * <p>
	 * This is useful for framework hooks, such as TestNG annotation transformation,
	 * that run before native parameter injection and can therefore replace
	 * framework injection errors with a clearer JPostman validation error.
	 * </p>
	 *
	 * @param method framework test method to validate
	 */
	public static void validateTestMethod(Method method) {
		List<Method> invalidRequests = new ArrayList<>();
		collectInvalidMethod(method, invalidRequests);
		throwIfInvalid(invalidRequests);
	}

	private static void collectInvalidMethod(Method method, List<Method> invalidRequests) {
		if (!isTestMethod(method)) {
			return;
		}

		JPostmanRequest request = JPostmanAnnotations.request(method);
		if (request != null) {
			invalidRequests.add(method);
		}
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

	private static void throwIfInvalid(List<Method> invalidRequests) {
		if (!invalidRequests.isEmpty()) {
			throw validationError(invalidRequests);
		}
	}

	private static AssertionError validationError(List<Method> invalidRequests) {
		StringBuilder message = new StringBuilder();
		message.append("Invalid JPostman annotation usage.").append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL)
				.append("@JPostmanRequest methods must not be annotated with @Test.").append(JPostmanErrors.ENDL)
				.append("They are request helper methods invoked by JPostman, not test methods invoked by the test framework.")
				.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid helper methods:")
				.append(JPostmanErrors.ENDL);

		for (Method method : invalidRequests) {
			message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
		}

		AssertionError error = new AssertionError(message.toString());
		error.setStackTrace(
				invalidRequests.stream().map(JPostmanAnnotationValidator::testFrame).toArray(StackTraceElement[]::new));
		return error;
	}

	private static String signature(Method method) {
		StringBuilder result = new StringBuilder();
		result.append(method.getDeclaringClass().getSimpleName()).append(".").append(method.getName()).append("(");

		Class<?>[] types = method.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			result.append(types[i].getSimpleName());
		}

		result.append(")");
		return result.toString();
	}

	private static StackTraceElement testFrame(Method method) {
		Class<?> type = method.getDeclaringClass();
		String fileName = type.getSimpleName() + ".java";
		int line = JPostmanStackTraceCleaner.findSourceLine(type, method.getName());

		return new StackTraceElement(type.getName(), method.getName(), fileName, line);
	}
}
