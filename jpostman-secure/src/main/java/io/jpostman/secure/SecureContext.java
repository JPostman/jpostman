package io.jpostman.secure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jpostman.ApiExecutor;
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

	private static final Logger log = LoggerFactory.getLogger(SecureContext.class);

	private final SecureValues.Builder values = SecureValues.builder();

	private RedactionPolicy redactionPolicy = RedactionPolicy.defaults();

	private final List<String> filters = new ArrayList<>();
	private final List<String> headerFilters = new ArrayList<>();
	private final Map<String, PolicySection> policySections = new LinkedHashMap<>();

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

		List<String> lines = readLines(input);

		if (isPolicy(lines)) {
			return loadPolicyLines(lines);
		}

		for (String line : lines) {
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

		return this;
	}

	/**
	 * Loads secure policy profiles from an INI-style input stream.
	 *
	 * <p>
	 * Sections define reusable profiles. The {@code default} section is applied to
	 * this context after loading. Other profiles can be applied with
	 * {@link #loadRules(String...)}.
	 * </p>
	 *
	 * @param input input stream containing policy profiles
	 * @return this context
	 * @throws IOException if the input stream cannot be read
	 */
	public SecureContext loadPolicy(InputStream input) throws IOException {
		if (input == null) {
			throw new IllegalArgumentException("input cannot be null");
		}

		return loadPolicyLines(readLines(input));
	}

	private SecureContext loadPolicyLines(List<String> lines) {
		PolicySection current = null;

		for (String line : lines) {
			String value = line.trim();

			if (value.isEmpty() || value.startsWith("#")) {
				continue;
			}

			if (value.startsWith("[") && value.endsWith("]")) {
				String name = value.substring(1, value.length() - 1).trim();
				if (name.isEmpty()) {
					throw new IllegalArgumentException("policy section name cannot be blank");
				}
				current = policySections.computeIfAbsent(name, PolicySection::new);
				continue;
			}

			if (current == null) {
				throw new IllegalArgumentException("policy rule must be inside a section: " + value);
			}

			int index = value.indexOf('=');
			if (index < 0) {
				throw new IllegalArgumentException("policy rule must use key=value format: " + value);
			}

			current.add(value.substring(0, index).trim(), value.substring(index + 1).trim());
		}

		if (policySections.containsKey("default")) {
			applyRules("default", new LinkedHashSet<>(), new LinkedHashSet<>());
		}

		return this;
	}

	private static List<String> readLines(InputStream input) throws IOException {
		List<String> lines = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}

		return lines;
	}

	private static boolean isPolicy(List<String> lines) {
		for (String line : lines) {
			String value = line.trim();
			if (value.isEmpty() || value.startsWith("#")) {
				continue;
			}
			return value.startsWith("[") && value.endsWith("]");
		}
		return false;
	}

	/**
	 * Applies named policy profiles to a copied context.
	 *
	 * @param names profile names to apply
	 * @return copied context with the selected rules applied
	 */
	public SecureContext loadRules(String... names) {
		SecureContext copy = copy();

		if (names != null) {
			Set<String> applied = new LinkedHashSet<>();
			if (copy.policySections.containsKey("default")) {
				applied.add("default");
			}
			for (String name : names) {
				copy.applyRules(name, applied, new LinkedHashSet<>());
			}
		}

		return copy;
	}

	private void applyRules(String name, Set<String> applied, Set<String> resolving) {
		if (name == null || name.trim().isEmpty()) {
			return;
		}

		String sectionName = name.trim();
		if (applied.contains(sectionName)) {
			return;
		}

		PolicySection section = policySections.get(sectionName);
		if (section == null) {
			throw new IllegalArgumentException("unknown policy section: " + sectionName);
		}

		if (!resolving.add(sectionName)) {
			throw new IllegalArgumentException("cyclic policy section dependency: " + sectionName);
		}

		for (String parent : section.extendsRules) {
			applyRules(parent, applied, resolving);
		}

		applyRuleValues(section.redact, this::redact, applied, resolving);
		applyRuleValues(section.unredact, this::unredact, applied, resolving);
		applyRuleValues(section.headers, this::headers, applied, resolving);
		applyRuleValues(section.unheaders, this::unheaders, applied, resolving);
		applyRuleValues(section.filter, this::filter, applied, resolving);
		applyRuleValues(section.headersFilter, this::headersFilter, applied, resolving);
		applyRuleValues(section.unsecret, this::unsecret, applied, resolving);

		resolving.remove(sectionName);
		applied.add(sectionName);
	}

	private void applyRuleValues(List<String> values, RuleApplier applier, Set<String> applied, Set<String> resolving) {
		if (values.isEmpty()) {
			return;
		}

		List<String> rules = new ArrayList<>();

		for (String value : values) {
			if (isSectionReference(value)) {
				applyRules(value.substring(1, value.length() - 1), applied, resolving);
			} else {
				rules.add(value);
			}
		}

		if (!rules.isEmpty()) {
			applier.apply(rules.toArray(new String[0]));
		}
	}

	private static boolean isSectionReference(String value) {
		return value != null && value.startsWith("[") && value.endsWith("]") && value.length() > 2;
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
	 * Sets response header filter rules.
	 *
	 * <p>
	 * When header filters are configured, only matching response headers are
	 * included in the logged response headers. Calling this method replaces any
	 * existing header filter rules.
	 * </p>
	 *
	 * @param names header names to include
	 * @return this context
	 */
	public SecureContext headersFilter(String... names) {
		headerFilters.clear();
		if (names != null) {
			headerFilters.addAll(Params.asList(names));
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
	 * Adds a redaction rule for keys that match the given regular expression.
	 * <p>
	 * This is useful when multiple fields should be protected by the same key
	 * pattern. For example, {@code (?i).*phone.*} can match keys such as
	 * {@code phone}, {@code backupPhone}, and {@code otherPhone}.
	 *
	 * @param keyRegex the regular expression used to match field keys
	 * @return this secure context
	 */
	public SecureContext redactRegex(String keyRegex) {
		this.redactionPolicy = this.redactionPolicy.addRegexRule(keyRegex);
		return this;
	}

	/**
	 * Adds a redaction rule for keys that match the given regular expression and
	 * applies the given value expression to the matched values.
	 * <p>
	 * The value expression can be a slice expression, such as {@code [:3]} or
	 * {@code [-4:]}, or a regex value expression, such as
	 * {@code [regex:^\\+\\d{1,2}]}.
	 * <p>
	 * Example:
	 *
	 * <pre>
	 * redactRegex("(?i).*phone.*", "[:3]");
	 * redactRegex("(?i).*phone.*", "[regex:^\\+\\d{1,2}]");
	 * </pre>
	 *
	 * @param keyRegex        the regular expression used to match field keys
	 * @param valueExpression the slice or regex expression applied to matched
	 *                        values
	 * @return this secure context
	 */
	public SecureContext redactRegex(String keyRegex, String valueExpression) {
		this.redactionPolicy = this.redactionPolicy.addRegexRule(keyRegex, valueExpression);
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
	 * Converts protected values to plain values by key.
	 *
	 * @param names value keys to convert to plain values
	 * @return this context
	 */
	public SecureContext unsecret(String... names) {
		this.values.unsecret(names);
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
	 * Removes protected header names.
	 *
	 * @param names header names or regex rules to remove
	 * @return this secure context
	 */
	public SecureContext unheaders(String... names) {
		this.redactionPolicy = this.redactionPolicy.unheaders(names);
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
	 * Returns the current secure values.
	 *
	 * @return current secure values
	 */
	public SecureValues values() {
		return this.values.build();
	}

	/**
	 * Returns the current secure values.
	 *
	 * @return current secure values
	 */
	public Object get(String key) {
		SecureValue value = values().get(key);
		return value == null ? null : value.reveal();
	}

	/**
	 * Returns the value for the given key as a string.
	 *
	 * @param key the secure value key
	 * @return the value as a string, or an empty string if the key does not exist
	 */
	public String asString(String key) {
		Object value = get(key);
		return value == null ? "" : String.valueOf(value);
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
		for (Map.Entry<String, PolicySection> entry : policySections.entrySet()) {
			copy.policySections.put(entry.getKey(), entry.getValue().copy());
		}
		return copy;
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
	 * Wraps the response returned by the given executor as a secure response.
	 *
	 * @param executor the API executor used to execute the request
	 * @return the secure response wrapper
	 */
	public SecureResponse from(ApiExecutor executor) {
		return this.from(executor.response());
	}

	/**
	 * Wraps the given request and stores it in this secure context.
	 *
	 * @param request the request to protect
	 * @return this secure context
	 */
	public SecureContext request(Request request) {
		this.from(request);
		return this;
	}

	/**
	 * Wraps the given API response and stores it in this secure context.
	 *
	 * @param response the response to protect
	 * @return this secure context
	 */
	public SecureContext response(ApiResponse response) {
		this.from(response);
		return this;
	}

	/**
	 * Wraps the response returned by the given executor and stores it in this
	 * secure context.
	 *
	 * @param executor the API executor used to execute the request
	 * @return this secure context
	 */
	public SecureContext response(ApiExecutor executor) {
		this.from(executor.response());
		return this;
	}

	/**
	 * Returns the current secure request stored in this context.
	 *
	 * @return the current secure request, or {@code null} if no request was set
	 */
	public SecureRequest request() {
		return request;
	}

	/**
	 * Returns the current secure response stored in this context.
	 *
	 * @return the current secure response, or {@code null} if no response was set
	 */
	public SecureResponse response() {
		return response;
	}

	/**
	 * Logs the current secure request and response output at TRACE level.
	 */
	public void print() {
		print(true);
	}

	/**
	 * Logs the current secure request and response output at TRACE level.
	 *
	 * @param resolve {@code true} to resolve variables before logging, or
	 *                {@code false} to log unresolved placeholders
	 */
	public void print(boolean resolve) {
		log.trace(log(resolve));
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

	private interface RuleApplier {
		void apply(String... values);
	}

	private static final class PolicySection {
		private final String name;
		private final List<String> extendsRules = new ArrayList<>();
		private final List<String> redact = new ArrayList<>();
		private final List<String> unredact = new ArrayList<>();
		private final List<String> headers = new ArrayList<>();
		private final List<String> unheaders = new ArrayList<>();
		private final List<String> filter = new ArrayList<>();
		private final List<String> headersFilter = new ArrayList<>();
		private final List<String> unsecret = new ArrayList<>();

		private PolicySection(String name) {
			this.name = name;
		}

		private void add(String key, String value) {
			List<String> items = splitValues(value);

			switch (key) {
			case "extends":
				this.extendsRules.addAll(items);
				break;
			case "redact":
				this.redact.addAll(items);
				break;
			case "unredact":
				this.unredact.addAll(items);
				break;
			case "headers":
				this.headers.addAll(items);
				break;
			case "unheaders":
				this.unheaders.addAll(items);
				break;
			case "filter":
				this.filter.addAll(items);
				break;
			case "headersFilter":
				this.headersFilter.addAll(items);
				break;
			case "unsecret":
				this.unsecret.addAll(items);
				break;
			default:
				throw new IllegalArgumentException("unknown policy key in [" + name + "]: " + key);
			}
		}

		private PolicySection copy() {
			PolicySection copy = new PolicySection(name);
			copy.extendsRules.addAll(extendsRules);
			copy.redact.addAll(redact);
			copy.unredact.addAll(unredact);
			copy.headers.addAll(headers);
			copy.unheaders.addAll(unheaders);
			copy.filter.addAll(filter);
			copy.headersFilter.addAll(headersFilter);
			copy.unsecret.addAll(unsecret);
			return copy;
		}

		private static List<String> splitValues(String value) {
			List<String> result = new ArrayList<>();

			if (value == null || value.trim().isEmpty()) {
				return result;
			}

			StringBuilder item = new StringBuilder();
			int bracketDepth = 0;

			for (int i = 0; i < value.length(); i++) {
				char ch = value.charAt(i);
				if (ch == '[') {
					bracketDepth++;
				} else if (ch == ']' && bracketDepth > 0) {
					bracketDepth--;
				}

				if (ch == ',' && bracketDepth == 0) {
					addSplitValue(result, item);
					item.setLength(0);
				} else {
					item.append(ch);
				}
			}

			addSplitValue(result, item);

			return result;
		}

		private static void addSplitValue(List<String> result, StringBuilder item) {
			String trimmed = item.toString().trim();
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}
	}
}
