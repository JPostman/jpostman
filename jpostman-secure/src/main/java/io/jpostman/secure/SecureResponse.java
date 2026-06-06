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
	 * Reads a value from the JSON response body using a simple dot/bracket path.
	 *
	 * <p>
	 * Examples:
	 * </p>
	 *
	 * <pre>
	 * String token = response.path("accessToken");
	 * Integer id = response.path("products[0].id");
	 * String title = response.path("products[0].title");
	 * </pre>
	 *
	 * @param path simple JSON path
	 * @param <T>  expected return type
	 * @return selected value converted to a Java value
	 */
	public <T> T path(String path) {
		return Params.path(parse(), path);
	}

	/**
	 * Returns the response body formatted for display.
	 *
	 * <p>
	 * JSON objects and arrays are returned as pretty-printed JSON. JSON string
	 * primitives are returned as plain text without JSON quotes. If the response
	 * body could not be parsed, the original raw body is returned.
	 * </p>
	 *
	 * @return formatted response body text
	 */
	public String pretty() {
		return SecureText.redact(response.pretty(), secureValues, redactionPolicy);
	}

	/**
	 * Returns a readable response summary including status code, and body.
	 *
	 * @return formatted response summary
	 */
	public String log() {
		return log(false);
	}

	/**
	 * Returns a readable response summary including status code, headers, and body.
	 *
	 * @return formatted response summary
	 */
	public String log(boolean all) {
		return SecureText.redact(response.log(all), secureValues, redactionPolicy);
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