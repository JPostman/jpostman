package io.jpostman.junit;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import org.junit.jupiter.api.Assertions;

import io.jpostman.secure.JPostmanAssertionError;
import io.jpostman.secure.SecureResponse;

/**
 * Fluent JUnit assertions with optional secure log output.
 *
 * <p>
 * Hard assertions run immediately. Soft assertions override the assertion
 * execution hook and verify collected failures later.
 * </p>
 */
public class JUnitAssertions<T extends JUnitAssertions<T>> {

	protected final JUnitContext context;
	protected final boolean includeLog;
	protected boolean statusCodeAsserted;

	JUnitAssertions(JUnitContext context, boolean includeLog) {
		this.context = context;
		this.includeLog = includeLog;
	}

	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}

	boolean soft() {
		return false;
	}

	/**
	 * Returns the owning context for continuing the fluent chain.
	 *
	 * @return owning JUnit context
	 */
	public JUnitContext context() {
		return context;
	}

	/**
	 * Asserts that the actual value is equal to the expected value.
	 *
	 * @param actual   the actual value
	 * @param expected the expected value
	 * @return this assertion helper
	 */
	public T isEqual(Object actual, Object expected) {
		return isEqual(actual, expected, "Values should be equal");
	}

	/**
	 * Asserts that the actual value is equal to the expected value.
	 *
	 * @param actual   the actual value
	 * @param expected the expected value
	 * @param message  custom failure message
	 * @return this assertion helper
	 */
	public T isEqual(Object actual, Object expected, String message) {
		return assertWithLog(() -> Assertions.assertEquals(expected, actual, message(message)));
	}

	/**
	 * Asserts that the actual value is not equal to the expected value.
	 *
	 * @param actual   the actual value
	 * @param expected the expected value
	 * @return this assertion helper
	 */
	public T isNotEqual(Object actual, Object expected) {
		return isNotEqual(actual, expected, "Values should not be equal");
	}

	/**
	 * Asserts that the actual value is not equal to the expected value.
	 *
	 * @param actual   the actual value
	 * @param expected the expected value
	 * @param message  custom failure message
	 * @return this assertion helper
	 */
	public T isNotEqual(Object actual, Object expected, String message) {
		return assertWithLog(() -> Assertions.assertNotEquals(expected, actual, message(message)));
	}

	/**
	 * Asserts that the condition is true.
	 *
	 * @param condition the condition to check
	 * @return this assertion helper
	 */
	public T isTrue(boolean condition) {
		return isTrue(condition, "Condition should be true");
	}

	/**
	 * Asserts that the condition is true.
	 *
	 * @param condition the condition to check
	 * @param message   custom failure message
	 * @return this assertion helper
	 */
	public T isTrue(boolean condition, String message) {
		return assertWithLog(() -> Assertions.assertTrue(condition, message(message)));
	}

	/**
	 * Asserts that the condition is false.
	 *
	 * @param condition the condition to check
	 * @return this assertion helper
	 */
	public T isFalse(boolean condition) {
		return isFalse(condition, "Condition should be false");
	}

	/**
	 * Asserts that the condition is false.
	 *
	 * @param condition the condition to check
	 * @param message   custom failure message
	 * @return this assertion helper
	 */
	public T isFalse(boolean condition, String message) {
		return assertWithLog(() -> Assertions.assertFalse(condition, message(message)));
	}

	/**
	 * Asserts that the value is null.
	 *
	 * @param value the value to check
	 * @return this assertion helper
	 */
	public T isNull(Object value) {
		return isNull(value, "Value should be null");
	}

	/**
	 * Asserts that the value is null.
	 *
	 * @param value   the value to check
	 * @param message custom failure message
	 * @return this assertion helper
	 */
	public T isNull(Object value, String message) {
		return assertWithLog(() -> Assertions.assertNull(value, message(message)));
	}

	/**
	 * Asserts that the value is not null.
	 *
	 * @param value the value to check
	 * @return this assertion helper
	 */
	public T isNotNull(Object value) {
		return isNotNull(value, "Value should not be null");
	}

	/**
	 * Asserts that the value is not null.
	 *
	 * @param value   the value to check
	 * @param message custom failure message
	 * @return this assertion helper
	 */
	public T isNotNull(Object value, String message) {
		return assertWithLog(() -> Assertions.assertNotNull(value, message(message)));
	}

	/**
	 * Asserts that the current secure response has the expected status code.
	 *
	 * @param expected the expected status code
	 * @return this assertion helper
	 */
	public T statusCode(int expected) {
		return statusCode(expected, null);
	}

	/**
	 * Asserts that the current secure response has the expected status code.
	 *
	 * @param expected the expected status code
	 * @param message  custom failure message
	 * @return this assertion helper
	 */
	public T statusCode(int expected, String message) {
		statusCodeAsserted = true;
		return assertWithLog(() -> {
			SecureResponse response = requireResponse(message);
			Assertions.assertEquals(expected, response.statusCode(), message(message, "Status code mismatch:"));
		});
	}

	/**
	 * Asserts that the current secure response contains the given path.
	 *
	 * @param path the response path to check
	 * @return this assertion helper
	 */
	public T exists(String path) {
		return exists(path, null);
	}

	/**
	 * Asserts that the current secure response contains the given path.
	 *
	 * @param path    the response path to check
	 * @param message custom failure message
	 * @return this assertion helper
	 */
	public T exists(String path, String message) {
		return assertWithLog(() -> {
			SecureResponse response = requireResponse(message);
			if (!response.exists(path)) {
				Assertions.fail(message(message, "Path not found: " + path));
			}
		});
	}

	/**
	 * Asserts that the current secure response does not contain the given path.
	 *
	 * @param path the response path to check
	 * @return this assertion helper
	 */
	public T notExists(String path) {
		return notExists(path, null);
	}

	/**
	 * Asserts that the current secure response does not contain the given path.
	 *
	 * @param path    the response path to check
	 * @param message custom failure message
	 * @return this assertion helper
	 */
	public T notExists(String path, String message) {
		return assertWithLog(() -> {
			SecureResponse response = requireResponse(message);
			if (response.exists(path)) {
				Assertions.fail(message(message, "Path should not exist: " + path));
			}
		});
	}

	/**
	 * Asserts that the value at the given response path is equal to the expected
	 * value.
	 *
	 * @param path     the response path to check
	 * @param expected the expected value
	 * @return this assertion helper
	 */
	public T pathEquals(String path, Object expected) {
		return pathEquals(path, expected, null);
	}

	/**
	 * Asserts that the value at the given response path is equal to the expected
	 * value.
	 *
	 * @param path     the response path to check
	 * @param expected the expected value
	 * @param message  custom failure message
	 * @return this assertion helper
	 */
	public T pathEquals(String path, Object expected, String message) {
		return assertWithLog(() -> {
			SecureResponse response = requireResponse(message);
			if (!response.exists(path)) {
				Assertions.fail(message(message, "Path not found: " + path));
			}
			Assertions.assertEquals(expected, response.path(path), message(message, "Path value mismatch: " + path));
		});
	}

	/**
	 * Asserts that the value at the given response path is not null.
	 *
	 * @param path the response path to check
	 * @return this assertion helper
	 */
	public T pathNotNull(String path) {
		return pathNotNull(path, null);
	}

	/**
	 * Asserts that the value at the given response path is not null.
	 *
	 * @param path    the response path to check
	 * @param message custom failure message
	 * @return this assertion helper
	 */
	public T pathNotNull(String path, String message) {
		return assertWithLog(() -> {
			SecureResponse response = requireResponse(message);
			if (!response.exists(path)) {
				Assertions.fail(message(message, "Path not found: " + path));
			}
			Assertions.assertNotNull(response.path(path), message(message, "Path value is null: " + path));
		});
	}

	/**
	 * Asserts that every numeric value returned by the response path matches the
	 * predicate.
	 *
	 * @param path      response path that resolves to a list of numeric values
	 * @param predicate predicate applied to each value
	 * @param message   custom failure message. Use {} for the value.
	 * @return this assertion helper
	 */
	public T allMatch(String path, Predicate<Number> predicate, String message) {
		return allMatch(path, Number.class, (value, index) -> predicate.test(value), message);
	}

	/**
	 * Asserts that every numeric value returned by the response path matches the
	 * predicate.
	 *
	 * @param path      response path that resolves to a list of numeric values
	 * @param predicate predicate applied to each value and index
	 * @param message   custom failure message. Use {} placeholders for value and
	 *                  index.
	 * @return this assertion helper
	 */
	public T allMatch(String path, BiPredicate<Object, Integer> predicate, String message) {
		return allMatch(path, Object.class, predicate, message);
	}

	/**
	 * Asserts that every value returned by the response path matches the predicate.
	 *
	 * @param <V>       expected value type
	 * @param path      response path that resolves to a list of values
	 * @param type      expected value type
	 * @param predicate predicate applied to each value and index
	 * @param message   custom failure message. Use {} placeholders for value and
	 *                  index.
	 * @return this assertion helper
	 */
	public <V> T allMatch(String path, Class<V> type, BiPredicate<V, Integer> predicate, String message) {
		requireResponse(message);
		List<?> values = context.paths(path);

		for (int i = 0; i < values.size(); i++) {
			final int index = i;
			final Object value = values.get(i);

			assertWithLog(() -> {
				if (!type.isInstance(value)) {
					Assertions.fail(format("Path value has wrong type. Expected: {}, Value: {}, Index: {}",
							type.getSimpleName(), value, index));
				}

				V typedValue = type.cast(value);
				if (!predicate.test(typedValue, index)) {
					Assertions.fail(valueIndexMessage(message(message, "Path value did not match"), typedValue, index));
				}
			});
		}

		return self();
	}

	/**
	 * Verifies status code {@code 200} and returns the test context.
	 *
	 * <p>
	 * This allows assertion chains to finish back on the context.
	 * </p>
	 *
	 * @return current test context
	 */
	public JUnitContext verify() {
		return verify(200);
	}

	/**
	 * Verifies the expected status code and returns the test context.
	 *
	 * <p>
	 * In hard mode, this fails immediately on status code mismatch. In soft mode,
	 * collected soft assertions are verified and the expected status code is added
	 * only when no status code assertion was already collected.
	 * </p>
	 *
	 * @param statusCode expected status code
	 * @return current test context
	 */
	public JUnitContext verify(int statusCode) {
		statusCode(statusCode);
		return context;
	}

	/**
	 * Executes an assertion and wraps failures with optional secure log context.
	 *
	 * @param assertion assertion to execute
	 * @return this assertion helper
	 */
	protected T assertWithLog(Runnable assertion) {
		try {
			assertion.run();
		} catch (AssertionError error) {
			throw wrap(error);
		}
		return self();
	}

	/**
	 * Wraps an assertion error with optional secure log context.
	 *
	 * @param error original assertion error
	 * @return wrapped assertion error
	 */
	protected AssertionError wrap(AssertionError error) {
		return JPostmanAssertionError.wrap(error, includeLog ? context.log() : null);
	}

	private static String valueIndexMessage(String message, Object value, int index) {
		String resolved = message(message, "Path value did not match");
		if (resolved.contains("{}")) {
			return format(resolved, value, index);
		}
		return format(resolved + ". Value: {}, Index: {}", value, index);
	}

	private static String format(String message, Object... args) {
		String result = message;
		for (Object arg : args) {
			result = result.replaceFirst("\\{}", Matcher.quoteReplacement(String.valueOf(arg)));
		}
		return result;
	}

	private SecureResponse requireResponse(String message) {
		SecureResponse response = context.response();
		if (response == null) {
			Assertions.fail(message(message, "Secure response is not set"));
		}
		return response;
	}

	private static String message(String message) {
		return message == null || message.isBlank() ? null : message;
	}

	private static String message(String message, String defaultMessage) {
		return message == null || message.isBlank() ? defaultMessage : message;
	}
}
