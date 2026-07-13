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
	 * Use {@code 0} to explicitly skip status-code verification for this response,
	 * even when the context has a default expected status code. Set a concrete
	 * value, such as {@code 200} or {@code 201}, when the response should be
	 * verified by the annotation runtime.
	 * </p>
	 *
	 * @return expected HTTP status code, {@code -1} to use the context default, or
	 *         {@code 0} to skip status-code verification for this response
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