package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Executes multiple Postman requests from a collection folder before a test
 * method runs.
 *
 * <p>
 * This annotation behaves like {@link JPostmanResponse}, but the request names
 * are discovered from the selected Postman folder instead of being declared one
 * by one. Explicit {@link JPostmanResponse} methods for the same namespace,
 * folder, and request name are skipped by the runner.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanRunner {

	/**
	 * Optional annotation id used by dependsOn = "#id".
	 *
	 * @return annotation id
	 */
	String id() default "";

	/**
	 * Logical tags for this runner execution.
	 *
	 * @return runner tags, or empty array when not defined
	 */
	String[] tags() default {};

	/**
	 * Context namespace to use. Empty means the default context.
	 *
	 * @return context namespace
	 */
	String namespace() default "";

	/**
	 * Optional Postman folder name. Empty means root-level collection requests.
	 *
	 * @return Postman folder name
	 */
	String folder() default "";

	/**
	 * Optional secure rule section for response filtering and masking.
	 *
	 * @return secure rule section
	 */
	String rule() default "";

	/**
	 * Optional response fields to keep before printing or verifying.
	 *
	 * @return response fields to keep
	 */
	String[] filter() default {};

	/**
	 * Dependency method names to run before this runner.
	 *
	 * <p>
	 * For runner launcher methods, a single runner dependency such as
	 * {@code dependsOn = "#testRunner"} can reuse the referenced runner body with
	 * this annotation's tags when this runner does not define its own folder,
	 * include/exclude, executor, rule, filter, data, asserts, verify, soft, or
	 * lifecycle settings.
	 * </p>
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};

	/**
	 * Request names to include. Empty means include all discovered requests.
	 *
	 * @return request names to include
	 */
	String[] include() default {};

	/**
	 * Request names to exclude from this runner.
	 *
	 * @return request names to exclude
	 */
	String[] exclude() default {};

	/**
	 * Expected HTTP status code for each executed request.
	 *
	 * <p>
	 * The default value {@code -1} uses {@link JPostmanContext#verifyStatusCode()}.
	 * Use {@code 0} to explicitly skip status-code verification for this runner,
	 * even when the context has a default expected status code. Set a concrete
	 * value when each runner request should be verified by the annotation runtime.
	 * </p>
	 *
	 * @return expected HTTP status code, {@code -1} to use the context default, or
	 *         {@code 0} to skip status-code verification for this runner
	 */
	int verify() default -1;

	/**
	 * @JPostmanExecutor id to use. Empty means default execution.
	 *
	 * @return executor id
	 */
	String executor() default "";

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

	/**
	 * Whether to use soft assertion verification.
	 *
	 * @return {@code true} to use soft assertions
	 */
	boolean soft() default false;

	/**
	 * Enables the new request/response runner lifecycle callback mode.
	 *
	 * <p>
	 * The default {@code false} keeps the original behavior: the runner method body
	 * is invoked only after each executed response. Set this to {@code true} when
	 * using {@code jpostman.runner().start(...)},
	 * {@code jpostman.runner().request(...)}, or
	 * {@code jpostman.runner().response(...)} and when the fluent runner chain
	 * should control the method body for the before/after phases.
	 * </p>
	 *
	 * @return {@code true} to enable before-request and response lifecycle
	 *         callbacks
	 */
	boolean lifecycle() default false;

	/**
	 * Optional data group or data section to apply before request execution.
	 *
	 * <p>
	 * Use this for request data loaded by {@link JPostmanContext#dataload()}. For
	 * example, {@code data = "product"} applies the product data group, while
	 * {@code data = "product.mouse"} applies an exact data section.
	 * </p>
	 *
	 * @return data group or section name, or empty string when no data should be
	 *         applied
	 */
	String data() default "";

	/**
	 * Optional assertion rule sections to apply after response execution.
	 *
	 * <p>
	 * Assertion files are loaded by {@link JPostmanContext#assertions()} or the
	 * {@code assertions} config property. This selector only chooses sections from
	 * those already-loaded files. Java reserves the word {@code assert}, so the
	 * annotation member is named {@code asserts}.
	 * </p>
	 *
	 * @return assertion rule sections, or empty array to use request-name/default
	 *         resolution
	 */
	String[] asserts() default {};

	/**
	 * Runs this runner even when {@link JPostmanContext#skipAll()} is enabled.
	 *
	 * @return {@code true} to opt in while skipAll is active
	 */
	boolean enabled() default false;

	/**
	 * Skips this runner/test execution before dependencies or request execution
	 * run.
	 *
	 * @return {@code true} to skip this runner/test execution
	 */
	boolean skip() default false;

}
