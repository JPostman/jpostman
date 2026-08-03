package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.jpostman.annotations.runtime.JPostmanInfo;

/**
 * Prepares request data before a request is executed.
 *
 * <p>
 * {@code @JPostmanRequest} is a setup/helper annotation. Its method body runs
 * before execution so it can update {@link JPostmanInfo} with body, query,
 * header, path, or authentication values. A {@link JPostmanResponse} can then
 * depend on this helper and execute the prepared request.
 * </p>
 *
 * <p>
 * Difference from {@link JPostmanResponse}: request helpers prepare data first;
 * response methods execute first and then allow the method body to read the
 * executed response.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanRequest {

	/**
	 * Internal sentinel used to distinguish omitted cache from explicit cache = "".
	 */
	String NO_CACHE = "__jpostman_no_cache__";

	/**
	 * Logical tags for this request helper.
	 *
	 * @return logical JPostman invocation tags
	 */
	String[] tags() default {};

	/**
	 * Optional annotation id used by dependsOn = "#id".
	 *
	 * @return unique annotation id, or empty string when not used
	 */
	String id() default "";

	/**
	 * Selects a named {@link JPostmanExecutor} by method name or {@code "#id"}.
	 * Leave empty to use the single executor or the executor without an id. The
	 * selector is stored in {@link JPostmanInfo} for the active dependency chain.
	 *
	 * @return executor selector, or empty string for automatic/default selection
	 */
	String executor() default "";

	/**
	 * Context namespace to use.
	 *
	 * <p>
	 * Empty means inherit from the parent chain. If there is no parent namespace,
	 * the default context is used.
	 * </p>
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
	 * Postman request name to prepare.
	 *
	 * <p>
	 * Empty means inherit from the parent chain. This allows helper methods to
	 * modify {@link JPostmanInfo#body} without repeating the same request name on
	 * every method in the chain.
	 * </p>
	 *
	 * @return Postman request name
	 */
	String request() default "";

	/**
	 * Secure rule section to load before preparing the request.
	 *
	 * @return secure rule section name
	 */
	String rule() default "";

	/**
	 * Dependency method names or annotation ids to run before this request helper.
	 * Use plain values for Java method names, or prefix ids with "#", such as
	 * dependsOn = "#login".
	 *
	 * @return dependency method names or "#id" references
	 */
	String[] dependsOn() default {};

	/**
	 * Cache key for this request helper dependency.
	 *
	 * <p>
	 * When omitted, this helper is not cached. When set to an empty string,
	 * JPostman uses the annotation id as the cache key when an id is present;
	 * otherwise it caches the dependency by the Java method name to prevent a
	 * second call in the same run. When set to a non-empty value, that value is
	 * used as the cache key. Non-void methods store their returned value; void
	 * methods store a marker only.
	 * </p>
	 *
	 * @return cache key, empty string to cache by annotation id or method name, or
	 *         {@link #NO_CACHE} when omitted
	 */
	String cache() default NO_CACHE;

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
