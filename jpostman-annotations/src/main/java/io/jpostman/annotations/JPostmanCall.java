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

	/** @return executor id */
	String executor() default "";

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
