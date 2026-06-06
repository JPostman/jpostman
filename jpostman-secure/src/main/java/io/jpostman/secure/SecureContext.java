package io.jpostman.secure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.jpostman.ApiResponse;
import io.jpostman.Environment;
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
	 * Wraps a request using this secure context.
	 *
	 * @param request request to wrap
	 * @return secure request
	 */
	public SecureRequest from(Request request) {
		return SecureRequest.from(request).redactionPolicy(redactionPolicy).values(values.build());
	}

	/**
	 * Wraps a response using this secure context.
	 *
	 * @param response response to wrap
	 * @return secure API response
	 */
	public SecureResponse from(ApiResponse response) {
		return SecureResponse.from(response).redactionPolicy(redactionPolicy).values(values.build());
	}

	/**
	 * Returns the redaction policy.
	 *
	 * @return redaction policy
	 */
	public RedactionPolicy redactionPolicy() {
		return redactionPolicy;
	}
}
