package io.jpostman.secure;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Environment;
import io.jpostman.Request;

/**
 * Shared fluent test context contract used by the JPostman TestNG and JUnit
 * modules.
 * <p>
 * Framework-specific contexts, such as TestNgContext and JUnitContext, should
 * implement this interface so common features are exposed through one stable
 * API: request/response handling, assertions, soft assertions, verification,
 * cache access, rules, filtering, masking, logging, and path helpers.
 * <p>
 * The generic parameters keep fluent chaining strongly typed for each module:
 * <ul>
 * <li>{@code C} - concrete context type, for example TestNgContext or
 * JUnitContext</li>
 * <li>{@code A} - assertion facade type returned by {@link #asserts()}</li>
 * <li>{@code S} - soft assertion facade type returned by {@link #soft()}</li>
 * </ul>
 * The annotations module can also extend this contract from
 * {@code JPostman.Test} instead of duplicating the TestNG and JUnit method
 * lists.
 */
public interface JPostmanTestContext<C, A, S> {

	A asserts();

	A asserts(boolean includeLog);

	S soft();

	S soft(boolean includeLog);

	C verify();

	C verify(int statusCode);

	<T> T cache(String key);

	<T> T cache(Supplier<T> supplier);

	<T> T cache(Supplier<T> supplier, String key);

	C cache(String key, Object value);

	C cacheClean(String... keys);

	C load(InputStream input) throws IOException;

	C loadPolicy(InputStream input) throws IOException;

	C loadRules(String... names);

	C filterList(String... rules);

	C filter(String... rules);

	C headersFilter(String... names);

	C redactionPolicy(RedactionPolicy redactionPolicy);

	C redact(String... rules);

	C redactRegex(String keyRegex);

	C redactRegex(String keyRegex, String valueExpression);

	C unredact(String... rules);

	C headers(String... names);

	C unheaders(String... names);

	C plain(Object... values);

	C plain(Map<String, ?> values);

	C plain(Environment values);

	C secret(Object... values);

	C secret(Map<String, ?> values);

	C secret(Environment values);

	C unsecret(String... names);

	SecureValues values();

	boolean hasKey(String key);

	Object get(String key);

	String asString(String key);

	C copy();

	SecureRequest from(Request request);

	SecureResponse from(ApiResponse response);

	SecureResponse from(ApiExecutor executor);

	C request(Request request);

	C response(ApiResponse response);

	C response(ApiExecutor executor);

	SecureRequest request();

	SecureResponse response();

	<T> T path(String path);

	<T> List<T> paths(String rule);

	boolean exists(String path);

	int statusCode();

	RedactionPolicy redactionPolicy();

	String log();

	String log(boolean resolve);

	void print();

	void print(boolean resolve);
}
