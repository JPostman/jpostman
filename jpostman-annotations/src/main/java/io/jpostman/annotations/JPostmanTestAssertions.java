package io.jpostman.annotations;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.jpostman.secure.JPostmanAssertions;

/**
 * Framework-neutral facade for hard assertions used by {@link JPostman.Test}.
 */
public interface JPostmanTestAssertions extends JPostmanAssertions<JPostman.Test, JPostmanTestAssertions> {

	JPostmanTestAssertions allMatch(String path, Predicate<Number> predicate);

	JPostmanTestAssertions allMatch(String path, BiPredicate<Object, Integer> predicate);

	<V> JPostmanTestAssertions allMatch(String path, Class<V> type, BiPredicate<V, Integer> predicate);
}
