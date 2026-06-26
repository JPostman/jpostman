package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Prepares and executes a Postman request before a test method runs.
 *
 * <p>
 * A response method is the top-level test entry point for a single request. It
 * may define request location values directly, or it may use
 * {@link #dependsOn()} to let a {@link JPostmanRequest} helper prepare request
 * data and location.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanResponse {

	/**
	 * Logical id for this response execution.
	 *
	 * @return response id, or empty string when not defined
	 */
	String id() default "";

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
	 * Request helper method to run after this response chain completes.
	 *
	 * @return next request helper method name
	 */
	String next() default "";

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
	 * Named executor method to use. Empty means default execution.
	 *
	 * @return executor id
	 */
	String executor() default "";

	/**
	 * Cache key used to store the context after this response runs.
	 *
	 * <p>
	 * Empty means the response context is not cached. When set, JPostman stores the
	 * active framework context after the response is executed, so later data
	 * expressions can read response values using {@code {{jpostman:path[key]}}}.
	 * </p>
	 *
	 * @return cache key, or empty string when response caching is disabled
	 */
	String cache() default "";

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
}