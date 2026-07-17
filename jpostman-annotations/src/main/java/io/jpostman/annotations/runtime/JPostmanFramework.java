package io.jpostman.annotations.runtime;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
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
	 * Creates a request/response-free copy of a configured framework context.
	 *
	 * <p>
	 * The copied context preserves setup-time values, redaction rules, filters, and
	 * shared cache state, while the underlying secure context deliberately omits
	 * the latest request and response.
	 * </p>
	 *
	 * @param context source framework context
	 * @return copied framework context
	 */
	C copy(C context);

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
	 * Applies response field filtering to an already available response and returns
	 * a context whose active response is the filtered response clone.
	 *
	 * <p>
	 * This is intentionally separate from {@link #filter(Object, String...)}.
	 * Context filters are useful before a request is executed, but a response-reuse
	 * path must filter the response object that already exists. Otherwise a caller
	 * response that depends on another response keeps printing the dependency
	 * response view.
	 * </p>
	 *
	 * @param context framework context with an existing response
	 * @param paths   response paths to keep
	 * @return context whose response is the filtered response clone
	 */
	default C filterResponse(C context, String... paths) {
		if (context == null || paths == null || paths.length == 0) {
			return context;
		}

		ApiResponse filtered = filteredApiResponse(context, paths);
		if (filtered == null) {
			return filter(context, paths);
		}

		C result = response(context, () -> filtered);
		copyCache(context, result);
		return result;
	}

	private static ApiResponse filteredApiResponse(Object context, String... paths) {
		try {
			Method responseMethod = context.getClass().getMethod("response");
			Object response = responseMethod.invoke(context);
			if (response == null) {
				return null;
			}

			Object filtered = invokeResponseFilter(response, paths);
			return filtered instanceof ApiResponse ? (ApiResponse) filtered : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object invokeResponseFilter(Object response, String... paths) throws ReflectiveOperationException {
		List<String> values = Arrays.asList(paths);
		try {
			Method method = response.getClass().getMethod("filter", List.class);
			return method.invoke(response, values);
		} catch (NoSuchMethodException e) {
			Method method = response.getClass().getMethod("filter", java.util.Collection.class);
			return method.invoke(response, values);
		}
	}

	/**
	 * Applies a Postman request to the context.
	 *
	 * @param context framework context
	 * @param request Postman request
	 * @return context with the request applied
	 */
	C request(C context, Request request);

	/**
	 * Applies a Postman request to the context after applying request values from
	 * {@link JPostmanInfo}.
	 *
	 * <p>
	 * This lets request helper methods call {@code info.body(...)},
	 * {@code info.query(...)}, {@code info.headers(...)}, {@code info.path(...)},
	 * or {@code info.auth(...)} while executor methods can stay focused on choosing
	 * an {@link ApiExecutor}:
	 * </p>
	 *
	 * <pre>
	 * return RestAssuredExecutor.apply(ctx.request());
	 * </pre>
	 *
	 * @param context framework context
	 * @param request original Postman request
	 * @param info    current annotation execution information
	 * @return context with the customized request applied
	 */
	default C request(C context, Request request, JPostmanInfo info) {
		C result = request(context, applyRequestValues(request, info));
		applySecureRequestMetadata(result, info);
		return result;
	}

	/**
	 * Executes the request using the supplied executor and stores the response.
	 *
	 * @param context  framework context
	 * @param executor API executor
	 * @return context with the response applied
	 */
	C response(C context, ApiExecutor executor);

	/** Applies request values collected in {@link JPostmanInfo} to a request. */
	static Request applyRequestValues(Request request, JPostmanInfo info) {
		if (request == null || info == null || !info.hasRequestValues()) {
			return request;
		}

		Request built = request.builder().build();
		Request.RequestBuilder builder = request.builder();

		Map<String, String> bodyParams = built.getBody() == null || built.getBody().params() == null ? Map.of()
				: built.getBody().params();
		Map<String, String> headerParams = built.getHeader() == null || built.getHeader().getParams() == null ? Map.of()
				: built.getHeader().getParams();
		Map<String, String> urlParams = built.getUrl() == null || built.getUrl().getParams() == null ? Map.of()
				: built.getUrl().getParams();

		applySetOrAdd(builder.url(), urlParams, info.query);
		applySetOrAdd(builder.headers(), headerParams, info.headers);
		applySetOrAdd(builder.body(), bodyParams, info.body);
		applySetOrAdd(builder.url(), urlParams, info.path);
		applyAuth(request, builder, info.auth);

		applyAdd(builder.url(), info.queryAdd);
		applyAdd(builder.headers(), info.headersAdd);
		applyAdd(builder.body(), info.bodyAdd);

		return builder.build();
	}

	private static void applySetOrAdd(Request.RequestBuilder.ParamStep step, Map<String, String> configuredParams,
			Map<String, Object> values) {
		if (step == null || values == null || values.isEmpty()) {
			return;
		}

		Map<String, String> params = configuredParams == null ? Map.of() : configuredParams;
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) {
				continue;
			}

			Object value = executableValue(entry.getValue());
			if (params.containsKey(key)) {
				step.set(key, value);
			} else {
				step.add(key, value);
			}
		}
	}

	private static void applyAuth(Request request, Request.RequestBuilder builder, Map<String, Object> auth) {
		if (builder == null || auth == null || auth.isEmpty()) {
			return;
		}
		// No collection auth parameters: preserve the legacy bearer-header fallback.
		// Configured collection auth is applied later by JPostmanDefaultExecutorFactory
		// through executor.auth().oauth2(token), so it is not duplicated in headers.
		applyOAuth2Header(builder.headers(), auth);
	}

	private static void applyOAuth2Header(Request.RequestBuilder.ParamStep headers, Map<String, Object> auth) {
		if (headers == null || auth == null || auth.isEmpty()) {
			return;
		}

		Object token = firstAuthValue(auth, "oauth2", "bearer");
		if (token == null) {
			return;
		}

		Object value = executableValue(token);
		if (value == null || String.valueOf(value).isBlank()) {
			return;
		}

		setOrAdd(headers, "Authorization", "Bearer " + value);
	}

	private static Object firstAuthValue(Map<String, Object> auth, String... keys) {
		for (String key : keys) {
			Object value = auth.get(key);
			Object raw = executableValue(value);
			if (raw != null && !String.valueOf(raw).isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static void setOrAdd(Request.RequestBuilder.ParamStep step, String key, Object value) {
		try {
			step.set(key, value);
		} catch (IllegalArgumentException ex) {
			step.add(key, value);
		}
	}

	private static void applyAdd(Request.RequestBuilder.ParamStep step, Map<String, Object> values) {
		if (step == null || values == null || values.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key != null && !key.isBlank()) {
				step.add(key, executableValue(entry.getValue()));
			}
		}
	}

	/**
	 * Converts secret/cache wrapper values into ordinary Java values only at the
	 * executable request boundary. JPostmanInfo retains the wrappers so masking and
	 * secure-request metadata continue to work.
	 */
	private static Object executableValue(Object value) {
		return JPostmanCacheValueConverter.unwrap(value);
	}

	private static void applySecureRequestMetadata(Object context, JPostmanInfo info) {
		if (context == null || info == null) {
			return;
		}
		Map<String, Object> secrets = info.secretValues();
		String[] secretHeaders = info.secretHeaders();
		if ((secrets == null || secrets.isEmpty()) && (secretHeaders == null || secretHeaders.length == 0)) {
			return;
		}
		try {
			Object secureRequest = context.getClass().getMethod("request").invoke(context);
			if (secureRequest == null) {
				return;
			}
			if (secrets != null && !secrets.isEmpty()) {
				try {
					secureRequest.getClass().getMethod("secret", Map.class).invoke(secureRequest, secrets);
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Older secure modules may not expose SecureRequest.secret(Map).
				}
			}
			if (secretHeaders != null && secretHeaders.length > 0) {
				try {
					secureRequest.getClass().getMethod("headers", String[].class).invoke(secureRequest,
							(Object) secretHeaders);
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Older secure modules may not expose SecureRequest.headers(String...).
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Context implementation does not expose a secure request view.
		}
	}

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
	 * Enables soft assertion collection for the supplied context.
	 *
	 * @param context framework context
	 * @param log     whether request/response diagnostics should be collected
	 */
	void soft(C context, boolean log);

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
	 * Verifies the response status code and includes current annotation location in
	 * the assertion message when the underlying assertion API supports custom
	 * messages.
	 *
	 * <p>
	 * This overload is used by the annotation runner so soft assertion failures
	 * keep the Postman folder/request that produced each failure. The older
	 * overload is kept for compatibility with tests and custom framework bridges.
	 * </p>
	 *
	 * @param context    framework context
	 * @param statusCode expected HTTP status code
	 * @param soft       whether to use soft assertions
	 * @param log        whether to attach secure log output to failures
	 * @param info       current annotation execution information
	 */
	default void verify(C context, int statusCode, boolean soft, boolean log, JPostmanInfo info) {
		verify(context, statusCode, soft, log);
	}

	default void verify(C context, int statusCode, boolean soft, boolean log, JPostmanInfo info, String diagnosticLog) {
		verify(context, statusCode, soft, log, info);
	}

	/**
	 * Verifies pending hard assertions for the supplied context.
	 *
	 * <p>
	 * Runner test bodies can keep a framework-specific hard assertion object, such
	 * as {@code JUnitAssertions<?>}, and use it inside fluent runner callbacks.
	 * Some assertion implementations collect failures until {@code verify()} is
	 * called. This hook lets the runner fail immediately after each request
	 * callback without depending on the concrete assertion type.
	 * </p>
	 *
	 * @param context framework context
	 */
	default void verifyAssertions(C context) {
		verifyAssertionObject(assertions(context));
	}

	/**
	 * Verifies pending soft assertions for the supplied context.
	 *
	 * <p>
	 * Runner status verification uses framework soft assertions when
	 * {@code @JPostmanRunner(soft = true)} is active. The normal
	 * {@link #verifyAssertions(Object)} hook checks the hard assertion facade, so
	 * runner soft aggregation needs this companion hook to flush status/assertion
	 * failures recorded through {@code context.soft(false)} after each request.
	 * </p>
	 *
	 * @param context framework context
	 */
	default void verifySoftAssertions(C context) {
		verifyAssertionObject(softAssertions(context));
	}

	static void verifyAssertionObject(Object assertions) {
		if (assertions == null) {
			return;
		}
		try {
			Method verify = assertions.getClass().getMethod("verify");
			verify.invoke(assertions);
		} catch (NoSuchMethodException e) {
			return;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getTargetException();
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException(cause);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static Object assertions(Object context) {
		if (context == null) {
			return null;
		}
		try {
			Method method = context.getClass().getMethod("asserts", boolean.class);
			return method.invoke(context, false);
		} catch (NoSuchMethodException e) {
			try {
				Method method = context.getClass().getMethod("asserts");
				return method.invoke(context);
			} catch (NoSuchMethodException ignored) {
				return null;
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException(ex);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static Object softAssertions(Object context) {
		if (context == null) {
			return null;
		}
		try {
			Method method = context.getClass().getMethod("soft", boolean.class);
			return method.invoke(context, false);
		} catch (NoSuchMethodException e) {
			try {
				Method method = context.getClass().getMethod("soft");
				return method.invoke(context);
			} catch (NoSuchMethodException ignored) {
				return null;
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException(ex);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

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
	 * Returns cache keys when the underlying context exposes its cache map.
	 *
	 * @param context framework context
	 * @return cache keys, or an empty set when unavailable
	 */
	default Set<String> cacheKeys(C context) {
		Set<String> result = new LinkedHashSet<>();
		if (context == null) {
			return result;
		}
		try {
			Method cache = context.getClass().getMethod("cache");
			Object values = cache.invoke(context);
			if (values instanceof Map<?, ?>) {
				for (Object key : ((Map<?, ?>) values).keySet()) {
					if (key != null) {
						result.add(String.valueOf(key));
					}
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Older context implementations may not expose the cache map.
		}
		return result;
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

	/** Prints the full framework context when supported. */
	default void printContext(C context) {
		invokePrint(context);
	}

	/** Prints only the current framework request when supported. */
	default void printRequest(C context) {
		invokeOwnerPrint(context, "request");
	}

	/** Prints only the current framework response when supported. */
	default void printResponse(C context) {
		invokeOwnerPrint(context, "response");
	}

	private static void invokeOwnerPrint(Object context, String ownerName) {
		if (context == null || ownerName == null || ownerName.isBlank()) {
			return;
		}
		try {
			Method ownerMethod = context.getClass().getMethod(ownerName);
			if (ownerMethod.getReturnType() == Void.TYPE) {
				return;
			}
			invokePrint(ownerMethod.invoke(context));
		} catch (NoSuchMethodException e) {
			// Context does not expose this object.
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Logging helpers must never hide the original execution.
		}
	}

	private static void invokePrint(Object target) {
		if (target == null) {
			return;
		}
		try {
			Method print = target.getClass().getMethod("print");
			if (print.getReturnType() == Void.TYPE) {
				print.invoke(target);
			}
		} catch (NoSuchMethodException e) {
			// Object does not expose print().
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Logging helpers must never hide the original execution.
		}
	}

	/**
	 * Returns best-effort diagnostic output for the current context.
	 *
	 * <p>
	 * This is used when request execution fails before assertion/verification can
	 * print the normal secure request/response log. Implementations may override
	 * this, but the default reflection-based version supports common JPostman
	 * methods such as {@code log()}, {@code request().log()}, and
	 * {@code response().log()}.
	 * </p>
	 *
	 * @param context framework context
	 * @return diagnostic text, or an empty string when unavailable
	 */
	default String diagnosticLog(C context, boolean request, boolean response) {
		if (context == null) {
			return "";
		}

		if (request && response) {
			return diagnosticLog(context);
		}

		StringBuilder result = new StringBuilder();
		if (request) {
			appendOwnerLog(context, "request", result);
		}
		if (response) {
			appendOwnerLog(context, "response", result);
		}
		return result.toString();
	}

	default String diagnosticLog(C context) {
		if (context == null) {
			return "";
		}

		String contextLog = contextLog(context);
		if (!contextLog.isBlank()) {
			return contextLog;
		}

		StringBuilder result = new StringBuilder();

		for (String ownerName : new String[] { "request", "response" }) {
			try {
				Method ownerMethod = context.getClass().getMethod(ownerName);
				if (ownerMethod.getReturnType() == Void.TYPE) {
					continue;
				}
				Object owner = ownerMethod.invoke(context);
				if (owner == null) {
					continue;
				}
				try {
					Method log = owner.getClass().getMethod("log");
					if (log.getReturnType() != Void.TYPE) {
						Object value = log.invoke(owner);
						if (value != null && !String.valueOf(value).isBlank()) {
							if (result.length() > 0) {
								result.append(JPostmanErrors.ENDL);
							}
							result.append(String.valueOf(value));
						}
					}
				} catch (NoSuchMethodException e) {
					// Object does not expose log().
				}
			} catch (NoSuchMethodException e) {
				// Context does not expose this object.
			} catch (ReflectiveOperationException | RuntimeException e) {
				// Diagnostics must never hide the original execution failure.
			}
		}

		return result.toString();
	}

	private static String contextLog(Object context) {
		if (context == null) {
			return "";
		}
		try {
			Method method = context.getClass().getMethod("log");
			if (method.getReturnType() == Void.TYPE) {
				return "";
			}
			Object value = method.invoke(context);
			return value == null ? "" : String.valueOf(value);
		} catch (NoSuchMethodException e) {
			return "";
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Diagnostics must never hide the original execution failure.
			return "";
		}
	}

	private static void appendOwnerLog(Object context, String ownerName, StringBuilder result) {
		if (context == null || ownerName == null || result == null) {
			return;
		}
		try {
			Method ownerMethod = context.getClass().getMethod(ownerName);
			if (ownerMethod.getReturnType() == Void.TYPE) {
				return;
			}
			Object owner = ownerMethod.invoke(context);
			if (owner == null) {
				return;
			}
			try {
				Method log = owner.getClass().getMethod("log");
				if (log.getReturnType() != Void.TYPE) {
					Object value = log.invoke(owner);
					if (value != null && !String.valueOf(value).isBlank()) {
						if (result.length() > 0) {
							result.append(JPostmanErrors.ENDL);
						}
						result.append(String.valueOf(value));
					}
				}
			} catch (NoSuchMethodException e) {
				// Object does not expose log().
			}
		} catch (NoSuchMethodException e) {
			// Context does not expose this object.
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Diagnostics must never hide the original execution failure.
		}
	}

	/**
	 * Invokes a framework assertion object's {@code statusCode} assertion while
	 * passing the current JPostman location when supported.
	 *
	 * <p>
	 * Newer JPostman assertion APIs may expose {@code statusCode(int, String)}.
	 * When present, this method is preferred so soft assertion collections contain
	 * the folder/request for each individual failure. Older APIs are still
	 * supported through {@code statusCode(int)}. For hard assertions on older APIs,
	 * the thrown message is wrapped with the location suffix.
	 * </p>
	 *
	 * @param assertions assertion target returned by {@code context.asserts(...)}
	 *                   or {@code context.soft(...)}
	 * @param statusCode expected HTTP status code
	 * @param info       current annotation execution information
	 */
	static void statusCode(Object context, Object assertions, int statusCode, JPostmanInfo info) {
		statusCode(context, assertions, statusCode, info, false, false, "");
	}

	static void statusCode(Object context, Object assertions, int statusCode, JPostmanInfo info, boolean soft,
			boolean log, String diagnosticLog) {
		String message = statusCodePrefix(info);
		if (soft && log) {
			int actualStatusCode = actualStatusCode(context);
			String diagnostic = value(diagnosticLog).trim();
			if (actualStatusCode >= 0 && actualStatusCode != statusCode && !diagnostic.isBlank()) {
				message = statusCodeMessage(info, statusCode, actualStatusCode, diagnostic);
			}
		}
		try {
			invokeStatusCode(assertions, statusCode, message);
		} catch (AssertionError e) {
			String detail = log ? appendDiagnostic(e.getMessage(), diagnosticLog) : value(e.getMessage());
			AssertionError error = new AssertionError(endWithNewLine(detail), e);
			copySuppressed(e, error);
			throw error;
		}
	}

	private static String statusCodePrefix(JPostmanInfo info) {
		if (info == null) {
			return "";
		}
		return JPostmanErrors.suffix(info) + "\nStatus code mismatch:";
	}

	private static String statusCodeMessage(JPostmanInfo info, int expected, int actual, String diagnosticLog) {
		StringBuilder message = new StringBuilder();
		message.append(JPostmanErrors.suffix(info));
		message.append(JPostmanErrors.ENDL);
		message.append("Status code mismatch: expected [").append(expected).append("] but found [").append(actual)
				.append("]");
		String diagnostic = value(diagnosticLog).trim();
		if (!diagnostic.isBlank()) {
			message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append(diagnostic);
		}
		return endWithNewLine(message.toString());
	}

	private static String appendDiagnostic(String message, String diagnosticLog) {
		String result = value(message).stripTrailing();
		String diagnostic = value(diagnosticLog).trim();
		if (diagnostic.isBlank() || result.contains(diagnostic)) {
			return result;
		}
		return result + JPostmanErrors.ENDL + JPostmanErrors.ENDL + diagnostic;
	}

	private static int actualStatusCode(Object context) {
		Object response = responseObject(context);
		if (response == null) {
			return -1;
		}
		for (String methodName : new String[] { "statusCode", "getStatusCode", "status", "getStatus" }) {
			try {
				Method method = response.getClass().getMethod(methodName);
				if (method.getReturnType() == Void.TYPE) {
					continue;
				}
				Object value = method.invoke(response);
				if (value instanceof Number) {
					return ((Number) value).intValue();
				}
				if (value != null) {
					return Integer.parseInt(String.valueOf(value));
				}
			} catch (NoSuchMethodException e) {
				// Try the next common method name.
			} catch (ReflectiveOperationException | RuntimeException e) {
				return -1;
			}
		}
		return -1;
	}

	private static Object responseObject(Object context) {
		if (context == null) {
			return null;
		}
		try {
			Method method = context.getClass().getMethod("response");
			if (method.getReturnType() == Void.TYPE) {
				return null;
			}
			return method.invoke(context);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}

	private static boolean invokeStatusCode(Object assertions, int statusCode, String message) {
		if (assertions == null) {
			return false;
		}
		Class<?> type = assertions.getClass();
		try {
			Method method = type.getMethod("statusCode", int.class, String.class);
			method.invoke(assertions, statusCode, message);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof AssertionError) {
				throw (AssertionError) cause;
			}
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException("Failed to verify JPostman status code.", cause);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to verify JPostman status code.", e);
		}
	}

	private static void copySuppressed(Throwable source, Throwable target) {
		if (source == null || target == null) {
			return;
		}
		Throwable current = source;
		while (current != null) {
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed != null && suppressed != target) {
					target.addSuppressed(suppressed);
				}
			}
			current = current.getCause();
		}
	}

	private static String endWithNewLine(String value) {
		String result = value == null ? "" : value;
		return result.endsWith(JPostmanErrors.ENDL) ? result : result + JPostmanErrors.ENDL;
	}

	/**
	 * Creates a framework-specific skip/abort exception.
	 *
	 * <p>
	 * The annotation runner uses this when JPostman cannot execute a generated
	 * runner flow because required runtime pieces, such as a default executor, are
	 * missing. Implementations should return the native skip exception for their
	 * framework so the test is reported as skipped instead of passed or failed.
	 * </p>
	 *
	 * @param info  annotation execution information used in the skip message
	 * @param lines skip message lines
	 * @return framework-specific runtime exception
	 */
	default RuntimeException skipException(JPostmanInfo info, String... lines) {
		return new IllegalStateException(getMessage(info, lines));
	}

	static String getMessage(JPostmanInfo info, String... lines) {
		return JPostmanErrors.message(info, lines);
	}

	static String infoSuffix(JPostmanInfo info) {
		String suffix = JPostmanErrors.suffix(info);
		return suffix.isBlank() ? "" : " " + suffix;
	}

	/**
	 * Returns the framework name used in error messages.
	 *
	 * @return framework name
	 */
	String name();
}
