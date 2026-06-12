package io.jpostman.secure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.RequestProvider;

/**
 * Provides a secure wrapper around a request.
 * <p>
 * This class can build two versions of the request:
 * </p>
 * <ul>
 * <li>a resolved request for execution, containing the real secret values</li>
 * <li>a redacted request for review, logging, or debugging</li>
 * </ul>
 * <p>
 * Executors should execute the request returned by {@link #build()}
 * </p>
 */
public final class SecureRequest implements RequestProvider {

	private static final Logger log = LoggerFactory.getLogger(SecureRequest.class);

	private final Request request;
	private final SecureValues.Builder values = SecureValues.builder();
	private RedactionPolicy redactionPolicy = RedactionPolicy.defaults();

	private SecureRequest(Request request) {
		if (request == null) {
			throw new IllegalArgumentException("request cannot be null");
		}
		this.request = request;
	}

	/**
	 * Starts wrapping a request.
	 *
	 * @param request request to wrap
	 * @return secure request wrapper
	 */
	public static SecureRequest from(Request request) {
		return new SecureRequest(request);
	}

	/**
	 * Adds secure values used for request resolution.
	 *
	 * @param secureValues secure values
	 * @return this secure request
	 */
	public SecureRequest values(SecureValues secureValues) {
		this.values.values(secureValues);
		return this;
	}

	/**
	 * Sets the redaction policy.
	 *
	 * @param redactionPolicy redaction policy
	 * @return this secure request
	 */
	public SecureRequest redactionPolicy(RedactionPolicy redactionPolicy) {
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
	 * Adds request fields that should be redacted.
	 *
	 * <p>
	 * Examples:
	 * </p>
	 *
	 * <pre>
	 * redact("Authorization", "API-KEY", "password", "creditCard[-4:]")
	 * </pre>
	 *
	 * @param rules field redaction rules
	 * @return this secure request
	 */
	public SecureRequest redact(String... rules) {
		this.redactionPolicy = this.redactionPolicy.addRules(rules);
		return this;
	}

	/**
	 * Removes request fields from redaction.
	 *
	 * @param rules field redaction rules to remove
	 * @return this secure request
	 */
	public SecureRequest unredact(String... rules) {
		this.redactionPolicy = this.redactionPolicy.removeRules(rules);
		return this;
	}

	/**
	 * Adds protected header names whose values should be fully masked in logs.
	 *
	 * @param names header names
	 * @return this secure request
	 */
	public SecureRequest headers(String... names) {
		this.redactionPolicy = this.redactionPolicy.headers(names);
		return this;
	}

	/**
	 * Removes protected header names.
	 *
	 * @param names header names or regex rules to remove
	 * @return this secure request
	 */
	public SecureRequest unheaders(String... names) {
		this.redactionPolicy = this.redactionPolicy.unheaders(names);
		return this;
	}

	/**
	 * Adds plain values used for request resolution.
	 *
	 * <p>
	 * Plain values are resolved into the request and are not automatically masked.
	 * </p>
	 *
	 * @param values plain values
	 * @return this secure request
	 */
	public SecureRequest plain(Map<String, ?> values) {
		this.values.plain(values);
		return this;
	}

	/**
	 * Adds protected values used for request resolution.
	 *
	 * <p>
	 * Protected values are resolved into the request only when {@link #build()} is
	 * called. Debug and print output remain masked.
	 * </p>
	 *
	 * @param values protected values
	 * @return this secure request
	 */
	public SecureRequest secret(Map<String, ?> values) {
		this.values.secrets(values);
		return this;
	}

	/**
	 * Returns real values for resolving request variables.
	 *
	 * @return key/value map using unmasked values
	 */
	private Map<String, ?> resolveValues() {
		Map<String, Object> result = new LinkedHashMap<>();
		values.build().values().forEach((key, value) -> result.put(key, value.reveal()));
		return result;
	}

	/**
	 * Builds the real request by resolving stored values.
	 *
	 * <p>
	 * This is the only method that reveals protected values into the core request.
	 * </p>
	 *
	 * @return resolved request
	 */
	@Override
	public Request build() {
		Environment env = new Environment("jpostman-secure").builder().resolve(resolveValues()).end();
		return request.builder().build(env);
	}

	/**
	 * Returns unresolved redacted debug output.
	 *
	 * @return safe debug output
	 */
	public String log() {
		return log(true);
	}

	/**
	 * Returns redacted debug output.
	 *
	 * @param resolve true to resolve variables first
	 * @return safe debug output
	 */
	public String log(boolean resolve) {
		if (resolve) {
			return redactConcreteProtectedHeaders(SecureText.redact(build().log(), values.build(), redactionPolicy));
		}
		return redactConcreteProtectedHeaders(SecureText.redact(request.log(), redactionPolicy));
	}

	private String redactConcreteProtectedHeaders(String text) {
		String[] lines = text.split("\\R", -1);
		StringBuilder result = new StringBuilder();

		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				result.append('\n');
			}

			String line = lines[i];
			int index = line.indexOf('=');
			if (index < 0) {
				result.append(line);
				continue;
			}

			String name = line.substring(0, index).trim();
			String value = line.substring(index + 1);
			if (redactionPolicy.isHeaderProtected(name) && !value.contains("{{")) {
				result.append(line.substring(0, index + 1)).append(' ').append(redactionPolicy.mask());
			} else {
				result.append(line);
			}
		}

		return result.toString();
	}

	/**
	 * Logs detailed multi-line output including description, auth, headers, and
	 * body at TRACE level.
	 */
	public void print() {
		print(true);
	}

	/**
	 * Logs detailed multi-line output including description, auth, headers, and
	 * body at TRACE level.
	 * 
	 * @param resolve true to resolve variables first
	 */
	public void print(boolean resolve) {
		log.trace(log(resolve));
	}

	/**
	 * Returns unresolved redacted request summary.
	 *
	 * @return safe request summary
	 */
	@Override
	public String toString() {
		return SecureText.redact(request.toString(), redactionPolicy);
	}

	/**
	 * Returns the wrapped request.
	 *
	 * @return wrapped request
	 */
	public Request request() {
		return request;
	}
}
