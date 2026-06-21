package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method that returns an ApiExecutor for annotation-based response
 * execution.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanExecutor {

	String namespace() default "";

	String name() default "";

	String[] dependsOn() default {};
}
