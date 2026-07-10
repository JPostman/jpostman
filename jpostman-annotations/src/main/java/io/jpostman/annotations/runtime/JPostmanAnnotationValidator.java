package io.jpostman.annotations.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.jpostman.annotations.JPostmanCall;
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
	 * @param testClass test class to validate
	 */
	public static void validateTestClass(Class<?> testClass) {
		List<Method> invalidHelpers = new ArrayList<>();
		List<Method> invalidResponses = new ArrayList<>();
		List<Method> invalidCalls = new ArrayList<>();
		List<Method> invalidCachedResponses = new ArrayList<>();
		List<Method> invalidCachedRequests = new ArrayList<>();

		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				collectInvalidMethod(method, invalidHelpers, invalidResponses, invalidCalls, invalidCachedResponses,
						invalidCachedRequests);
			}
			current = current.getSuperclass();
		}

		throwIfInvalid(invalidHelpers, invalidResponses, invalidCalls, invalidCachedResponses, invalidCachedRequests);
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
		List<Method> invalidCalls = new ArrayList<>();
		List<Method> invalidCachedResponses = new ArrayList<>();
		List<Method> invalidCachedRequests = new ArrayList<>();

		collectInvalidMethod(method, invalidHelpers, invalidResponses, invalidCalls, invalidCachedResponses,
				invalidCachedRequests);
		throwIfInvalid(invalidHelpers, invalidResponses, invalidCalls, invalidCachedResponses, invalidCachedRequests);
	}

	private static void collectInvalidMethod(Method method, List<Method> invalidHelpers, List<Method> invalidResponses,
			List<Method> invalidCalls, List<Method> invalidCachedResponses, List<Method> invalidCachedRequests) {
		JPostmanRequest request = JPostmanAnnotations.request(method);

		if (!isTestMethod(method)) {
			return;
		}

		if (request != null) {
			invalidHelpers.add(method);
		}

		JPostmanResponse response = JPostmanAnnotations.response(method);
		if (response != null && method.getParameterCount() > 0) {
			invalidResponses.add(method);
		}

		JPostmanCall call = JPostmanAnnotations.call(method);
		if (call != null && method.getParameterCount() > 0) {
			invalidCalls.add(method);
		}

		if (response != null && cacheRequested(response.cache())) {
			invalidCachedResponses.add(method);
		}
	}

	private static String value(String value) {
		return value == null ? "" : value.trim();
	}

	private static boolean cacheRequested(String cache) {
		return !JPostmanResponse.NO_CACHE.equals(value(cache));
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
			List<Method> invalidCalls, List<Method> invalidCachedResponses, List<Method> invalidCachedRequests) {
		if (!invalidHelpers.isEmpty() || !invalidResponses.isEmpty() || !invalidCalls.isEmpty()
				|| !invalidCachedResponses.isEmpty() || !invalidCachedRequests.isEmpty()) {
			throw validationError(invalidHelpers, invalidResponses, invalidCalls, invalidCachedResponses,
					invalidCachedRequests);
		}
	}

	private static AssertionError validationError(List<Method> invalidHelpers, List<Method> invalidResponses,
			List<Method> invalidCalls, List<Method> invalidCachedResponses, List<Method> invalidCachedRequests) {
		StringBuilder message = new StringBuilder();

		message.append("Invalid JPostman annotation usage.").append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL);

		if (!invalidHelpers.isEmpty()) {
			message.append("@JPostmanRequest methods must not be annotated with @Test.").append(JPostmanErrors.ENDL)
					.append("They are request helper methods invoked by JPostman, not test methods invoked by the test framework.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid helper methods:")
					.append(JPostmanErrors.ENDL);

			for (Method method : invalidHelpers) {
				message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
			}
		}

		if (!invalidHelpers.isEmpty() && (!invalidResponses.isEmpty() || !invalidCachedResponses.isEmpty()
				|| !invalidCachedRequests.isEmpty())) {
			message.append(JPostmanErrors.ENDL);
		}

		if (!invalidResponses.isEmpty()) {
			message.append("@JPostmanResponse methods annotated with @Test must not declare parameters.")
					.append(JPostmanErrors.ENDL)
					.append("The test framework invokes @Test methods directly and only supports its own parameter injection rules.")
					.append(JPostmanErrors.ENDL)
					.append("Use a no-argument @Test method, or move parameters to a JPostman helper method such as @JPostmanRequest.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid response methods:")
					.append(JPostmanErrors.ENDL);

			for (Method method : invalidResponses) {
				message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
			}
		}

		if (!invalidCalls.isEmpty()) {
			message.append("@JPostmanCall methods annotated with @Test must not declare parameters.")
					.append(JPostmanErrors.ENDL)
					.append("The test framework invokes @Test methods directly and only supports its own parameter injection rules.")
					.append(JPostmanErrors.ENDL)
					.append("Use jpostman.call((ctx, info) -> ...) inside the test body instead.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid call methods:")
					.append(JPostmanErrors.ENDL);

			for (Method method : invalidCalls) {
				message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
			}
		}

		if ((!invalidResponses.isEmpty() || !invalidCalls.isEmpty())
				&& (!invalidCachedResponses.isEmpty() || !invalidCachedRequests.isEmpty())) {
			message.append(JPostmanErrors.ENDL);
		}

		if (!invalidCachedResponses.isEmpty()) {
			message.append("@JPostmanResponse(cache) cannot be used with @Test.").append(JPostmanErrors.ENDL)
					.append("Remove @Test to use it as a cached dependency, or remove cache to keep it as a test.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid cached response methods:")
					.append(JPostmanErrors.ENDL);

			for (Method method : invalidCachedResponses) {
				message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
			}
		}

		if (!invalidCachedResponses.isEmpty() && !invalidCachedRequests.isEmpty()) {
			message.append(JPostmanErrors.ENDL);
		}

		if (!invalidCachedRequests.isEmpty()) {
			message.append("@JPostmanRequest(cache) requires a non-void return value.").append(JPostmanErrors.ENDL)
					.append("Return the value to cache, or remove cache from the request method.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid cached request methods:")
					.append(JPostmanErrors.ENDL);

			for (Method method : invalidCachedRequests) {
				message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
			}
		}

		List<Method> invalid = Stream
				.concat(Stream.concat(Stream.concat(invalidHelpers.stream(), invalidResponses.stream()),
						invalidCalls.stream()),
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
