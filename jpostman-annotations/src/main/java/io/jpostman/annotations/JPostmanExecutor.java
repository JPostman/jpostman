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
	 * Dependency method names or annotation ids to run before creating this
	 * executor. Use plain values for Java method names, or prefix ids with "#",
	 * such as dependsOn = "#login".
	 *
	 * @return dependency method names or "#id" references
	 */
	String[] dependsOn() default {};

	/**
	 * Namespace where this executor interceptor applies. Empty means all namespaces
	 * for void interceptors and the default executor provider for
	 * ApiExecutor-returning methods.
	 *
	 * @return namespace, or empty string
	 */
	String namespace() default "";

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
	 * Local automatic JPostman failure output mode. Values are single-choice; use
	 * one value only.
	 *
	 * <ul>
	 * <li>{@code none} - print only the minimum failure message and the first
	 * useful user-code stack frame.</li>
	 * <li>{@code debug} - print the configured debug output and use minimum failure
	 * output when debug is {@code none}.</li>
	 * <li>{@code error} - print the failure message and include the trace.</li>
	 * </ul>
	 *
	 * @return local automatic failure output mode
	 */
	String log() default "debug";

}
