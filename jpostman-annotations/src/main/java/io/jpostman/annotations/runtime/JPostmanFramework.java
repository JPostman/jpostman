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
	 * Returns whether the context currently has a response.
	 *
	 * <p>
	 * Used by default status-code verification for request helper methods, where
	 * some helpers only prepare request data and do not execute a response.
	 * </p>
	 *
	 * @param context framework context
	 * @return {@code true} when a response is available
	 */
	default boolean hasResponse(C context) {
		if (context == null) {
			return false;
		}

		try {
			Method method = context.getClass().getMethod("response");
			if (method.getReturnType() == Void.TYPE) {
				return false;
			}
			return method.invoke(context) != null;
		} catch (NoSuchMethodException e) {
			return false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

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
	 * Reads a value from the current response using the framework context path
	 * helper.
	 *
	 * @param context framework context
	 * @param path    response path
	 * @return resolved response value, or {@code null} when unavailable
	 */
	default Object path(C context, String path) {
		if (context == null || path == null || path.isBlank()) {
			return null;
		}

		try {
			Method method = context.getClass().getMethod("path", String.class);
			if (method.getReturnType() == Void.TYPE) {
				return null;
			}
			return method.invoke(context, path);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new IllegalStateException("Failed to resolve JPostman response path: " + path, e);
		}
	}

	/**
	 * Stores a cached value in the context.
	 *
	 * @param context framework context
	 * @param key     cache key
	 * @param value   value to cache
	 */
	void cache(C context, String key, Object value);

	/**
	 * Resolves a plain or secret value from the framework context.
	 *
	 * <p>
	 * Newer secure contexts expose a single-argument value lookup method. Older
	 * contexts may not have one, so this default implementation uses reflection and
	 * returns {@code null} when no lookup method is available.
	 * </p>
	 *
	 * @param context framework context
	 * @param key     value key
	 * @return resolved value, or {@code null} when unavailable
	 */
	default Object value(C context, String key) {
		if (context == null || key == null || key.isBlank()) {
			return null;
		}

		for (String methodName : new String[] { "value", "get", "param" }) {
			try {
				Method method = context.getClass().getMethod(methodName, String.class);
				if (method.getReturnType() == Void.TYPE) {
					continue;
				}
				return method.invoke(context, key);
			} catch (NoSuchMethodException e) {
				// Try the next common lookup method name.
			} catch (ReflectiveOperationException | RuntimeException e) {
				throw new IllegalStateException("Failed to resolve JPostman context value: " + key, e);
			}
		}

		for (String methodName : new String[] { "params", "plain", "secret", "values", "env", "environment" }) {
			try {
				Method method = context.getClass().getMethod(methodName);
				if (method.getReturnType() == Void.TYPE) {
					continue;
				}
				Object values = method.invoke(context);
				Object resolved = mapValue(values, key);
				if (resolved != null) {
					return resolved;
				}
			} catch (NoSuchMethodException e) {
				// Try the next common value container method name.
			} catch (ReflectiveOperationException | RuntimeException e) {
				throw new IllegalStateException("Failed to resolve JPostman context value: " + key, e);
			}
		}

		return null;
	}

	private static Object mapValue(Object values, String key) throws ReflectiveOperationException {
		if (values instanceof Map<?, ?>) {
			return ((Map<?, ?>) values).get(key);
		}
		if (values == null) {
			return null;
		}
		try {
			Method getParams = values.getClass().getMethod("getParams");
			Object params = getParams.invoke(values);
			if (params instanceof Map<?, ?>) {
				return ((Map<?, ?>) params).get(key);
			}
		} catch (NoSuchMethodException e) {
			// Not an Environment-like object.
		}
		return null;
	}

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
	 * Copies cache entries from one context to another.
	 *
	 * <p>
	 * The annotation runner uses this when it creates a clean execution context for
	 * a new test method. Cache entries are preserved, but request-scoped state such
	 * as filters and responses is not reused.
	 * </p>
	 *
	 * @param source context that owns the current cache values
	 * @param target context that should receive the cache values
	 */
	default void copyCache(C source, C target) {
		if (source == null || target == null || source == target) {
			return;
		}

		try {
			Method cache = source.getClass().getMethod("cache");
			Object values = cache.invoke(source);
			if (!(values instanceof Map<?, ?>)) {
				return;
			}

			for (Map.Entry<?, ?> entry : ((Map<?, ?>) values).entrySet()) {
				Object key = entry.getKey();
				if (key != null) {
					cache(target, String.valueOf(key), entry.getValue());
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Older context implementations may not expose the cache map.
		}
	}

	/**
	 * Returns the framework name used in error messages.
	 *
	 * @return framework name
	 */
	String name();
}
