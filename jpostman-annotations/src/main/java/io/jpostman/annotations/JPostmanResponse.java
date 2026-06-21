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

	String namespace() default "";

	String folder() default "";

	String request();

	String rule() default "";

	String[] dependsOn() default {};

	String cache() default "";

	int verify() default 200;

	String executor() default "";
}
