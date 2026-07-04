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
	 * Local annotation log output mode for this executor.
	 *
	 * <p>
	 * Empty means inherit the {@link JPostmanContext#debug()} value. Non-empty
	 * values override the context log output mode for this executor invocation.
	 * Supported values are none, request, response, info, and all. request,
	 * response, and info may be combined. none and all must be used alone. request,
	 * response, and info may be combined. none and all must be used alone.
	 * </p>
	 *
	 * @return local log output mode values, or empty to inherit from the context
	 */
	String[] logOutput() default {};

}
