package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Prepares and executes a Postman request response before a test method runs.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanResponse {

	/** Context namespace to use. Empty means default context. */
	String namespace() default "";

	/** Optional Postman folder name. Empty means search by request name only. */
	String folder() default "";

	/** Postman request name to prepare and execute. */
	String request();

	/** Optional secure rule section for response filtering and masking. */
	String rule() default "";

	/** Optional response fields to keep before printing or verifying. */
	String[] filter() default {};

	/** Dependency method names to run before this response. */
	String[] dependsOn() default {};

	/** Optional cache key for storing this method result. */
	String cache() default "";

	/** Expected HTTP status code. */
	int verify() default 200;

	/** Named executor method to use. Empty means default execution. */
	String executor() default "";

	/** Attach secure log details to assertion failures. */
	boolean log() default false;

	/** Use soft assertion verification. */
	boolean soft() default false;
}