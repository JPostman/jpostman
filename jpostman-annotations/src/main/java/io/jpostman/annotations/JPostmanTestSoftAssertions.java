package io.jpostman.annotations;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.jpostman.secure.JPostmanSoftAssertions;

/**
 * Framework-neutral facade for soft assertions used by {@link JPostman.Test}.
 */
public interface JPostmanTestSoftAssertions extends JPostmanSoftAssertions<JPostman.Test, JPostmanTestSoftAssertions> {

	JPostmanTestSoftAssertions allMatch(String path, Predicate<Number> predicate);

	JPostmanTestSoftAssertions allMatch(String path, BiPredicate<Object, Integer> predicate);

	<V> JPostmanTestSoftAssertions allMatch(String path, Class<V> type, BiPredicate<V, Integer> predicate);
}
