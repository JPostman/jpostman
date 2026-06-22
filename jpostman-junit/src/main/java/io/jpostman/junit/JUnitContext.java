package io.jpostman.junit;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.opentest4j.TestAbortedException;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.secure.RedactionPolicy;
import io.jpostman.secure.SecureContext;
import io.jpostman.secure.SecureRequest;
import io.jpostman.secure.SecureResponse;
import io.jpostman.secure.SecureValues;

/**
 * JUnit-friendly wrapper around {@link SecureContext}.
 *
 * <p>
 * The wrapper keeps JPostman secure logging and adds assertion helpers that can
 * include the secure context log on failure.
 * </p>
 */
public final class JUnitContext {

	private final SecureContext secure;
	private JUnitAssertions<?> assertions;

	/**
	 * Holds the JPostman context for the current test thread.
	 *
	 * <p>
	 * This is used by annotation-based execution, so a test can call
	 * {@code JUnitContext.current()} and get the context prepared for the current
	 * test method.
	 * </p>
	 *
	 * <p>
	 * ThreadLocal is used so parallel test execution does not share the same
	 * context between different test threads.
	 * </p>
	 */
	private static final ThreadLocal<JUnitContext> CURRENT = new ThreadLocal<>();

	/**
	 * Returns the JPostman context prepared for the current JUnit test method.
	 *
	 * @return current JUnit context
	 * @throws AssertionError if no context was prepared for the current thread
	 */
	public static JUnitContext current() {
		JUnitContext context = CURRENT.get();
		if (context == null) {
			throw new AssertionError("No current JUnitContext is available.");
		}
		return context;
	}

	/**
	 * Sets the current context for the running test thread.
	 *
	 * <p>
	 * This should be called by the JUnit extension before the test method runs.
	 * </p>
	 *
	 * @param ctx context prepared for the current test method
	 */
	public static void setCurrent(JUnitContext ctx) {
		CURRENT.set(ctx);
	}

	/**
	 * Clears the current context from the running test thread.
	 *
	 * <p>
	 * This should be called by the JUnit extension after the test method finishes.
	 * Test frameworks may reuse worker threads, so clearing the ThreadLocal is
	 * important to avoid leaking one test context into another test.
	 * </p>
	 */
	public static void clearCurrent() {
		CURRENT.remove();
	}

	/**
	 * Returns the current context for this test thread.
	 *
	 * <p>
	 * This is a short instance helper for annotation-based tests. It delegates to
	 * {@link #current()} so parallel tests use the ThreadLocal context.
	 * </p>
	 *
	 * @return current JUnit context
	 */
	public JUnitContext ctx() {
		return current();
	}

	private JUnitContext(SecureContext secure) {
		this.secure = secure == null ? SecureContext.create() : secure;
	}

	/**
	 * Creates a new JUnit context.
	 *
	 * @return JUnit context
	 */
	public static JUnitContext create() {
		return new JUnitContext(SecureContext.create());
	}

	/**
	 * Wraps an existing secure context.
	 *
	 * @param secure secure context to wrap
	 * @return JUnit context
	 */
	public static JUnitContext from(SecureContext secure) {
		return new JUnitContext(secure);
	}

	/**
	 * Applies custom logic to this context and returns this context.
	 *
	 * <p>
	 * This is useful for adding custom assertions, cache updates, or other context
	 * operations inside a fluent method chain.
	 * </p>
	 *
	 * @param consumer custom context logic
	 * @return this context
	 */
	public JUnitContext context(Consumer<JUnitContext> consumer) {
		if (consumer != null) {
			consumer.accept(this);
		}
		return this;
	}

	/**
	 * Returns the wrapped secure context.
	 *
	 * @return secure context
	 */
	public SecureContext secure() {
		return secure;
	}

	/**
	 * Returns hard assertion helpers.
	 *
	 * @return hard assertions
	 */
	public JUnitAssertions<?> asserts() {
		return asserts(false);
	}

	/**
	 * Returns hard assertion helpers.
	 *
	 * @param includeLog {@code true} to include the secure log on failure
	 * @return hard assertions
	 */
	public JUnitAssertions<?> asserts(boolean includeLog) {
		assertions = new JUnitAssertions<>(this, includeLog);
		return assertions;
	}

	/**
	 * Returns soft assertion helpers.
	 *
	 * @return soft assertions
	 */
	public JUnitSoftAssertions soft() {
		return soft(false);
	}

