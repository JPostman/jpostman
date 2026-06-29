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
	 * @JPostmanExecutor method name associated with this request helper.
	 *
	 *                   <p>
	 *                   Empty means the default executor. This executor id is
	 *                   stored in {@link JPostmanInfo} so helper methods can see
	 *                   which executor is active for the chain.
	 *                   </p>
	 *
	 * @return executor id
	 */
	String executor() default "";

	/**
	 * Context namespace to use.
	 *
	 * <p>
	 * Empty means inherit from the caller. If there is no caller namespace, the
	 * default context is used.
	 * </p>
	 *
	 * @return context namespace
	 */
	String namespace() default "";

	/**
	 * Postman folder name to search before resolving the request.
	 *
	 * <p>
	 * Empty means inherit from the caller. If no folder is available, the
	 * collection root is searched by request name.
	 * </p>
	 *
	 * @return Postman folder name
	 */
	String folder() default "";

	/**
	 * Postman request name to prepare.
	 *
	 * <p>
	 * Empty means inherit from the caller. This allows helper methods to modify
	 * {@link JPostmanInfo#body} without repeating the same request name on every
	 * method in the chain.
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
	 * Dependency method names to run before this request helper.
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};

	/**
	 * Cache key for this request helper dependency.
	 *
	 * <p>
	 * When omitted, this helper is not cached. When set to an empty string,
	 * JPostman caches the dependency by the Java method name to prevent a second
	 * call in the same run. When set to a non-empty value, that value is used as
	 * the cache key. Non-void methods store their returned value; void methods
	 * store a marker only.
	 * </p>
	 *
	 * @return cache key, empty string to cache by method name, or {@link #NO_CACHE}
	 *         when omitted
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
	 * Local annotation debug level for this request helper.
	 *
	 * <p>
	 * Empty means inherit the {@link JPostmanContext#debug()} value. Non-empty
	 * values override the context debug level for this request invocation.
	 * Supported values are TRACE, DEBUG, INFO, WARN, and ERROR.
	 * </p>
	 *
	 * @return local debug level, or empty string to inherit from the context
	 */
	String logLevel() default "";

}
