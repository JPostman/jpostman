package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Prepares a Postman request for a test or helper method.
 *
 * <p>
 * Request helper methods can be used as dependencies for
 * {@link JPostmanResponse}, {@link JPostmanRunner}, or another
 * {@code @JPostmanRequest}. When request, folder, or namespace is blank, the
 * value is inherited from the caller in the current {@link JPostmanInfo} chain.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanRequest {

	/**
	 * Logical name for this request helper.
	 *
	 * @return logical JPostman invocation name
	 */
	String id() default "";

	/**
	 * Named executor associated with this request helper.
	 *
	 * <p>
	 * Empty means the default executor. This value is stored in
	 * {@link JPostmanInfo} so helper methods can see which executor is active for
	 * the chain.
	 * </p>
	 *
	 * @return named executor
	 */
	String executor() default "";

	/**
	 * Context namespace to use.
	 *
	 * <p>
	 * Empty means inherit from the caller. If there is no caller namespace, the
	 * default context is used.
	 * </p>
	 *
	 * @return context namespace
	 */
	String namespace() default "";

	/**
	 * Postman folder name to search before resolving the request.
	 *
	 * <p>
	 * Empty means inherit from the caller. If no folder is available, the
	 * collection root is searched by request name.
	 * </p>
	 *
	 * @return Postman folder name
	 */
	String folder() default "";

	/**
	 * Postman request name to prepare.
	 *
	 * <p>
	 * Empty means inherit from the caller. This allows helper methods to modify
	 * {@link JPostmanInfo#params} without repeating the same request name on every
	 * method in the chain.
	 * </p>
	 *
	 * @return Postman request name
	 */
	String request() default "";

	/**
	 * Secure rule section to load before preparing the request.
	 *
	 * @return secure rule section name
	 */
	String rule() default "";

	/**
	 * Dependency method names to run before this request helper.
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};

	/**
	 * Request helper method to run after this request helper completes.
	 *
	 * <p>
	 * This supports forward-readable chains, for example
	 * {@code prepareUser -> buildBody -> login}. Circular chains are rejected by
	 * the annotation runner.
	 * </p>
	 *
	 * @return next request helper method name
	 */
	String next() default "";

	/**
	 * Cache key for storing this method result.
	 *
	 * <p>
	 * Empty means the dependency is not cached. When a cache key is provided, the
	 * request method must return a value. Void request methods cannot be cached
	 * because there is no result to store.
	 * </p>
	 *
	 * @return cache key
	 */
	String cache() default "";
}