	/**
	 * Returns soft assertion helpers.
	 *
	 * <p>
	 * The first soft helper created is reused until {@link #verify()} is called.
	 * Choose the secure log option before adding soft assertions.
	 * </p>
	 *
	 * @param includeLog {@code true} to include the secure log on failure
	 * @return soft assertions
	 */
	public JUnitSoftAssertions soft(boolean includeLog) {
		if (assertions == null || !assertions.soft()) {
			assertions = new JUnitSoftAssertions(this, includeLog);
		}
		return (JUnitSoftAssertions) assertions;
	}

	/**
	 * Verifies the current response with status code {@code 200}.
	 *
	 * <p>
	 * If soft assertions were collected, all soft assertions are verified.
	 * Otherwise, a hard status code assertion is performed.
	 * </p>
	 * 
	 * @return this context
	 */
	public JUnitContext verify() {
		return verify(200);
	}

	/**
	 * Verifies the current response with the expected status code.
	 *
	 * <p>
	 * If soft assertions were collected, all soft assertions are verified and the
	 * expected status code is added only when no status code assertion was already
	 * collected. Otherwise, a hard status code assertion is performed.
	 * </p>
	 *
	 * @param statusCode expected status code
	 * @return this context
	 */
	public JUnitContext verify(int statusCode) {
		if (response() == null && assertions == null) {
			return this;
		}

		if (assertions == null) {
			asserts().verify(statusCode);
			return this;
		}

		JUnitAssertions<?> current = assertions;
		if (current.soft()) {
			assertions = null;
		}
		current.verify(statusCode);
		return this;
	}

	/**
	 * Reads a cached value by key.
	 *
	 * @param key cache key
	 * @param <T> expected value type
	 * @return cached value
	 */
	@SuppressWarnings("unchecked")
	public <T> T cache(String key) {
		return (T) secure.cache().get(key);
	}

	/**
	 * Returns a cached value using the caller method name as the cache key.
	 *
	 * @param supplier value supplier used when the key is not cached
	 * @param <T>      value type
	 * @return cached value
	 */
	public <T> T cache(Supplier<T> supplier) {
		return cache(supplier, SecureContext.callerMethodName(2));
	}

	/**
	 * Returns a cached value, creating it when missing.
	 *
	 * <p>
	 * If creating the cached value failed earlier, the current test is skipped.
	 * </p>
	 *
	 * @param supplier value supplier used when the key is not cached
	 * @param key      cache key
	 * @param <T>      value type
	 * @return cached value
	 */
	public <T> T cache(Supplier<T> supplier, String key) {
		try {
			return secure.cache(key, supplier);
		} catch (SecureContext.CachedFailureException e) {
			throw new TestAbortedException("Skipped because cached value failed: " + key, e);
		}
	}

	/**
	 * Stores a value in the shared cache and returns this context.
	 *
	 * @param key   cache key
	 * @param value cached value
	 * @return this context
	 */
	public JUnitContext cache(String key, Object value) {
		secure.cache().put(key, value);
		return this;
	}

	/**
	 * Clears cached values.
	 *
	 * <p>
	 * If no keys are provided, all cached values are removed.
	 * </p>
	 *
	 * @param keys cache keys to remove
	 * @return this JUnit context
	 */
	public JUnitContext cacheClean(String... keys) {
		secure.cacheClean(keys);
		return this;
	}

	/**
	 * Returns the shared cache map.
	 *
	 * <p>
	 * Changes made to this map affect all copied contexts that share the same
	 * cache.
	 * </p>
	 *
	 * @return shared cache map
	 */
	public Map<String, Object> cache() {
		return secure.cache();
	}

	/**
	 * Loads secure rules from an input stream.
	 *
	 * @param input input stream containing secure rules
	 * @return this JUnit context
	 * @throws IOException if the rules cannot be read
	 */
	public JUnitContext load(InputStream input) throws IOException {
		secure.load(input);
		return this;
	}

	/**
	 * Loads secure policy profiles from an input stream.
	 *
	 * @param input input stream containing policy profiles
	 * @return this JUnit context
	 * @throws IOException if the policy cannot be read
	 */
	public JUnitContext loadPolicy(InputStream input) throws IOException {
		secure.loadPolicy(input);
		return this;
	}

	/**
	 * Applies named policy profiles and returns a copied context.
	 *
	 * @param names policy profile names
	 * @return copied JUnit context with the selected rules applied
	 */
	public JUnitContext loadRules(String... names) {
		return new JUnitContext(secure.loadRules(names));
	}

	/**
	 * Adds response list filter rules.
	 *
	 * <p>
	 * List filter rules filter array/list content while keeping the parent response
	 * structure.
	 * </p>
	 *
	 * @param rules list filter rules
	 * @return this JUnit context
	 */
	public JUnitContext filterList(String... rules) {
		secure.filterList(rules);
		return this;
	}

