package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.loadProperties;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.open;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

import io.jpostman.annotations.JPostmanAssert;

/**
 * Loads and applies assertion rules declared by {@link JPostmanAssert}.
 *
 * @param <C> framework context type
 */
final class JPostmanAssertionRunner<C> {

	private final JPostmanFramework<C> framework;

	JPostmanAssertionRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
	}

	void apply(Class<?> testClass, C ctx, JPostmanAssert annotation, String requestName, boolean soft, boolean log)
			throws Exception {

		String rulesLocation = assertionRulesLocation(annotation, testClass);
		if (rulesLocation.isBlank()) {
			String namespace = annotation.namespace();
			throw new IllegalStateException(
					"JPostman assertion rules are required. Configure @JPostmanAssert(rules=...), " + "assertions"
							+ (namespace.isBlank() ? "" : "." + namespace)
							+ ", or fallback property assertions in jpostman.properties.");
		}

		Map<String, Map<String, String>> rules;
		try (InputStream input = open(rulesLocation, testClass)) {
			rules = loadAssertionRules(input);
		}

		Map<String, String> resolved = resolveAssertionRules(rules, annotation.sections(), requestName);
		if (resolved.isEmpty()) {
			return;
		}

		Object asserts = assertionTarget(ctx, soft, log);
		for (Map.Entry<String, String> entry : resolved.entrySet()) {
			applyAssertionRule(asserts, entry.getKey(), entry.getValue());
		}

		if (soft) {
			invokeOptional(asserts, "assertAll");
		}
	}

	private String assertionRulesLocation(JPostmanAssert annotation, Class<?> testClass) throws IOException {
		if (!annotation.rules().isBlank()) {
			return annotation.rules();
		}

		Properties properties = loadProperties("classpath:jpostman.properties", testClass);
		String namespace = annotation.namespace();
		if (namespace != null && !namespace.isBlank()) {
			String namespaced = properties.getProperty("assertions." + namespace, "").trim();
			if (!namespaced.isBlank()) {
				return namespaced;
			}
		}

		return properties.getProperty("assertions", "").trim();
	}

	private Map<String, Map<String, String>> loadAssertionRules(InputStream input) throws IOException {
		Map<String, Map<String, String>> sections = new LinkedHashMap<>();
		String current = "";
		sections.put(current, new LinkedHashMap<>());

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String value = line.trim();
				if (value.isBlank() || value.startsWith("#") || value.startsWith(";")) {
					continue;
				}

				if (value.startsWith("[") && value.endsWith("]")) {
					current = value.substring(1, value.length() - 1).trim();
					sections.putIfAbsent(current, new LinkedHashMap<>());
					continue;
				}

				int index = value.indexOf('=');
				if (index < 0) {
					throw new IllegalStateException("Invalid assertion rule line: " + line);
				}

				String key = value.substring(0, index).trim();
				String ruleValue = value.substring(index + 1).trim();
				sections.get(current).put(key, ruleValue);
			}
		}

		return sections;
	}

	/**
	 * Resolves the effective assertion rules for a request.
	 *
	 * <p>
	 * A section matching the current Postman request name is authoritative. When it
	 * exists, only that section and its own {@code extends} chain are used. This
	 * keeps request-specific sections independent from configured
	 * {@link JPostmanAssert} sections such as {@code product}. When no request
	 * section exists, configured sections are applied.
	 * </p>
	 *
	 * @param rules       loaded assertion rule sections
	 * @param sections    configured assertion sections from {@link JPostmanAssert}
	 * @param requestName current Postman request name
	 * @return resolved assertion rules
	 */
	private Map<String, String> resolveAssertionRules(Map<String, Map<String, String>> rules, String[] sections,
			String requestName) {

		if (requestName != null && rules.containsKey(requestName)) {
			return resolveAssertionSection(rules, requestName, new LinkedHashSet<>());
		}

		Map<String, String> resolved = new LinkedHashMap<>();
		String[] selected = sections == null || sections.length == 0 ? new String[] { "default" } : sections;
		for (String section : selected) {
			if (section != null && !section.isBlank()) {
				mergeAssertionRules(resolved, resolveAssertionSection(rules, section.trim(), new LinkedHashSet<>()));
			}
		}

		return resolved;
	}

	private Map<String, String> resolveAssertionSection(Map<String, Map<String, String>> rules, String section,
			Set<String> resolving) {

		Map<String, String> values = rules.get(section);
		if (values == null) {
			return new LinkedHashMap<>();
		}

		if (!resolving.add(section)) {
			throw new IllegalStateException(
					"Circular assertion rule inheritance detected: " + resolving + " -> " + section);
		}

		Map<String, String> resolved = new LinkedHashMap<>();
		String parentList = values.get("extends");
		if (parentList != null && !parentList.isBlank()) {
			for (String parent : splitValues(parentList)) {
				mergeAssertionRules(resolved, resolveAssertionSection(rules, parent, resolving));
			}
		}

		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!"extends".equals(entry.getKey())) {
				mergeAssertionRule(resolved, entry.getKey(), entry.getValue());
			}
		}

		resolving.remove(section);
		return resolved;
	}

	/**
	 * Merges assertion rules while preserving inherited parent assertions.
	 *
	 * <p>
	 * Repeatable assertion keys are appended so inherited checks are not lost when
	 * a child section defines the same key. Singleton keys, such as statusCode,
	 * keep the latest value so a request-specific section can override a parent
	 * default.
	 * </p>
	 *
	 * @param target target rule map to update
	 * @param source source rule map to merge
	 */
	private void mergeAssertionRules(Map<String, String> target, Map<String, String> source) {
		for (Map.Entry<String, String> entry : source.entrySet()) {
			mergeAssertionRule(target, entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Merges a single assertion rule into an existing rule map.
	 *
	 * <p>
	 * Repeatable rules use comma-separated values, so parent and child values are
	 * combined. Non-repeatable rules replace the previous value.
	 * </p>
	 *
	 * @param target target rule map to update
	 * @param key    assertion rule key
	 * @param value  assertion rule value
	 */
	private void mergeAssertionRule(Map<String, String> target, String key, String value) {
		if (key == null || key.isBlank()) {
			return;
		}

		if (!isRepeatableRule(key)) {
			target.put(key, value);
			return;
		}

		String current = target.get(key);
		if (current == null || current.isBlank()) {
			target.put(key, value);
			return;
		}

		if (value == null || value.isBlank()) {
			return;
		}

		target.put(key, appendRuleValues(current, value));
	}

	/**
	 * Appends repeatable rule values while removing duplicates.
	 *
	 * <p>
	 * This preserves parent/child ordering and prevents duplicated assertions when
	 * multiple selected sections inherit the same parent rule.
	 * </p>
	 *
	 * @param current current comma-separated rule values
	 * @param value   values to append
	 * @return comma-separated values with duplicates removed
	 */
	private String appendRuleValues(String current, String value) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		result.addAll(splitValues(current));
		result.addAll(splitValues(value));
		return String.join(",", result);
	}

	/**
	 * Returns whether an assertion rule can safely accumulate inherited values.
	 *
	 * @param key assertion rule key
	 * @return {@code true} for repeatable assertion keys; otherwise {@code false}
	 */
	private boolean isRepeatableRule(String key) {
		switch (key) {
		case "exists":
		case "notExists":
		case "pathNotNull":
		case "pathEquals":
		case "compare":
		case "allMatch":
			return true;
		default:
			return false;
		}
	}

	private Object assertionTarget(C ctx, boolean soft, boolean log) throws Exception {
		if (soft) {
			Object target = invokeOptional(ctx, "soft", log);
			if (target != null) {
				return target;
			}
			target = invokeOptional(ctx, "soft");
			if (target != null) {
				return target;
			}
		}

		Object target = invokeOptional(ctx, "asserts", log);
		if (target != null) {
			return target;
		}

		target = invokeOptional(ctx, "asserts");
		if (target != null) {
			return target;
		}

		throw new IllegalStateException("Unable to create assertion target for " + framework.name() + " context.");
	}

	private void applyAssertionRule(Object asserts, String key, String value) throws Exception {
		switch (key) {
		case "statusCode":
			invokeAssertion(asserts, "statusCode", Integer.parseInt(value.trim()));
			return;
		case "exists":
			for (String path : splitValues(value)) {
				invokeAssertion(asserts, "exists", path);
			}
			return;
		case "notExists":
			for (String path : splitValues(value)) {
				invokeAssertion(asserts, "notExists", path);
			}
			return;
		case "pathNotNull":
			for (String path : splitValues(value)) {
				invokeAssertion(asserts, "pathNotNull", path);
			}
			return;
		case "pathEquals":
			for (String entry : splitValues(value)) {
				int index = entry.indexOf('=');
				if (index < 0) {
					throw new IllegalStateException("pathEquals rule must use path=value: " + entry);
				}
				invokeAssertion(asserts, "pathEquals", entry.substring(0, index).trim(),
						scalar(entry.substring(index + 1).trim()));
			}
			return;
		case "compare":
			for (String expression : splitValues(value)) {
				Comparison comparison = Comparison.parseComparison(expression);

				Object actual = invokePath(asserts, comparison.path);
				Object expected = scalar(comparison.expected);

				boolean passed = comparison.compare(actual, comparison.operator, expected);

				invokeAssertion(asserts, "isTrue", passed, "Expected path " + comparison.path + " "
						+ comparison.operator + " " + expected + " but was " + actual);
			}
			return;
		case "allMatch":
			for (AllMatchRule rule : parseAllMatchRules(value)) {
				invokeAssertion(asserts, "allMatch", rule.comparison.path, rule.predicate(), rule.message);
			}
			return;
		default:
			throw new IllegalStateException("Unsupported JPostman assertion rule: " + key);
		}
	}

	private List<AllMatchRule> parseAllMatchRules(String expression) {
		List<AllMatchRule> rules = new ArrayList<>();
		String value = expression == null ? "" : expression.trim();
		if (value.isBlank()) {
			return rules;
		}

		if (value.contains("|")) {
			rules.add(parseAllMatchRule(value));
			return rules;
		}

		for (String condition : splitValues(value)) {
			rules.add(parseAllMatchRule(condition));
		}
		return rules;
	}

	private AllMatchRule parseAllMatchRule(String expression) {
		String value = expression == null ? "" : expression.trim();
		String message = "";

		int messageIndex = value.indexOf('|');
		if (messageIndex >= 0) {
			message = value.substring(messageIndex + 1).trim();
			value = value.substring(0, messageIndex).trim();
		}

		Comparison comparison = Comparison.parseComparison(value);
		Object expected = scalar(comparison.expected);

		if (message.isBlank()) {
			message = "Item: {}, Index: {} failed allMatch condition: " + value;
		}

		return new AllMatchRule(comparison, expected, message);
	}

	private Object invokePath(Object asserts, String path) throws Exception {
		Object context = invokeOptional(asserts, "context");
		if (context != null) {
			return invokeCompatible(context, "path", path);
		}

		return invokeCompatible(asserts, "path", path);
	}

	private List<String> splitValues(String value) {
		List<String> result = new ArrayList<>();
		if (value == null || value.isBlank()) {
			return result;
		}
		for (String item : value.split(",")) {
			String trimmed = item.trim();
			if (!trimmed.isBlank()) {
				result.add(trimmed);
			}
		}
		return result;
	}

	private Object scalar(String value) {
		if ("true".equalsIgnoreCase(value)) {
			return Boolean.TRUE;
		}
		if ("false".equalsIgnoreCase(value)) {
			return Boolean.FALSE;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private void invokeAssertion(Object target, String methodName, Object... args) throws Exception {
		invokeCompatible(target, methodName, args);
	}

	private Object invokeCompatible(Object target, String methodName, Object... args) throws Exception {
		for (Method method : target.getClass().getMethods()) {
			if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
				continue;
			}
			Class<?>[] types = method.getParameterTypes();
			boolean match = true;
			for (int i = 0; i < types.length; i++) {
				if (!isCompatible(types[i], args[i])) {
					match = false;
					break;
				}
			}
			if (match) {
				method.setAccessible(true);
				try {
					return method.invoke(target, args);
				} catch (InvocationTargetException e) {
					Throwable cause = e.getCause();
					if (cause instanceof Exception) {
						throw (Exception) cause;
					}
					if (cause instanceof Error) {
						throw (Error) cause;
					}
					throw e;
				}
			}
		}
		throw new IllegalStateException("Assertion method not found: " + methodName + "(" + args.length + ") on "
				+ target.getClass().getName());
	}

	private boolean isCompatible(Class<?> type, Object value) {
		if (value == null) {
			return !type.isPrimitive();
		}
		if (type.isPrimitive()) {
			if (type == int.class) {
				return value instanceof Integer;
			}
			if (type == boolean.class) {
				return value instanceof Boolean;
			}
			return false;
		}
		return type.isAssignableFrom(value.getClass()) || type == Object.class;
	}

	private Object invokeOptional(Object target, String methodName, Object... args) throws Exception {
		try {
			return invokeCompatible(target, methodName, args);
		} catch (IllegalStateException e) {
			return null;
		}
	}

	private static final class AllMatchRule {
		private final Comparison comparison;
		private final Object expected;
		private final String message;

		private AllMatchRule(Comparison comparison, Object expected, String message) {
			this.comparison = comparison;
			this.expected = expected;
			this.message = message;
		}

		private Predicate<Object> predicate() {
			return actual -> comparison.compare(actual, comparison.operator, expected);
		}
	}
}
