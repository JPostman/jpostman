package io.jpostman.secure;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.jpostman.ApiResponse;
import io.jpostman.Params;

/**
 * Secret-safe wrapper around a JPostman {@link ApiResponse}.
 */
public final class SecureResponse {

	private static final Logger log = LoggerFactory.getLogger(SecureResponse.class);

	private List<String> filters = List.of();
	private List<String> headerFilters = List.of();

	private final ApiResponse response;
	private SecureValues secureValues = SecureValues.empty();
	private RedactionPolicy redactionPolicy = RedactionPolicy.defaults();

	private SecureResponse(ApiResponse response) {
		if (response == null) {
			throw new IllegalArgumentException("response cannot be null");
		}
		this.response = response;
	}

	/**
	 * Starts wrapping a response.
	 *
	 * @param response response to wrap
	 * @return secure response wrapper
	 */
	public static SecureResponse from(ApiResponse response) {
		return new SecureResponse(response);
	}

	/**
	 * Sets secure values used for value-based redaction.
	 *
	 * @param values secure values
	 * @return this wrapper
	 */
	public SecureResponse values(SecureValues values) {
		this.secureValues = values == null ? SecureValues.empty() : values;
		return this;
	}

	/**
	 * Sets the redaction policy used for key-based redaction.
	 *
	 * @param redactionPolicy redaction policy
	 * @return this wrapper
	 */
	public SecureResponse redactionPolicy(RedactionPolicy redactionPolicy) {
		this.redactionPolicy = redactionPolicy == null ? RedactionPolicy.defaults() : redactionPolicy;
		return this;
	}

	/**
	 * Returns the current redaction policy.
	 *
	 * @return redaction policy
	 */
	public RedactionPolicy redactionPolicy() {
		return redactionPolicy;
	}

	/**
	 * Adds response fields that should be protected.
	 *
	 * <p>
	 * Examples:
	 * </p>
	 *
	 * <pre>
	 * secure("token", "ssn", "creditCard[:-4]")
	 * </pre>
	 *
	 * <p>
	 * A plain field name masks the full value. A field with a slice keeps part of
	 * the value visible depending on the slice rule.
	 * </p>
	 *
	 * @param rules field redaction rules
	 * @return this wrapper
	 */
	public SecureResponse redact(String... rules) {
		this.redactionPolicy = this.redactionPolicy.addRules(rules);
		return this;
	}

	/**
	 * Adds protected header names whose values should be fully masked in logs.
	 *
	 * @param names header names
	 * @return this wrapper
	 */
	public SecureResponse headers(String... names) {
		this.redactionPolicy = this.redactionPolicy.headers(names);
		return this;
	}

	/**
	 * Removes protected header names.
	 *
	 * @param names header names or regex rules to remove
	 * @return this response
	 */
	public SecureResponse unheaders(String... names) {
		this.redactionPolicy = this.redactionPolicy.unheaders(names);
		return this;
	}

	/**
	 * Sets response body filter rules.
	 *
	 * <p>
	 * When filters are configured, only matching response fields or JSON paths are
	 * included in the logged response body.
	 * </p>
	 *
	 * @param filters fields or JSON path rules to include
	 * @return this response
	 */
	public SecureResponse filter(List<String> filters) {
		this.filters = filters == null ? List.of() : List.copyOf(filters);
		return this;
	}

	/**
	 * Sets response header filter rules.
	 *
	 * <p>
	 * When header filters are configured, only matching response headers are
	 * included in the logged response headers.
	 * </p>
	 *
	 * @param headerFilters header names to include
	 * @return this response
	 */
	public SecureResponse headersFilter(List<String> headerFilters) {
		this.headerFilters = headerFilters == null ? List.of() : List.copyOf(headerFilters);
		return this;
	}

	/**
	 * Sets response header filter rules.
	 *
	 * @param headerFilters header names to include
	 * @return this response
	 */
	public SecureResponse headersFilter(String... headerFilters) {
		return headersFilter(headerFilters == null ? null : Params.asList(headerFilters));
	}

	/** @return HTTP status code. */
	public int statusCode() {
		return response.statusCode();
	}

	/** @return response body as text. */
	public String getBody() {
		return response.getBody();
	}

	/**
	 * Attempts to parse a raw string as JSON, returning {@code null} on failure.
	 */
	public JsonElement parse() {
		return response.parse();
	}

	/**
	 * Attempts to parse a raw string as JSON, returning {@code null} on failure.
	 */
	public JsonElement parse(String raw) {
		return response.parse(raw);
	}

	/** @return raw response body bytes. */
	public byte[] asByteArray() {
		return response.asByteArray();
	}

	/** @return response headers. */
	public Map<String, List<String>> getHeaders() {
		return response.getHeaders();
	}