	/**
	 * Adds response body filter rules.
	 *
	 * @param rules response body filter rules
	 * @return this JUnit context
	 */
	public JUnitContext filter(String... rules) {
		secure.filter(rules);
		return this;
	}

	/**
	 * Adds response header filter rules.
	 *
	 * @param names header names or header regex rules
	 * @return this JUnit context
	 */
	public JUnitContext headersFilter(String... names) {
		secure.headersFilter(names);
		return this;
	}

	/**
	 * Sets the redaction policy.
	 *
	 * @param redactionPolicy redaction policy to use
	 * @return this JUnit context
	 */
	public JUnitContext redactionPolicy(RedactionPolicy redactionPolicy) {
		secure.redactionPolicy(redactionPolicy);
		return this;
	}

	/**
	 * Adds redaction rules.
	 *
	 * @param rules redaction rules
	 * @return this JUnit context
	 */
	public JUnitContext redact(String... rules) {
		secure.redact(rules);
		return this;
	}

	/**
	 * Adds a regex key redaction rule.
	 *
	 * @param keyRegex regular expression used to match field keys
	 * @return this JUnit context
	 */
	public JUnitContext redactRegex(String keyRegex) {
		secure.redactRegex(keyRegex);
		return this;
	}

	/**
	 * Adds a regex key redaction rule with a value expression.
	 *
	 * @param keyRegex        regular expression used to match field keys
	 * @param valueExpression slice or regex expression applied to matched values
	 * @return this JUnit context
	 */
	public JUnitContext redactRegex(String keyRegex, String valueExpression) {
		secure.redactRegex(keyRegex, valueExpression);
		return this;
	}

	/**
	 * Removes redaction rules.
	 *
	 * @param rules redaction rules to remove
	 * @return this JUnit context
	 */
	public JUnitContext unredact(String... rules) {
		secure.unredact(rules);
		return this;
	}

	/**
	 * Adds protected header rules.
	 *
	 * @param names header names or header regex rules
	 * @return this JUnit context
	 */
	public JUnitContext headers(String... names) {
		secure.headers(names);
		return this;
	}

	/**
	 * Removes protected header rules.
	 *
	 * @param names header names or header regex rules to remove
	 * @return this JUnit context
	 */
	public JUnitContext unheaders(String... names) {
		secure.unheaders(names);
		return this;
	}

	/**
	 * Adds plain key/value pairs.
	 *
	 * @param values key/value pairs
	 * @return this JUnit context
	 */
	public JUnitContext plain(Object... values) {
		secure.plain(values);
		return this;
	}

	/**
	 * Adds plain values from a map.
	 *
	 * @param values plain values
	 * @return this JUnit context
	 */
	public JUnitContext plain(Map<String, ?> values) {
		secure.plain(values);
		return this;
	}

	/**
	 * Adds plain values from an environment.
	 *
	 * @param values environment values
	 * @return this JUnit context
	 */
	public JUnitContext plain(Environment values) {
		secure.plain(values);
		return this;
	}

	/**
	 * Adds protected key/value pairs.
	 *
	 * @param values key/value pairs
	 * @return this JUnit context
	 */
	public JUnitContext secret(Object... values) {
		secure.secret(values);
		return this;
	}

	/**
	 * Adds protected values from a map.
	 *
	 * @param values protected values
	 * @return this JUnit context
	 */
	public JUnitContext secret(Map<String, ?> values) {
		secure.secret(values);
		return this;
	}

	/**
	 * Adds protected values from an environment.
	 *
	 * @param values environment values
	 * @return this JUnit context
	 */
	public JUnitContext secret(Environment values) {
		secure.secret(values);
		return this;
	}

	/**
	 * Converts protected values to plain values by key.
	 *
	 * @param names value keys to make plain
	 * @return this JUnit context
	 */
	public JUnitContext unsecret(String... names) {
		secure.unsecret(names);
		return this;
	}

	/**
	 * Returns the current secure values.
	 *
	 * @return current secure values
	 */
	public SecureValues values() {
		return secure.values();
	}

	/**
	 * Checks whether a plain or protected value key exists.
	 *
	 * <p>
	 * This checks key presence, not the resolved value. This is useful when callers
	 * need to distinguish between a missing key and a key that exists with an empty
	 * or {@code null} value.
	 * </p>
	 *
	 * @param key value key
	 * @return {@code true} if the key exists
	 */
	public boolean hasKey(String key) {
		return key != null && secure.values().get(key) != null;
	}

	/**
	 * Returns the original value for the given key.
	 *
	 * @param key secure value key
	 * @return original value, or {@code null} if the key does not exist
	 */
	public Object get(String key) {
		return secure.get(key);
	}

