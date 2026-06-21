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

	String namespace() default "";

	String folder() default "";

	String request();

	String rule() default "";

	String[] dependsOn() default {};

	String cache() default "";
}
