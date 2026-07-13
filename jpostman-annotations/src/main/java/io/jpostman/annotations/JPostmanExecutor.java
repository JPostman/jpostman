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
	 * Controls local automatic annotation output and failure formatting.
	 *
	 * <p>
	 * The value may contain one failure mode and optional explicit output
	 * selections, separated by commas.
	 * </p>
	 *
	 * <ul>
	 * <li>{@code none} - suppress normal automatic annotation output and use the
	 * minimum failure message with the first useful user-code stack frame.</li>
	 * <li>{@code debug} - inherit the active context {@code debug} configuration;
	 * when it resolves to {@code none}, use minimum failure output.</li>
	 * <li>{@code error} - suppress normal automatic annotation output and print the
	 * failure message with its trace.</li>
	 * <li>{@code request} - explicitly print the prepared request for this
	 * annotation.</li>
	 * <li>{@code response} - explicitly print the received response for this
	 * annotation.</li>
	 * <li>{@code info} - explicitly print runtime annotation information for this
	 * annotation.</li>
	 * <li>{@code all} - explicitly print request, response, and info output for
	 * this annotation.</li>
	 * </ul>
	 *
	 * <p>
	 * Explicit {@code request}, {@code response}, and {@code info} selections
	 * override the context debug output for this annotation and may be combined.
	 * {@code all} selects all three and must be used alone as an output selection.
	 * Only one failure mode ({@code none}, {@code debug}, or {@code error}) may be
	 * supplied. For example, {@code log = "debug"} inherits the context
	 * configuration, {@code log = "info"} prints only info, and
	 * {@code log = "request,response"} prints both request and response.
	 * </p>
	 *
	 * @return local automatic output and failure mode
	 */
	String log() default "debug";

}
