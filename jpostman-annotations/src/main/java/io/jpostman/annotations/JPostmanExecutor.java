package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.jpostman.annotations.runtime.JPostmanInfo;

/**
 * Marks a method that provides an {@code ApiExecutor} for annotation-based
 * request execution.
 *
 * <p>
 * Executor methods are used by {@link JPostmanResponse#executor()} and
 * {@link JPostmanRunner#executor()} through a unique executor {@link #id()}.
 * They can also declare dependencies that must run before the executor is
 * created. Supported method signatures are: context only, or context and
 * {@link JPostmanInfo}.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanExecutor {

	/**
	 * Unique executor id referenced by {@link JPostmanResponse#executor()} and
	 * {@link JPostmanRunner#executor()}. Empty means this executor may be used as a
	 * default executor when no explicit executor id is requested.
	 *
	 * @return unique executor id, or empty string for a default executor candidate
	 */
	String id() default "";

	/**
	 * Dependency method names to run before creating this executor.
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};

	/**
	 * Reuse the executor instance returned by this method during the current
	 * annotation runner execution.
	 *
	 * <p>
	 * This is useful for executor implementations that keep browser/API session
	 * state, cookies, or connection context between requests. The default is
	 * {@code false}, so JPostman calls the executor method for each request.
	 * </p>
	 *
	 * @return {@code true} to reuse the same executor instance for the current run
	 */
	boolean session() default false;

	/**
	 * Local annotation log level for this executor.
	 *
	 * <p>
	 * Empty means inherit the {@link JPostmanContext#logLevel()} value. Non-empty
	 * values override the context log level for this executor invocation. Supported
	 * values are TRACE, DEBUG, INFO, WARN, and ERROR.
	 * </p>
	 *
	 * @return local log level, or empty string to inherit from the context
	 */
	String logLevel() default "";

}
