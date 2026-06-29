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

	/**
	 * Context namespace to use. Empty means default context.
	 *
	 * @return context namespace
	 */
	String namespace() default "";

	/**
	 * Properties file to load context locations from.
	 *
	 * @return context configuration location
	 */
	String config() default "classpath:jpostman.properties"; // JPostmanDataLoader.DEFAULT_CONFIG

	/**
	 * Optional Postman collection location.
	 *
	 * @return Postman collection location
	 */
	String collection() default "";

	/**
	 * Optional Postman environment location.
	 *
	 * @return Postman environment location
	 */
	String environment() default "";

	/**
	 * Optional secure rules location.
	 *
	 * @return secure rules location
	 */
	String rules() default "";

	/**
	 * When true, this field mirrors the latest active framework context produced by
	 * JPostman execution, regardless of namespace.
	 *
	 * @return {@code true} when this field should mirror the active context
	 */
	boolean active() default false;
}
