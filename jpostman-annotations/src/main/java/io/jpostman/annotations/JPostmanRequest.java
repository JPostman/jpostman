package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Prepares a Postman request before a test or helper method runs.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanRequest {

	/** Context namespace to use. Empty means default context. */
	String namespace() default "";

	/** Optional Postman folder name. Empty means search by request name only. */
	String folder() default "";

	/** Postman request name to prepare and execute. */
	String request();

	/** Optional secure rule section for response filtering and masking. */
	String rule() default "";

	/** Dependency method names to run before this response. */
	String[] dependsOn() default {};

	/** Optional cache key for storing this method result. */
	String cache() default "";
}
