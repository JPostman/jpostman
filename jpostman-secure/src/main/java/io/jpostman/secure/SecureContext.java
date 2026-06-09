package io.jpostman.secure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.jpostman.ApiResponse;
import io.jpostman.Environment;
import io.jpostman.Params;
import io.jpostman.Request;

/**
 * Reusable secure configuration for multiple requests.
 *
 * <p>
 * A context stores plain values, protected values, and a redaction policy so
 * the same secure setup can be applied to many requests.
 * </p>
 */
public final class SecureContext {

	private final SecureValues.Builder values = SecureValues.builder();

	private RedactionPolicy redactionPolicy = RedactionPolicy.defaults();

	private final List<String> filters = new ArrayList<>();
	private final List<String> headerFilters = new ArrayList<>();

	private SecureRequest request;
	private SecureResponse response;

	private SecureContext() {
	}

	/**
	 * Creates a secure context.
	 *
	 * @return secure context
	 */
	public static SecureContext create() {
		return new SecureContext();
	}

	/**
	 * Loads redaction rules from an input stream.
	 *
	 * <p>
	 * Each non-empty line is treated as one redaction rule. Lines starting with
	 * {@code #} are ignored. Lines starting with {@code -} remove a rule. Use
	 * {@code \-} to redact a field whose name starts with {@code -}.
	 * </p>
	 *
	 * @param input input stream containing redaction rules
	 * @return this context
	 * @throws IOException if the input stream cannot be read
	 */
	public SecureContext load(InputStream input) throws IOException {
		if (input == null) {
			throw new IllegalArgumentException("input cannot be null");
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;

			while ((line = reader.readLine()) != null) {
				String value = line.trim();

				if (value.isEmpty() || value.startsWith("#")) {
					continue;
				}

				if (value.startsWith("\\-")) {
					redact(value.substring(1));
				} else if (value.startsWith("-")) {
					unredact(value.substring(1));
				} else {
					redact(value);
				}
			}
		}

		return this;
	}

	/**
	 * Adds response fields that should be included in filtered output.
	 *
	 * <p>
	 * Rules can be field names, wildcard JSON paths, or regex rules.
	 * </p>
	 *
	 * <pre>
	 * secure.filter("id", "title", "/reviews/&#42;/date", "/&#42;&#42;/rating");
	 * </pre>
	 *
	 * @param rules filter rules
	 * @return this context
	 */
	public SecureContext filter(String... rules) {
		if (rules != null) {
			filters.addAll(Params.asList(rules));
		}
		return this;
	}

	/**
	 * Adds response header filter rules.
	 *
	 * <p>
	 * When header filters are configured, only matching response headers are
	 * included in the logged response headers.
	 * </p>
	 *
	 * @param names header names to include
	 * @return this context
	 */
	public SecureContext headersFilter(String... names) {
		if (names != null) {
			headerFilters.addAll(Params.asList(names));
		}
		return this;
	}

	/**
	 * Removes response header filter rules.
	 *
	 * @param names header names to remove from filtered header output
	 * @return this context
	 */
	public SecureContext removeHeaders(String... names) {
		removeHeaders(headerFilters, names);
		return this;
	}

	/**
	 * Sets the redaction policy.
	 *
	 * @param redactionPolicy redaction policy
	 * @return this context
	 */
	public SecureContext redactionPolicy(RedactionPolicy redactionPolicy) {
		this.redactionPolicy = redactionPolicy == null ? RedactionPolicy.defaults() : redactionPolicy;
		return this;
	}

	/**
	 * Adds request fields that should be redacted.
	 *
	 * @param rules field redaction rules
	 * @return this context
	 */
	public SecureContext redact(String... rules) {
		this.redactionPolicy = this.redactionPolicy.addRules(rules);
		return this;
	}

	/**
	 * Removes fields from redaction.
	 *
	 * @param rules field redaction rules to remove
	 * @return this context
	 */
	public SecureContext unredact(String... rules) {
		this.redactionPolicy = this.redactionPolicy.removeRules(rules);
		return this;
	}

	/**
	 * Adds protected header names whose values should be fully masked in logs.
	 *
	 * @param names header names
	 * @return this secure context
	 */
	public SecureContext headers(String... names) {
		this.redactionPolicy = this.redactionPolicy.headers(names);
		return this;
	}

