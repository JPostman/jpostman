package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Loads dynamic request data into {@link JPostmanInfo#params}.
 *
 * <p>
 * Use this annotation on a {@code @JPostmanResponse}, {@code @JPostmanRequest},
 * or {@code @JPostmanRunner} method when the request body values should come
 * from an external data file instead of Java switch/case code.
 * </p>
 *
 * <p>
 * Data files are configured with {@link JPostmanContext#dataload()}. The
 * selected section is copied into {@code info.params} before the request is
 * executed.
 * </p>
 *
 * <p>
 * Data values may reference runtime values with
 * {@code {{jpostman:source[key]}}}. Supported sources are {@code env},
 * {@code secret}, {@code plain}, {@code cache}, {@code path}, {@code param},
 * and {@code params}.
 * </p>
 *
 * <p>
 * Cached response/context values may also be read with
 * {@code {{jpostman:[cacheKey]path}}}, for example
 * {@code {{jpostman:[user]firstName}}} or
 * {@code {{jpostman:[user]/&#42;&#42;/lastName}}}. Expressions are resolved
 * before map, JSON, or XML values are parsed.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanData {

	/**
	 * Data section name to load.
	 *
	 * <p>
	 * Empty means use the current JPostman execution id, usually
	 * {@link JPostmanResponse#id()}, and then fall back to
	 * {@link JPostmanInfo#callerId}.
	 * </p>
	 *
	 * @return data section name
	 */
	String section() default "";

	/**
	 * Shorthand alias for {@link #section()} so users may write
	 * {@code @JPostmanData("mouse")}.
	 *
	 * @return data section name
	 */
	String value() default "";

	/**
	 * Optional data namespace.
	 *
	 * <p>
	 * When set, JPostman first searches for a section named
	 * {@code namespace.section}. If that section is not found, it falls back to the
	 * plain section name.
	 * </p>
	 *
	 * @return data namespace
	 */
	String namespace() default "";

	/**
	 * Dependency method names to run before loading this data section.
	 *
	 * <p>
	 * Use this when data expressions require values cached by another JPostman
	 * method, for example {@code {{jpostman:[user]firstName}}}. Dependencies run
	 * before this annotation copies data into {@link JPostmanInfo#params}.
	 * </p>
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};
}