	/**
	 * Checks whether a value exists in the JSON response body.
	 *
	 * <p>
	 * This method supports simple dot/bracket paths and secure rule paths.
	 * </p>
	 *
	 * <pre>
	 * response.exists("accessToken");
	 * response.exists("products[0].id");
	 * response.exists("/products/&#42;/reviews");
	 * response.exists("/&#42;&#42;/reviews");
	 * response.exists("regex:.*token.*");
	 * response.exists("regex:/products/\\d+/reviews");
	 * </pre>
	 *
	 * @param path simple JSON path, wildcard rule, or regex rule
	 * @return {@code true} if the path or rule exists
	 */
	public boolean exists(String path) {
		if (path == null || path.trim().isEmpty()) {
			return false;
		}

		if (path.startsWith("/") || path.startsWith("regex:")) {
			RedactionPolicy policy = RedactionPolicy.builder().protectRule(JsonPathRules.normalizeRule(path)).build();

			return SecureText.exists(parse(), policy);
		}

		return Params.pathElement(parse(), path) != null;
	}

	/**
	 * Reads a value from the JSON response body using a simple path.
	 *
	 * <p>
	 * Supports normal dot/bracket syntax and slash-based exact path syntax.
	 * </p>
	 *
	 * <pre>
	 * String title = response.path("products[0].title");
	 * String title = response.path("/products[0].title");
	 * String title = response.path("/products[0]/title");
	 * String title = response.path("/products/0/title");
	 * </pre>
	 *
	 * @param path JSON path
	 * @param <T>  expected return type
	 * @return selected value converted to a Java value
	 */
	public <T> T path(String path) {
		return Params.path(parse(), JsonPathRules.toSimplePath(path));
	}

	/**
	 * Reads all values from the JSON response body that match a path rule.
	 *
	 * <p>
	 * Supports exact slash paths, wildcard paths, recursive wildcard paths, and
	 * regex path rules.
	 * </p>
	 *
	 * <pre>
	 * List&lt;Integer&gt; ids = response.paths("/&#42;&#42;/id");
	 * List&lt;String&gt; titles = response.paths("/products/&#42;/title");
	 * </pre>
	 *
	 * @param rule JSON path rule
	 * @param <T>  expected item type
	 * @return matching values
	 */
	public <T> List<T> paths(String rule) {
		return JsonPathRules.paths(parse(), rule);
	}

	/**
	 * Returns the response body formatted for display.
	 *
	 * <p>
	 * JSON objects and arrays are returned as pretty-printed JSON. JSON string
	 * primitives are returned as plain text without JSON quotes. If the response
	 * body could not be parsed, the original raw body is returned. Sensitive values
	 * are redacted.
	 * </p>
	 *
	 * @return formatted response body text
	 */
	public String pretty() {
		return SecureText.redact(response.pretty(), secureValues, redactionPolicy);
	}

	/**
	 * Returns the response body with configured filters applied.
	 *
	 * <p>
	 * If no filters are configured, the full formatted response body is returned.
	 * Sensitive values are redacted after filtering.
	 * </p>
	 *
	 * @return filtered and redacted response body
	 */
	public String filtered() {
		String body = response.pretty();
		if (!filters.isEmpty()) {
			body = SecureText.filter(body, filters);
		}
		return SecureText.redact(body, secureValues, redactionPolicy);
	}

	/**
	 * Returns a readable response summary including status code and body.
	 *
	 * @return formatted response summary
	 */
	public String log() {
		return log(false);
	}

	/**
	 * Returns a readable response summary including status code, headers, and body.
	 *
	 * <p>
	 * If filters are configured, the log body includes only matching fields.
	 * Sensitive values are redacted after filtering.
	 * </p>
	 *
	 * @param all true to include response headers
	 * @return formatted response summary
	 */
	public String log(boolean all) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Status Code: %d\n", response.statusCode()));

		if (all) {
			response.getHeaders().entrySet().stream().filter(e -> includeHeader(e.getKey()))
					.map(e -> String.format("  %-35s = %s\n", e.getKey(), filteredHeader(e.getKey(), e.getValue())))
					.forEach(sb::append);
		}

		sb.append(String.format("Body: %s\n", filtered()));

		return sb.toString();
	}

	private boolean includeHeader(String name) {
		if (headerFilters.isEmpty()) {
			return true;
		}

		return headerFilters.stream().anyMatch(filter -> JsonPathRules.matchesName(name, filter));
	}

	private String filteredHeader(String name, Object value) {
		if (redactionPolicy.isHeaderProtected(name)) {
			return RedactionPolicy.DEFAULT_MASK;
		}
		return SecureText.redact(String.valueOf(value), secureValues, redactionPolicy);
	}

	/** Logs this body at TRACE level. */
	public void print() {
		log.trace(log());
	}

	/**
	 * Returns the wrapped response.
	 *
	 * @return wrapped response
	 */
	public ApiResponse response() {
		return response;
	}
}