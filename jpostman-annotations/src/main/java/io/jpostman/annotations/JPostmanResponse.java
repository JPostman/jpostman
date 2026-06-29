package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Executes a prepared Postman request and exposes the response to the method
 * body.
 *
 * <p>
 * A response method is the top-level test entry point for a single request. It
 * may define request location values directly, or use {@link #dependsOn()} to
 * run one or more {@link JPostmanRequest} helpers first.
 * </p>
 *
 * <p>
 * Difference from {@link JPostmanRequest}: response methods execute the request
 * first, then the method body can read {@code ctx.response()}, assert values,
 * or return/cache data from the executed response.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanResponse {

	/**
	 * Internal sentinel used to distinguish omitted cache from explicit cache = "".
	 */
	String NO_CACHE = "__jpostman_no_cache__";

	/**
	 * Logical tags for this response execution.
	 *
	 * @return response tags, or empty array when not defined
	 */
	String[] tags() default {};

	/**
	 * Context namespace to use. Empty means default context.
	 *
	 * @return context namespace
	 */
	String namespace() default "";

	/**
	 * Optional Postman folder name. Empty means search by request name only.
	 *
	 * @return Postman folder name
	 */
	String folder() default "";

	/**
	 * Postman request name to prepare and execute.
	 *
	 * @return Postman request name
	 */
	String request() default "";

	/**
	 * Optional secure rule section for response filtering and masking.
	 *
	 * @return secure rule section name
	 */
	String rule() default "";

	/**
	 * Optional response fields to keep before printing or verifying.
	 *
	 * @return response fields to keep
	 */
	String[] filter() default {};

	/**
	 * Dependency method names to run before this response.
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};

	/**
	 * Expected HTTP status code.
	 *
	 * <p>
	 * A negative value uses {@link JPostmanContext#verifyStatusCode()}. When the
	 * context default is also negative, automatic status-code verification is
	 * disabled. This is the default so dependency-heavy flows do not accidentally
	 * verify a previous or unrelated response. Set a concrete value, such as
	 * {@code 200} or {@code 201}, when the response should be verified by the
	 * annotation runtime.
	 * </p>
	 *
	 * @return expected HTTP status code, or a negative value to use the context
	 *         default
	 */
	int verify() default -1;

	/**
	 * @JPostmanExecutor id to use. Empty means default execution.
	 *
	 * @return executor id
	 */
	String executor() default "";

	/**
	 * Cache key for this response dependency.
	 *
	 * <p>
	 * When omitted, this response is not cached. When set to an empty string,
	 * JPostman caches the dependency by the Java method name to prevent a second
	 * call in the same run. When set to a non-empty value, that value is used as
	 * the cache key. Non-void methods store their returned value; void methods
	 * store the executed framework context.
	 * </p>
	 *
	 * @return cache key, empty string to cache by method name, or {@link #NO_CACHE}
	 *         when omitted
	 */
	String cache() default NO_CACHE;

	/**
	 * Whether to attach secure log details to assertion failures.
	 *
	 * @return {@code true} to attach secure log details
	 */
	boolean log() default false;

	/**
	 * Whether to use soft assertion verification.
	 *
	 * @return {@code true} to use soft assertions
	 */
	boolean soft() default false;

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
	 * Local annotation log level for this response execution.
	 *
	 * <p>
	 * Empty means inherit the {@link JPostmanContext#logLevel()} value. Non-empty
	 * values override the context log level for this response invocation. Supported
	 * values are TRACE, DEBUG, INFO, WARN, and ERROR.
	 * </p>
	 *
	 * @return local log level, or empty string to inherit from the context
	 */
	String logLevel() default "";

	/**
	 * Runs this response even when {@link JPostmanContext#skipAll()} is enabled.
	 *
	 * @return {@code true} to opt in while skipAll is active
	 */
	boolean enabled() default false;

	/**
	 * Skips this response/test execution before dependencies or request execution
	 * run.
	 *
	 * @return {@code true} to skip this response/test execution
	 */
	boolean skip() default false;

	/**
	 * Optional reason used when this response/test execution is skipped.
	 *
	 * @return skip reason, or empty string when not provided
	 */
	String skipReason() default "";

}