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
	 * Optional annotation id used by dependsOn = "#id".
	 *
	 * @return unique annotation id, or empty string when not used
	 */
	String id() default "";

	/**
	 * Context namespace to use. Empty means default context.
	 *
	 * @return context namespace
	 */
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

	/**
	 * Postman request name to prepare and execute.
	 *
	 * @return Postman request name
	 */
	String request() default "";

	/**
	 * Optional secure rule sections for response filtering and masking.
	 *
	 * @return secure rule section names
	 */
	String[] rules() default {};

	/**
	 * Optional response fields to keep before printing or verifying.
	 *
	 * @return response fields to keep
	 */
	String[] filter() default {};

	/**
	 * Dependency method names or annotation ids to run before this response. Use
	 * plain values for Java method names, or prefix ids with "#", such as dependsOn
	 * = "#login".
	 *
	 * @return dependency method names or "#id" references
	 */
	String[] dependsOn() default {};

	/**
	 * Expected HTTP status code.
	 *
	 * <p>
	 * The default value {@code -1} uses {@link JPostmanContext#verifyStatusCode()}.
	 * Use {@code 0} to explicitly skip status-code verification for this response
	 * and keep a successful result. Use {@code 1} to perform the same execution but
	 * mark the otherwise successful completed test as skipped. Set a concrete
	 * value, such as {@code 200} or {@code 201}, when the response should be
	 * verified by the annotation runtime.
	 * </p>
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
	 * Cache key for this response dependency.
	 *
	 * <p>
	 * When omitted, this response is not cached. When set to an empty string,
	 * JPostman uses the annotation id as the cache key when an id is present;
	 * otherwise it caches the dependency by the Java method name to prevent a
	 * second call in the same run. When set to a non-empty value, that value is
	 * used as the cache key. Non-void methods store their returned value; void
	 * methods store the executed framework context.
	 * </p>
	 *
	 * @return cache key, empty string to cache by annotation id or method name, or
	 *         {@link #NO_CACHE} when omitted
	 */
	String cache() default NO_CACHE;

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

}