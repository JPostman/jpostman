package io.jpostman.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Injects the loaded JPostman core context.
 *
 * <p>
 * Use this when the test needs direct access to the loaded Postman collection
 * or environment.
 * </p>
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface JPostmanContext {

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
	String config() default "classpath:jpostman.properties";

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
	 * Optional data files to load for {@link JPostmanData}.
	 *
	 * <p>
	 * Locations may use the same format as other JPostman resources, for example
	 * {@code classpath:product-data.ini} or a file-system path. Multiple files are
	 * loaded in declaration order, and later files override earlier sections.
	 * </p>
	 *
	 * @return data file locations
	 */
	String[] dataload() default {};

	/**
	 * Default HTTP status code used when a response or runner does not specify
	 * {@code verify}.
	 *
	 * <p>
	 * The default value is {@code 200}. This means executed
	 * {@link JPostmanResponse} and {@link JPostmanRunner} requests are verified as
	 * HTTP 200 unless they define their own {@code verify} value.
	 * </p>
	 *
	 * <p>
	 * Use a negative value, such as {@code -1}, to disable default status-code
	 * verification.
	 * </p>
	 *
	 * @return default expected status code, or a negative value to disable default
	 *         verification
	 */
	int verifyStatusCode() default 200;

	/**
	 * Default secure log behavior for JPostman response verification.
	 *
	 * <p>
	 * Note: Java boolean annotation values cannot distinguish between an omitted
	 * value and an explicit {@code false}. This value is treated as a class-level
	 * default that enables response logging when a response/runner also does not
	 * enable it.
	 * </p>
	 *
	 * @return {@code true} to enable default secure response logging
	 */
	boolean logs() default false;

	/**
	 * Annotation debug level. Supported values are TRACE, DEBUG, INFO, WARN, ERROR.
	 * Values are case-insensitive. INFO disables automatic annotation debug output.
	 *
	 * @return annotation debug level
	 */
	String debug() default "info";

	/**
	 * Format used when debug level is DEBUG or TRACE.
	 *
	 * <p>
	 * Uses {@link java.text.MessageFormat} syntax. Argument {@code {0}} is the
	 * current callee/method name. Argument {@code {1}} is the JPostman annotation
	 * name.
	 * </p>
	 *
	 * @return debug log format
	 */
	String debugFormat() default "=== {1}: {0} ===";
}