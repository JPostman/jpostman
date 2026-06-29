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
	 * A negative value uses {@link JPostmanContext#verifyStatusCode()}. When the
	 * context default is also negative, automatic status-code verification is
	 * disabled. This is the default so runners can execute mixed request flows
	 * without assuming every response must be {@code 200}. Set a concrete value
	 * when each runner request should be verified by the annotation runtime.
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
	 * Local annotation debug level for this runner execution.
	 *
	 * <p>
	 * Empty means inherit the {@link JPostmanContext#debug()} value. Non-empty
	 * values override the context debug level for this runner invocation. Supported
	 * values are TRACE, DEBUG, INFO, WARN, and ERROR.
	 * </p>
	 *
	 * @return local debug level, or empty string to inherit from the context
	 */
	String logLevel() default "";

}
