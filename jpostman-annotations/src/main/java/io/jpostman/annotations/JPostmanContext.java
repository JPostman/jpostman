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
public @interface JPostmanContext {

	String namespace() default "";

	String config() default "classpath:jpostman.properties";

	String collection() default "";

	String environment() default "";

	String rules() default "";
}
