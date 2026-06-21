package io.jpostman.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Injects a JPostman framework context into a test class field.
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface JPostmanTestContext {

	/** Context namespace to use. Empty means default context. */
	String namespace() default "";

	/** Properties file to load context locations from. */
	String config() default "classpath:jpostman.properties";

	/** Optional Postman collection location. */
	String collection() default "";

	/** Optional Postman environment location. */
	String environment() default "";

	/** Optional secure rules location. */
	String rules() default "";
}