	/**
	 * Adds plain values used for request resolution.
	 *
	 * <p>
	 * Values must be provided as key/value pairs.
	 * </p>
	 *
	 * <pre>
	 * secure.plain("username", "admin", "role", "tester");
	 * </pre>
	 *
	 * @param values plain key/value pairs
	 * @return this context
	 */
	public SecureContext plain(Object... values) {
		return plain(Params.asMap(values));
	}

	/**
	 * Adds plain values used for request resolution.
	 *
	 * @param values plain values
	 * @return this context
	 */
	public SecureContext plain(Map<String, ?> values) {
		this.values.plain(values);
		return this;
	}

	/**
	 * Adds plain environment values for request resolution.
	 *
	 * @param values environment containing plain values
	 * @return this context
	 */
	public SecureContext plain(Environment values) {
		return plain(values.getParams());
	}

	/**
	 * Adds protected values used for request resolution.
	 *
	 * <p>
	 * Values must be provided as key/value pairs.
	 * </p>
	 *
	 * <pre>
	 * secure.secret("username", "admin", "password", "secret");
	 * </pre>
	 *
	 * @param values protected key/value pairs
	 * @return this context
	 */
	public SecureContext secret(Object... values) {
		return secret(Params.asMap(values));
	}

	/**
	 * Adds protected values used for request resolution.
	 *
	 * @param values protected values
	 * @return this context
	 */
	public SecureContext secret(Map<String, ?> values) {
		this.values.secrets(values);
		return this;
	}

	/**
	 * Adds protected environment values for request resolution.
	 *
	 * @param values environment containing protected values
	 * @return this context
	 */
	public SecureContext secret(Environment values) {
		return secret(values.getParams());
	}

	/**
	 * Creates a copy of this secure context.
	 *
	 * <p>
	 * The copy keeps the configured values, filters, and redaction policy, but does
	 * not copy the latest request or response state.
	 * </p>
	 *
	 * @return copied secure context
	 */
	public SecureContext copy() {
		SecureContext copy = new SecureContext();
		copy.values.values(values.build());
		copy.redactionPolicy = redactionPolicy;
		copy.filters.addAll(filters);
		copy.headerFilters.addAll(headerFilters);
		return copy;
	}

	private static void removeHeaders(List<String> filters, String... names) {
		if (filters.isEmpty() || names == null || names.length == 0) {
			return;
		}

		List<String> values = Params.asList(names);
		filters.removeIf(filter -> values.stream().anyMatch(value -> value.equalsIgnoreCase(filter)));
	}

	/**
	 * Wraps a request using this secure context.
	 *
	 * @param request request to wrap
	 * @return secure request
	 */
	public SecureRequest from(Request request) {
		this.response = null;
		return this.request = SecureRequest.from(request).redactionPolicy(redactionPolicy).values(values.build());
	}

	/**
	 * Wraps a response using this secure context.
	 *
	 * @param response response to wrap
	 * @return secure API response
	 */
	public SecureResponse from(ApiResponse response) {
		return this.response = SecureResponse.from(response).redactionPolicy(redactionPolicy).values(values.build())
				.filter(filters).headersFilter(headerFilters);
	}

	/**
	 * Returns the redaction policy.
	 *
	 * @return redaction policy
	 */
	public RedactionPolicy redactionPolicy() {
		return redactionPolicy;
	}

	/**
	 * Returns a secure log containing the latest wrapped request and response.
	 *
	 * @return request and response log
	 */
	public String log() {
		return log(true);
	}

	/**
	 * Returns a secure log containing the latest wrapped request and response.
	 *
	 * @param resolve true to show resolved review values; false to show original
	 *                values
	 * @return request and response log
	 */
	public String log(boolean resolve) {
		StringBuilder sb = new StringBuilder();

		if (request != null) {
			sb.append("\n********** SecureRequest: **********\n");
			sb.append(request.log(resolve));
		}

		if (response != null) {
			sb.append("\n\n**********SecureResponse: **********\n");
			sb.append(response.log(resolve));
		}

		return sb.toString();
	}
}
