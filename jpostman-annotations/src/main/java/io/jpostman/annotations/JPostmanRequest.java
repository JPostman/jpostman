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
 * {@code @JPostmanRequest}. Blank namespace and folder values use the default
 * context and collection root for the helper request.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanRequest {

	/**
	 * Context namespace to use.
	 *
	 * <p>
	 * Empty means the default context is used.
	 * </p>
	 *
	 * @return context namespace
	 */
	String namespace() default "";

	/**
	 * Postman folder name to search before resolving the request.
	 *
	 * <p>
	 * Empty means the collection root is searched by request name.
	 * </p>
	 *
	 * @return Postman folder name
	 */
	String folder() default "";

	/**
	 * Postman request name to prepare.
	 *
	 * <p>
	 * Empty means no request is prepared by this helper. This allows helper methods
	 * to modify {@link JPostmanInfo#params} without loading a request.
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
	 * Empty means the dependency is not cached. JPostman does not cache by method
	 * name automatically.
	 * </p>
	 *
	 * @return cache key
	 */
	String cache() default "";
}
