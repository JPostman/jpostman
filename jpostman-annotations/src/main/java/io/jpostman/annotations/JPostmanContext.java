package io.jpostman.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.jpostman.annotations.runtime.JPostmanInfo;
import io.jpostman.annotations.runtime.JPostmanRuntime;

/**
 * Injects the loaded JPostman context.
 *
 * <p>
 * A field may use the core {@link io.jpostman.JPostman.Context} type when it
 * only needs direct access to the loaded Postman collection/environment. A
 * field may also use {@link JPostmanRuntime} or the compact
 * {@link io.jpostman.annotations.JPostman.Context} alias when it needs
 * annotation-runtime access such as the active framework context,
 * namespace-specific contexts, or the current {@link JPostmanInfo}.
 * </p>
 *
 * <pre>
 * {@code @JPostmanContext(collection = "classpath:collection.json")
 * private JPostmanRuntime<TestNgContext> jctx;
 *
 * // Or with the compact facade import:
 * private io.jpostman.annotations.JPostman.Runtime<TestNgContext> jctx;
 *
 * jctx.ctx(); // default framework context
 * jctx.ctx("product"); // namespace-specific framework context
 * jctx.info(); // current JPostmanInfo
 * }
 * </pre>
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
	 * Optional data files to load for annotation data resolvers.
	 *
	 * <p>
	 * Locations may use the same format as other JPostman resources, for example
	 * {@code classpath:product-data.ini} or a file-system path. Multiple files are
	 * loaded in declaration order. Section names must be unique across all loaded
	 * data files; duplicate section names fail fast instead of being overridden.
	 * </p>
	 *
	 * @return data file locations
	 */
	String[] dataload() default {};

	/**
	 * Optional assertion rule files to load for annotation assertions.
	 *
	 * <p>
	 * Locations may use the same format as other JPostman resources, for example
	 * {@code classpath:annotation-test-assertions.ini} or a file-system path.
	 * Multiple files are loaded in declaration order. Section names must be unique
	 * across all loaded assertion files; duplicate section names fail fast instead
	 * of being overridden.
	 * </p>
	 *
	 * @return assertion rule file locations
	 */
	String[] assertions() default {};

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
	 * Default API executor class for this context.
	 *
	 * <p>
	 * When configured, JPostman can execute requests without a code-defined default
	 * {@link JPostmanExecutor} method. For non-session execution, the executor
	 * class must provide a static {@code apply(request)} method returning
	 * {@link io.jpostman.ApiExecutor}. For session execution, it must provide a
	 * static {@code create()} method returning {@link io.jpostman.ApiExecutor}.
	 * </p>
	 *
	 * @return executor class, or {@link Void} when no context executor is
	 *         configured
	 */
	Class<?> executor() default Void.class;

	/**
	 * Enables session executor mode for the context default executor.
	 *
	 * <p>
	 * When {@code false}, JPostman creates the default executor with
	 * {@code executor.apply(ctx.request())}. When {@code true}, JPostman creates
	 * and reuses the default executor with {@code executor.create()}.
	 * </p>
	 *
	 * @return {@code true} to use session mode; {@code false} to create an executor
	 *         per request
	 */
	boolean session() default false;

	/**
	 * Skips all JPostman response and runner test executions by default.
	 *
	 * <p>
	 * Individual response or runner annotations can opt back in with
	 * {@code enabled = true}.
	 * </p>
	 *
	 * @return {@code true} to skip all JPostman test executions by default
	 */
	boolean skipAll() default false;

	/**
	 * Enables secure request/response logging by default for this JPostman context.
	 *
	 * <p>
	 * This is a class-level/default logging flag. When enabled, annotation-driven
	 * executions such as {@code @JPostmanRunner}, {@code @JPostmanRequest}, and
	 * {@code @JPostmanResponse} will collect secure request/response logs unless
	 * the implementation explicitly defines another logging policy.
	 * </p>
	 *
	 * <p>
	 * Note: Java boolean annotation values cannot distinguish between an omitted
	 * value and an explicit {@code false}. Therefore, a local annotation value such
	 * as {@code log = false} is treated the same as omitting {@code log}. It does
	 * not override this global {@code logs = true} value.
	 * </p>
	 *
	 * @return {@code true} to enable secure request/response logging by default
	 */
	boolean logs() default false;

	/**
	 * Annotation log level. Supported values are TRACE, DEBUG, INFO, WARN, ERROR.
	 * Values are case-insensitive. INFO disables automatic annotation debug output.
	 *
	 * @return annotation log level
	 */
	String logLevel() default "info";

	/**
	 * Format used when log level is DEBUG or TRACE.
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