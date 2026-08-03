package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a JPostman request executor provider or post-response
 * interceptor.
 *
 * <p>
 * Methods returning {@code ApiExecutor} provide request execution. Methods
 * returning {@code void} run after a response is received. A single method of a
 * role is selected automatically, even when it has an id. When multiple methods
 * of the same role are available, the method without an id is the default. Use
 * the annotation {@code executor} attribute only to select a named method, for
 * example {@code executor = "#audit"}.
 * </p>
 *
 * <p>
 * For void interceptors, an exact namespace match takes precedence over a
 * global interceptor whose namespace is empty.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanExecutor {

	/**
	 * Optional executor id. A single executor is selected automatically even when
	 * this id is set. When multiple executors of the same role are available, an
	 * empty id marks the default and a non-empty id can be selected with
	 * {@code executor = "#id"}.
	 *
	 * @return unique executor id, or empty string for the default executor
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
	 * Namespace where this void executor interceptor applies. Empty is the global
	 * fallback. An interceptor whose namespace exactly matches the active request
	 * namespace takes precedence over a global interceptor. When the
	 * selected/default interceptor is the only source of a namespace, its namespace
	 * becomes the effective request namespace before collection lookup. An
	 * explicitly declared request annotation namespace always takes precedence.
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
	 * Local JPostman debug override.
	 *
	 * <p>
	 * Use {@code debug} to inherit {@link JPostmanContext#debug()}. Use
	 * {@code none} to suppress output, {@code error} for the full failure trace,
	 * {@code request} for prepared-request diagnostics, {@code response} for
	 * received-response diagnostics, {@code info} for runtime annotation
	 * information, or {@code all} for all local diagnostics. Request, response,
	 * info, and all are printed after annotation execution for both passing and
	 * failing executions. The local {@code error} mode is different: it is printed
	 * only for failures and is deferred until after the JPostman report execution
	 * details.
	 * </p>
	 *
	 * @return local debug setting
	 */
	String debug() default "debug";

}
