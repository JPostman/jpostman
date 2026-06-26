package io.jpostman.annotations.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;

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
	 * <p>
	 * {@code @JPostmanResponse} methods that are also framework {@code @Test}
	 * methods must not declare parameters. The test framework invokes the actual
	 * {@code @Test} method and cannot inject JPostman-specific parameters such as
	 * {@code JPostmanInfo}, {@code TestNgContext}, or {@code JUnitContext}.
	 * </p>
	 *
	 * <p>
	 * {@code @JPostmanResponse(cache = "...")} methods are dependency/helper
	 * methods and must not also be framework {@code @Test} methods.
	 * </p>
	 *
	 * <p>
	 * {@code @JPostmanRequest(cache = "...")} methods must return a value to cache.
	 * Void request helpers with cache are rejected because the cache would not
	 * contain the expected request result.
	 * </p>
	 *
	 * @param testClass test class to validate
	 */
	public static void validateTestClass(Class<?> testClass) {
		List<Method> invalidHelpers = new ArrayList<>();
		List<Method> invalidResponses = new ArrayList<>();
		List<Method> invalidCachedResponses = new ArrayList<>();
		List<Method> invalidCachedRequests = new ArrayList<>();

		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				collectInvalidMethod(method, invalidHelpers, invalidResponses, invalidCachedResponses,
						invalidCachedRequests);
			}
			current = current.getSuperclass();
		}

		throwIfInvalid(invalidHelpers, invalidResponses, invalidCachedResponses, invalidCachedRequests);
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
		List<Method> invalidHelpers = new ArrayList<>();
		List<Method> invalidResponses = new ArrayList<>();
		List<Method> invalidCachedResponses = new ArrayList<>();
		List<Method> invalidCachedRequests = new ArrayList<>();

		collectInvalidMethod(method, invalidHelpers, invalidResponses, invalidCachedResponses, invalidCachedRequests);
		throwIfInvalid(invalidHelpers, invalidResponses, invalidCachedResponses, invalidCachedRequests);
	}

	private static void collectInvalidMethod(Method method, List<Method> invalidHelpers, List<Method> invalidResponses,
			List<Method> invalidCachedResponses, List<Method> invalidCachedRequests) {
		JPostmanRequest request = method.getAnnotation(JPostmanRequest.class);
		if (request != null && !value(request.cache()).isBlank() && method.getReturnType() == Void.TYPE) {
			invalidCachedRequests.add(method);
		}

		if (!isTestMethod(method)) {
			return;
		}

		boolean jpostmanHelper = request != null || method.isAnnotationPresent(JPostmanExecutor.class);

		if (jpostmanHelper) {
			invalidHelpers.add(method);
		}

		JPostmanResponse response = method.getAnnotation(JPostmanResponse.class);
		if (response != null && method.getParameterCount() > 0) {
			invalidResponses.add(method);
		}

		if (response != null && !value(response.cache()).isBlank()) {
			invalidCachedResponses.add(method);
		}
	}

	private static String value(String value) {
		return value == null ? "" : value.trim();
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

	private static void throwIfInvalid(List<Method> invalidHelpers, List<Method> invalidResponses,
			List<Method> invalidCachedResponses, List<Method> invalidCachedRequests) {
		if (!invalidHelpers.isEmpty() || !invalidResponses.isEmpty() || !invalidCachedResponses.isEmpty()
				|| !invalidCachedRequests.isEmpty()) {
			throw validationError(invalidHelpers, invalidResponses, invalidCachedResponses, invalidCachedRequests);
		}
	}

	private static AssertionError validationError(List<Method> invalidHelpers, List<Method> invalidResponses,
			List<Method> invalidCachedResponses, List<Method> invalidCachedRequests) {
		StringBuilder message = new StringBuilder();

		message.append("Invalid JPostman annotation usage.").append(System.lineSeparator())
				.append(System.lineSeparator());

		if (!invalidHelpers.isEmpty()) {
			message.append("@JPostmanRequest and @JPostmanExecutor methods must not be annotated with @Test.")
					.append(System.lineSeparator())
					.append("They are helper methods invoked by JPostman, not test methods invoked by the test framework.")
					.append(System.lineSeparator()).append(System.lineSeparator()).append("Invalid helper methods:")
					.append(System.lineSeparator());

			for (Method method : invalidHelpers) {
				message.append("- ").append(signature(method)).append(System.lineSeparator());
			}
		}

		if (!invalidHelpers.isEmpty() && (!invalidResponses.isEmpty() || !invalidCachedResponses.isEmpty()
				|| !invalidCachedRequests.isEmpty())) {
			message.append(System.lineSeparator());
		}

		if (!invalidResponses.isEmpty()) {
			message.append("@JPostmanResponse methods annotated with @Test must not declare parameters.")
					.append(System.lineSeparator())
					.append("The test framework invokes @Test methods directly and only supports its own parameter injection rules.")
					.append(System.lineSeparator())
					.append("Use a no-argument @Test method, or move parameters to a JPostman helper method such as @JPostmanRequest or @JPostmanExecutor.")
					.append(System.lineSeparator()).append(System.lineSeparator()).append("Invalid response methods:")
					.append(System.lineSeparator());

			for (Method method : invalidResponses) {
				message.append("- ").append(signature(method)).append(System.lineSeparator());
			}
		}

		if (!invalidResponses.isEmpty() && (!invalidCachedResponses.isEmpty() || !invalidCachedRequests.isEmpty())) {
			message.append(System.lineSeparator());
		}

		if (!invalidCachedResponses.isEmpty()) {
			message.append("@JPostmanResponse(cache) cannot be used with @Test.").append(System.lineSeparator())
					.append("Remove @Test to use it as a cached dependency, or remove cache to keep it as a test.")
					.append(System.lineSeparator()).append(System.lineSeparator())
					.append("Invalid cached response methods:").append(System.lineSeparator());

			for (Method method : invalidCachedResponses) {
				message.append("- ").append(signature(method)).append(System.lineSeparator());
			}
		}

		if (!invalidCachedResponses.isEmpty() && !invalidCachedRequests.isEmpty()) {
			message.append(System.lineSeparator());
		}

		if (!invalidCachedRequests.isEmpty()) {
			message.append("@JPostmanRequest(cache) requires a non-void return value.").append(System.lineSeparator())
					.append("Return the value to cache, or remove cache from the request method.")
					.append(System.lineSeparator()).append(System.lineSeparator())
					.append("Invalid cached request methods:").append(System.lineSeparator());

			for (Method method : invalidCachedRequests) {
				message.append("- ").append(signature(method)).append(System.lineSeparator());
			}
		}

		List<Method> invalid = Stream
				.concat(Stream.concat(invalidHelpers.stream(), invalidResponses.stream()),
						Stream.concat(invalidCachedResponses.stream(), invalidCachedRequests.stream()))
				.distinct().collect(Collectors.toList());

		AssertionError error = new AssertionError(message.toString());
		error.setStackTrace(
				invalid.stream().map(JPostmanAnnotationValidator::testFrame).toArray(StackTraceElement[]::new));

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
