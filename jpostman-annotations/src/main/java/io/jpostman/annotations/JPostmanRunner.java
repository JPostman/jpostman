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
	 * Optional cache key for storing this method result when used as a dependency.
	 *
	 * <p>
	 * When empty, the annotated method name is used as the cache key.
	 * </p>
	 *
	 * @return cache key
	 */
	String cache() default "";

	/**
	 * Expected HTTP status code for each executed request.
	 *
	 * @return expected HTTP status code
	 */
	int verify() default 200;

	/**
	 * Named executor method to use. Empty means default execution.
	 *
	 * @return executor name
	 */
	String executor() default "";

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
