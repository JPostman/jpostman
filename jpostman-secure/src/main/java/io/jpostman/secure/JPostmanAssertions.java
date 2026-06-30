package io.jpostman.secure;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Shared assertion contract used by the JPostman TestNG and JUnit modules.
 * <p>
 * Framework-specific assertion classes, such as TestNgAssertions and
 * JUnitAssertions, should implement this interface to expose the same fluent
 * assertion API from both modules. This keeps assertion autocomplete and facade
 * behavior consistent for direct TestNG/JUnit usage and for the annotations
 * module through {@code JPostman.Assertions}.
 * <p>
 * The generic parameters keep chaining strongly typed:
 * <ul>
 * <li>{@code C} - owning context type, for example TestNgContext or
 * JUnitContext</li>
 * <li>{@code A} - concrete assertion type returned after each assertion
 * call</li>
 * </ul>
 */
public interface JPostmanAssertions<C, A> {

	C context();

	A isEqual(Object actual, Object expected);

	A isEqual(Object actual, Object expected, String message);

	A isNotEqual(Object actual, Object expected);

	A isNotEqual(Object actual, Object expected, String message);

	A isTrue(boolean condition);

	A isTrue(boolean condition, String message);

	A isFalse(boolean condition);

	A isFalse(boolean condition, String message);

	A isNull(Object value);

	A isNull(Object value, String message);

	A isNotNull(Object value);

	A isNotNull(Object value, String message);

	A statusCode(int expected);

	A statusCode(int expected, String message);

	A exists(String path);

	A exists(String path, String message);

	A notExists(String path);

	A notExists(String path, String message);

	A pathEquals(String path, Object expected);

	A pathEquals(String path, Object expected, String message);

	A pathNotNull(String path);

	A pathNotNull(String path, String message);

	A allMatch(String path, Predicate<Number> predicate, String message);

	A allMatch(String path, BiPredicate<Object, Integer> predicate, String message);

	<V> A allMatch(String path, Class<V> type, BiPredicate<V, Integer> predicate, String message);

	C verify();

	C verify(int statusCode);
}