	/**
	 * Returns the original value for the given key as a string.
	 *
	 * @param key secure value key
	 * @return value as a string, or an empty string if the key does not exist
	 */
	public String asString(String key) {
		Object value = get(key);
		return value == null ? "" : String.valueOf(value);
	}

	/**
	 * Creates a copy of this JUnit context.
	 *
	 * @return copied JUnit context
	 */
	public JUnitContext copy() {
		return new JUnitContext(secure.copy());
	}

	/**
	 * Wraps a request as a secure request.
	 *
	 * @param request request to protect
	 * @return secure request
	 */
	public SecureRequest from(Request request) {
		return secure.from(request);
	}

	/**
	 * Wraps an API response as a secure response.
	 *
	 * @param response response to protect
	 * @return secure response
	 */
	public SecureResponse from(ApiResponse response) {
		return secure.from(response);
	}

	/**
	 * Wraps the response returned by the given executor.
	 *
	 * @param executor API executor
	 * @return secure response
	 */
	public SecureResponse from(ApiExecutor executor) {
		return secure.from(executor);
	}

	/**
	 * Stores the given request as the current secure request.
	 *
	 * @param request request to protect
	 * @return this JUnit context
	 */
	public JUnitContext request(Request request) {
		secure.request(request);
		return this;
	}

	/**
	 * Stores the given response as the current secure response.
	 *
	 * @param response response to protect
	 * @return this JUnit context
	 */
	public JUnitContext response(ApiResponse response) {
		secure.response(response);
		return this;
	}

	/**
	 * Stores the response returned by the given executor as the current secure
	 * response.
	 *
	 * @param executor API executor
	 * @return this JUnit context
	 */
	public JUnitContext response(ApiExecutor executor) {
		secure.response(executor);
		return this;
	}

	/**
	 * Builds the response from the current JUnit context.
	 *
	 * <p>
	 * The function may return either an {@link ApiResponse} or an
	 * {@link ApiExecutor}. This allows fluent response execution from the current
	 * request.
	 * </p>
	 *
	 * @param executor function that receives this context
	 * @return this JUnit context
	 */
	public JUnitContext response(Function<JUnitContext, ?> executor) {
		Object result = executor.apply(this);

		if (result instanceof ApiResponse) {
			return response((ApiResponse) result);
		}

		if (result instanceof ApiExecutor) {
			return response((ApiExecutor) result);
		}

		throw new IllegalArgumentException("Response function must return ApiResponse or ApiExecutor");
	}

	/**
	 * Returns the current secure request.
	 *
	 * @return current secure request, or {@code null} if no request was set
	 */
	public SecureRequest request() {
		return secure.request();
	}

	/**
	 * Returns the current secure response.
	 *
	 * @return current secure response, or {@code null} if no response was set
	 */
	public SecureResponse response() {
		return secure.response();
	}

	/**
	 * Reads a value from the current secure response using a simple path.
	 *
	 * @param path response path
	 * @param <T>  expected return type
	 * @return selected response value
	 */
	@SuppressWarnings("unchecked")
	public <T> T path(String path) {
		return (T) secure.path(path);
	}

	/**
	 * Reads all values from the current secure response that match a path rule.
	 *
	 * @param rule response path rule
	 * @param <T>  expected item type
	 * @return matching response values
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> paths(String rule) {
		return (List<T>) secure.paths(rule);
	}

	/**
	 * Checks whether the current secure response contains the given path.
	 *
	 * @param path response path
	 * @return {@code true} if the path exists
	 */
	public boolean exists(String path) {
		return secure.exists(path);
	}

	/**
	 * Returns the current secure response status code.
	 *
	 * @return response status code
	 */
	public int statusCode() {
		return secure.statusCode();
	}

	/**
	 * Returns the current redaction policy.
	 *
	 * @return current redaction policy
	 */
	public RedactionPolicy redactionPolicy() {
		return secure.redactionPolicy();
	}

	/**
	 * Returns the secure log with resolved values.
	 *
	 * @return secure log output
	 */
	public String log() {
		return secure.log();
	}

	/**
	 * Returns the secure log.
	 *
	 * @param resolve {@code true} to resolve variables before logging, or
	 *                {@code false} to keep unresolved placeholders
	 * @return secure log output
	 */
	public String log(boolean resolve) {
		return secure.log(resolve);
	}

	/**
	 * Prints the secure log with resolved values.
	 */
	public void print() {
		secure.print();
	}

	/**
	 * Prints the secure log.
	 *
	 * @param resolve {@code true} to resolve variables before logging, or
	 *                {@code false} to keep unresolved placeholders
	 */
	public void print(boolean resolve) {
		secure.print(resolve);
	}

	void resetSoft(JUnitAssertions<?> current) {
		if (assertions == current) {
			assertions = null;
		}
	}
}