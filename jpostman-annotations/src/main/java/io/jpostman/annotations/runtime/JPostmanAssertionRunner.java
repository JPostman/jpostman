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

	private Map<String, String> resolveAssertionRules(Map<String, Map<String, String>> rules, String[] sections,
			String requestName) {

		Map<String, String> resolved = new LinkedHashMap<>();
		String[] selected = sections == null || sections.length == 0 ? new String[] { "default" } : sections;
		for (String section : selected) {
			if (section != null && !section.isBlank()) {
				resolved.putAll(resolveAssertionSection(rules, section.trim(), new LinkedHashSet<>()));
			}
		}

		if (requestName != null && rules.containsKey(requestName)) {
			resolved.putAll(resolveAssertionSection(rules, requestName, new LinkedHashSet<>()));
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
				resolved.putAll(resolveAssertionSection(rules, parent, resolving));
			}
		}

		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!"extends".equals(entry.getKey())) {
				resolved.put(entry.getKey(), entry.getValue());
			}
		}

		resolving.remove(section);
		return resolved;
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
		default:
			throw new IllegalStateException("Unsupported JPostman assertion rule: " + key);
		}
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
}
