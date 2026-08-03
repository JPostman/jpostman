package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a test method that will execute one annotated request manually through
 * {@code JPostman.Runtime.call(...)}.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanCall {

	/** @return tags used by the manual call */
	String[] tags() default {};

	/** @return optional annotation id used by dependsOn = "#id" */
	String id() default "";

	/** @return request namespace */
	String namespace() default "";

	/**
	 * Collection folder path from the collection root to the target folder.
	 *
	 * <p>
	 * Each value represents one folder level. An empty value selects requests from
	 * the collection root.
	 * </p>
	 *
	 * <pre>
	 * folder = "Products"
	 * folder = { "level1", "level2", "level3" }
	 * </pre>
	 *
	 * @return folder path levels from parent to child
	 */
	String[] folder() default "";

	/** @return collection request name */
	String request() default "";

	/** @return rules section name */
	String rule() default "";

	/** @return fields to keep in the context */
	String[] filter() default {};

	/** @return dependency method names or "#id" references */
	String[] dependsOn() default {};

	/**
	 * Expected HTTP status code. Verification runs after
	 * {@code JPostman.Runtime.call()} completes the request.
	 *
	 * @return expected HTTP status code, {@code -1} to use the context default,
	 *         {@code 0} to pass without status verification, or {@code 1} to mark
	 *         the completed test skipped
	 */
	int verify() default -1;

	/**
	 * Selects a named {@link JPostmanExecutor} by method name or {@code "#id"}.
	 * Leave empty to use the single executor or the executor without an id.
	 *
	 * @return executor selector, or empty string for automatic/default selection
	 */
	String executor() default "";

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

	/** @return data section name */
	String data() default "";

	/**
	 * Runs this call even when {@link JPostmanContext#skipAll()} is enabled.
	 *
	 * @return {@code true} to opt in while skipAll is active
	 */
	boolean enabled() default false;

	/** @return true to skip this call */
	boolean skip() default false;

}
