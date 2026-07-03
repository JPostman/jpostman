package io.jpostman.secure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Utility methods for normalizing, matching, and reading JSON path rules.
 */
final class JsonPathRules {

	private JsonPathRules() {
	}

	/**
	 * Normalizes a filter or path rule to slash-based JSON path syntax.
	 *
	 * <pre>
	 * /products[0].id -> /products/0/id
	 * /products[0]/id -> /products/0/id
	 * /products/&#42;/id  -> /products/&#42;/id
	 * regex:.&#42;token.&#42; -> regex:.&#42;token.&#42;
	 * </pre>
	 *
	 * @param rule path rule
	 * @return normalized rule
	 */
	static String normalizeRule(String rule) {
		if (rule == null) {
			return "";
		}

		String value = rule.trim();

		if (value.startsWith("regex:")) {
			return value;
		}

		value = value.replaceAll("\\[(\\d+|\\*)\\]", "/$1");
		value = value.replace(".", "/");

		return normalizePath(value);
	}

	/**
	 * Normalizes a slash-based JSON path.
	 *
	 * @param path JSON path
	 * @return normalized path
	 */
	static String normalizePath(String path) {
		if (path == null) {
			return "";
		}

		String value = path.trim();

		if (!value.startsWith("/")) {
			value = "/" + value;
		}

		while (value.contains("//")) {
			value = value.replace("//", "/");
		}

		if (value.length() > 1 && value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}

		return value;
	}

	/**
	 * Converts slash-based exact paths to simple dot/bracket paths.
	 *
	 * <pre>
	 * /products[0].id -> products[0].id
	 * /products[0]/id -> products[0].id
	 * /products/0/id  -> products[0].id
	 * </pre>
	 *
	 * @param path JSON path
	 * @return simple dot/bracket path
	 */
	static String toSimplePath(String path) {
		if (path == null) {
			return "";
		}

		String value = normalizeRule(path);

		if (value.startsWith("/")) {
			value = value.substring(1);
		}

		if (value.isEmpty()) {
			return value;
		}

		String[] parts = value.split("/");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part == null || part.isBlank()) {
				continue;
			}

			if (part.matches("\\d+")) {
				result.append("[").append(part).append("]");
				continue;
			}

			if (result.length() > 0) {
				result.append(".");
			}

			result.append(part);
		}

		return result.toString();
	}

	/**
	 * Returns {@code true} when the path should be handled as a secure path rule.
	 *
	 * <p>
	 * Secure path rules use slash-based paths or the {@code regex:} prefix. The
	 * caller can still pass exact slash paths; they are resolved through the same
	 * rule engine so wildcard and recursive paths can share the same behavior.
	 * </p>
	 *
	 * @param path JSON path or path rule
	 * @return {@code true} for slash-based or regex secure path rules
	 */
	static boolean isRulePath(String path) {
		if (path == null) {
			return false;
		}

		String value = path.trim();
		return value.startsWith("/") || value.startsWith(RedactionPolicy.REGEX_PREFIX);
	}

	/**
	 * Checks whether a field key or JSON path matches a rule.
	 *
	 * @param key  field key
	 * @param path JSON path
	 * @param rule exact field name, slash/wildcard path, or regex rule
	 * @return {@code true} when the rule matches
	 */
	static boolean matchesRule(String key, String path, String rule) {
		if (rule == null || rule.trim().isEmpty()) {
			return false;
		}

		String trimmed = rule.trim();

		if (trimmed.startsWith(RedactionPolicy.REGEX_PREFIX)) {
			String regex = trimmed.substring(RedactionPolicy.REGEX_PREFIX.length()).trim();
			Pattern pattern = Pattern.compile(regex);
			return pattern.matcher(key == null ? "" : key).matches()
					|| pattern.matcher(path == null ? "" : normalizePath(path)).matches();
		}

		if (!trimmed.startsWith("/")) {
			return key != null && key.equals(trimmed);
		}

		String normalized = normalizeRule(trimmed);

		return path != null && matches(normalized, path);
	}

	/**
	 * Checks whether a name matches an exact or regex rule.
	 *
	 * @param name name to check
	 * @param rule exact name or regex rule
	 * @return {@code true} when the rule matches
	 */
	static boolean matchesName(String name, String rule) {
		if (name == null || rule == null || rule.trim().isEmpty()) {
			return false;
		}

		String trimmed = rule.trim();

		if (trimmed.startsWith(RedactionPolicy.REGEX_PREFIX)) {
			String regex = trimmed.substring(RedactionPolicy.REGEX_PREFIX.length()).trim();
			return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name).matches();
		}

		return trimmed.equalsIgnoreCase(name);
	}

	/**
	 * Checks whether a path rule matches a JSON path.
	 *
	 * @param pattern path rule
	 * @param path    JSON path
	 * @return {@code true} when the rule matches
	 */
	static boolean matches(String pattern, String path) {
		String[] patternParts = normalizePath(pattern).split("/");
		String[] pathParts = normalizePath(path).split("/");

		return matches(patternParts, 0, pathParts, 0);
	}

	private static boolean matches(String[] patternParts, int patternIndex, String[] pathParts, int pathIndex) {
		if (patternIndex == patternParts.length && pathIndex == pathParts.length) {
			return true;
		}

		if (patternIndex == patternParts.length) {
			return false;
		}

		String patternPart = patternParts[patternIndex];

		if ("**".equals(patternPart)) {
			if (matches(patternParts, patternIndex + 1, pathParts, pathIndex)) {
				return true;
			}

			return pathIndex < pathParts.length && matches(patternParts, patternIndex, pathParts, pathIndex + 1);
		}

		if (pathIndex == pathParts.length) {
			return false;
		}

		if ("*".equals(patternPart) || patternPart.equals(pathParts[pathIndex])) {
			return matches(patternParts, patternIndex + 1, pathParts, pathIndex + 1);
		}

		return false;
	}

	/**
	 * Reads all JSON values that match a path rule.
	 *
	 * @param element JSON element to scan
	 * @param rule    path rule
	 * @param <T>     expected item type
	 * @return matching values
	 */
	static <T> List<T> paths(JsonElement element, String rule) {
		if (rule == null || rule.trim().isEmpty()) {
			return List.of();
		}

		List<T> result = new ArrayList<>();
		paths(element, null, "", rule, result);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T> void paths(JsonElement element, String key, String path, String rule, List<T> result) {
		if (element == null || element.isJsonNull()) {
			return;
		}

		if (matchesRule(key, path, rule)) {
			result.add((T) jsonValue(element));
			return;
		}

		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();

			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				String childKey = entry.getKey();
				paths(entry.getValue(), childKey, path + "/" + childKey, rule, result);
			}

			return;
		}

		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();

			for (int i = 0; i < array.size(); i++) {
				paths(array.get(i), null, path + "/" + i, rule, result);
			}
		}
	}

	/**
	 * Converts a JSON element to a Java value.
	 *
	 * @param element JSON element
	 * @return Java value
	 */
	private static Object jsonValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}

		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();

			if (primitive.isBoolean()) {
				return primitive.getAsBoolean();
			}

			if (primitive.isNumber()) {
				String value = primitive.getAsString();

				if (!value.contains(".") && !value.contains("e") && !value.contains("E")) {
					try {
						return Integer.valueOf(value);
					} catch (NumberFormatException e) {
						return Long.valueOf(value);
					}
				}

				return Double.valueOf(value);
			}

			return primitive.getAsString();
		}

		return element;
	}
}