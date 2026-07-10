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

	/** @return collection folder name */
	String folder() default "";

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

	/** @return local automatic failure output mode */
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
