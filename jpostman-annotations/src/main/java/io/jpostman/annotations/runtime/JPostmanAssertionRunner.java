package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.open;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.propertyKey;

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

import io.jpostman.annotations.JPostmanContext;

/**
 * Loads and applies assertion rules configured by @JPostmanContext/config.
 *
 * @param <C> framework context type
 */
final class JPostmanAssertionRunner<C> {

	private static final Set<String> REDUNDANT_ASSERTION_WARNINGS = new LinkedHashSet<>();

	private final JPostmanFramework<C> framework;

	JPostmanAssertionRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
	}

	static List<String> resolveLocations(String[] assertions, Properties properties, String namespace,
			String configLocation, JPostmanContext info) {

		List<String> result = new ArrayList<>();
		Map<String, String> firstSource = new LinkedHashMap<>();

		if (assertions != null) {
			for (String value : assertions) {
				addLocations(result, firstSource, value, "@JPostmanContext value: assertions", configLocation, info);
			}
		}

		String key = propertyKey("assertions", namespace);
		addLocations(result, firstSource, property(properties, "assertions", namespace), key, configLocation, info);

		return result;
	}

	static Map<String, Map<String, String>> loadAssertionRules(Class<?> testClass, List<String> locations)
			throws IOException {

		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		Map<String, String> sectionSource = new LinkedHashMap<>();

		if (locations == null) {
			return result;
		}

		for (String location : locations) {
			try (InputStream input = open(location, testClass)) {
				Map<String, Map<String, String>> loaded = loadAssertionRules(input, location);
				for (Map.Entry<String, Map<String, String>> entry : loaded.entrySet()) {
					String section = entry.getKey();
					if (section == null || section.isBlank()) {
						continue;
					}
					String previous = sectionSource.putIfAbsent(section, location);
					if (previous != null) {
						throw new IllegalStateException(JPostmanErrors.message(JPostmanAssertionRunner.class,
								"Duplicate JPostman assertion section: " + section,
								"The same assertion section was found in more than one loaded assertion file.",
								"Found in:", "- " + previous, "- " + location,
								"Keep each assertion section name unique across loaded files."));
					}
					result.put(section, entry.getValue());
				}
			}
		}

		return result;
	}

	boolean apply(C ctx, Map<String, Map<String, String>> rules, String[] assertions, String requestName, boolean soft,
			boolean log) throws Exception {

		boolean explicitAssertions = hasAssertions(assertions);
		if (explicitAssertions && (rules == null || rules.isEmpty())) {
			throw new IllegalStateException(
					JPostmanErrors.message(JPostmanAssertionRunner.class, "No JPostman assertion files configured.",
							"Add @JPostmanContext(assertions = {...}) or config properties key assertions."));
		}

		Map<String, String> resolved = resolveAssertionRules(rules, assertions, requestName);
		if (resolved.isEmpty()) {
			return false;
		}

		Object asserts = assertionTarget(ctx, soft, log);
		for (Map.Entry<String, String> entry : resolved.entrySet()) {
			applyAssertionRule(asserts, entry.getKey(), entry.getValue());
		}

		if (soft) {
			invokeOptional(asserts, "assertAll");
		}
		return true;
	}

	private static void addLocations(List<String> result, Map<String, String> firstSource, String value, String source,
			String configLocation, JPostmanContext info) {
		if (value == null || value.isBlank()) {
			return;
		}

		for (String part : value.split(",")) {
			String location = part.trim();
			if (location.isBlank()) {
				continue;
			}

			String previous = firstSource.putIfAbsent(location, source);
			if (previous != null) {
				String warningKey = configLocation + "|" + source + "|" + location;
				if (REDUNDANT_ASSERTION_WARNINGS.add(warningKey)) {
					System.err.println(JPostmanErrors.message(info, "Redundant JPostman assertions mapping ignored.",
							"The same assertion file is configured more than once.",
							"Using " + previous + "=" + location,
							"Ignored config mapping: " + configLocation + " -> " + source + "=" + location));
				}
				continue;
			}

			result.add(location);
		}
	}

	private static Map<String, Map<String, String>> loadAssertionRules(InputStream input, String location)
			throws IOException {
		Map<String, Map<String, String>> sections = new LinkedHashMap<>();
		String current = "";

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String value = line.trim();
				if (value.isBlank() || value.startsWith("#") || value.startsWith(";")) {
					continue;
				}

				if (value.startsWith("[") && value.endsWith("]")) {
					current = value.substring(1, value.length() - 1).trim();
					if (sections.containsKey(current)) {
						throw new IllegalStateException(JPostmanErrors.message(JPostmanAssertionRunner.class,
								"Duplicate JPostman assertion section: " + current,
								"The same assertion section appears more than once in one assertion file.",
								"File: " + location, "Keep each assertion section name unique."));
					}
					sections.put(current, new LinkedHashMap<>());
					continue;
				}

				int index = value.indexOf('=');
				if (index < 0) {
					throw new IllegalStateException("Invalid assertion rule line: " + line);
				}

				if (current.isBlank()) {
					throw new IllegalStateException("Assertion rule is outside a section in " + location + ": " + line);
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
	 * exists, only that section and its own {@code extends} chain are used. When no
	 * request section exists, configured {@code asserts} sections are applied. If
	 * no section is configured, the {@code default} assertion section is applied
	 * when it exists.
	 * </p>
	 */
	private Map<String, String> resolveAssertionRules(Map<String, Map<String, String>> rules, String[] assertions,
			String requestName) {
		if (rules == null || rules.isEmpty()) {
			return new LinkedHashMap<>();
		}

		Map<String, String> resolved = new LinkedHashMap<>();
		boolean explicitAssertions = hasAssertions(assertions);

		if (explicitAssertions) {
			for (String section : assertions) {
				if (section == null || section.isBlank()) {
					continue;
				}

				String name = section.trim();
				if (!rules.containsKey(name)) {
					throw new IllegalStateException(JPostmanErrors.message(JPostmanAssertionRunner.class,
							"JPostman assertion section not found: " + name));
				}

				mergeAssertionRules(resolved, resolveAssertionSection(rules, name, new LinkedHashSet<>()));
			}
			return resolved;
		}

		if (requestName != null && rules.containsKey(requestName)) {
			return resolveAssertionSection(rules, requestName, new LinkedHashSet<>());
		}

		if (rules.containsKey("default")) {
			return resolveAssertionSection(rules, "default", new LinkedHashSet<>());
		}

		return resolved;
	}

	private static boolean hasAssertions(String[] assertions) {
		if (assertions == null) {
			return false;
		}
		for (String assertion : assertions) {
			if (assertion != null && !assertion.isBlank()) {
				return true;
			}
		}
		return false;
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

	private void mergeAssertionRules(Map<String, String> target, Map<String, String> source) {
		for (Map.Entry<String, String> entry : source.entrySet()) {
			mergeAssertionRule(target, entry.getKey(), entry.getValue());
		}
	}

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

	private String appendRuleValues(String current, String value) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		result.addAll(splitValues(current));
		result.addAll(splitValues(value));
		return String.join(",", result);
	}

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
