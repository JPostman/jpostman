package io.jpostman.annotations.runtime;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import io.jpostman.ApiExecutor;
import io.jpostman.Request;

/**
 * Small bridge between the shared annotation engine and a test framework
 * context.
 *
 * <p>
 * Implementations adapt framework-specific context operations, such as setting
 * the current context, loading rules, executing requests, verifying responses,
 * and reading or writing cache values.
 * </p>
 *
 * @param <C> framework context type
 */
public interface JPostmanFramework<C> {

	/**
	 * Returns the framework context type handled by this bridge.
	 *
	 * @return context class
	 */
	Class<C> contextType();

	/**
	 * Creates a new framework context.
	 *
	 * @return new framework context
	 */
	C create();

	/**
	 * Sets the current context for the active test thread.
	 *
	 * @param context context to make current
	 */
	void setCurrent(C context);

	/**
	 * Clears the current context for the active test thread.
	 */
	void clearCurrent();

	/**
	 * Loads all environment values into the framework context as secrets.
	 *
	 * @param context     framework context
	 * @param environment Postman environment object
	 */
	void secret(C context, Object environment);

	/**
	 * Stores a plain value in the framework context.
	 *
	 * @param context framework context
	 * @param key     value key
	 * @param value   plain value
	 */
	void plain(C context, String key, Object value);

	/**
	 * Stores a secret value in the framework context.
	 *
	 * @param context framework context
	 * @param key     value key
	 * @param value   secret value
	 */
	void secret(C context, String key, Object value);

	/**
	 * Loads secure rules into the framework context.
	 *
	 * @param context framework context
	 * @param rules   rules input stream
	 * @throws Exception when rule loading fails
	 */
	void load(C context, InputStream rules) throws Exception;

	/**
	 * Applies a named secure rule section to the context.
	 *
	 * @param context framework context
	 * @param rule    rule section name
	 * @return context with the rule applied
	 */
	C loadRules(C context, String rule);

	/**
	 * Applies response field filtering to the context.
	 *
	 * @param context framework context
	 * @param paths   response paths to keep
	 * @return context with filtering applied
	 */
	C filter(C context, String... paths);

	/**
	 * Applies a Postman request to the context.
	 *
	 * @param context framework context
	 * @param request Postman request
	 * @return context with the request applied
	 */
	C request(C context, Request request);

	/**
	 * Executes the request using the supplied executor and stores the response.
	 *
	 * @param context  framework context
	 * @param executor API executor
	 * @return context with the response applied
	 */
	C response(C context, ApiExecutor executor);

	/**
	 * Verifies the response status code.
	 *
	 * @param context    framework context
	 * @param statusCode expected HTTP status code
	 * @param soft       whether to use soft assertions
	 * @param log        whether to attach secure log output to failures
	 */
	void verify(C context, int statusCode, boolean soft, boolean log);

	/**
	 * Reads a cached value from the context.
	 *
	 * @param context framework context
	 * @param key     cache key
	 * @return cached value, or {@code null} when no non-null value is available
	 */
	Object cache(C context, String key);

	/**
	 * Stores a cached value in the context.
	 *
	 * @param context framework context
	 * @param key     cache key
	 * @param value   value to cache
	 */
	void cache(C context, String key, Object value);

	/**
	 * Checks whether a cache entry exists for the given key.
	 *
	 * <p>
	 * This checks key existence, not value presence, so dependency methods can be
	 * marked as executed even when they do not return a value.
	 * </p>
	 *
	 * @param context framework context
	 * @param key     cache key
	 * @return {@code true} when the cache contains the key
	 */
	default boolean hasCache(C context, String key) {
		if (key == null) {
			return false;
		}

		try {
			Method cache = context.getClass().getMethod("cache");
			Object value = cache.invoke(context);
			if (value instanceof Map<?, ?>) {
				return ((Map<?, ?>) value).containsKey(key);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Older context implementations may not expose the cache map.
			// Fall back to value lookup for non-null cached values.
		}

		return cache(context, key) != null;
	}

	/**
	 * Returns the framework name used in error messages.
	 *
	 * @return framework name
	 */
	String name();
}
