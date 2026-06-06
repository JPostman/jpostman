package io.jpostman.secure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Utility methods for redacting protected values from strings.
 */
public final class SecureText {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private SecureText() {
	}

	/**
	 * Redacts protected values, protected placeholders, key assignments, JSON
	 * fields, and JSON paths.
	 *
	 * @param text   source text
	 * @param values secure values
	 * @param policy redaction policy
	 * @return redacted text
	 */
	public static String redact(String text, SecureValues values, RedactionPolicy policy) {
		if (text == null || text.isEmpty()) {
			return text == null ? "" : text;
		}
		RedactionPolicy effectivePolicy = policy == null ? RedactionPolicy.defaults() : policy;
		SecureValues effectiveValues = values == null ? SecureValues.empty() : values;
		String result = text;
		result = redactKnownProtectedValues(result, effectiveValues, effectivePolicy.mask());
		result = redactProtectedPlaceholders(result, effectiveValues, effectivePolicy);
		result = redactAssignmentsByKey(result, effectivePolicy);
		result = redactJsonFieldsByKey(result, effectivePolicy);
		result = redactJsonPaths(result, effectivePolicy);
		return result;
	}

	/**
	 * Redacts text using key, slice, and JSON path rules only.
	 *
	 * @param text   source text
	 * @param policy redaction policy
	 * @return redacted text
	 */
	public static String redact(String text, RedactionPolicy policy) {
		if (text == null || text.isEmpty()) {
			return text == null ? "" : text;
		}
		RedactionPolicy effectivePolicy = policy == null ? RedactionPolicy.defaults() : policy;
		String result = text;
		result = redactAssignmentsByExplicitSliceRule(result, effectivePolicy);
		result = redactJsonFieldsByKey(result, effectivePolicy);
		result = redactJsonPaths(result, effectivePolicy);
		return result;
	}

	private static String redactKnownProtectedValues(String text, SecureValues values, String mask) {
		String result = text;
		for (SecureValue value : values.values().values()) {
			if (value.isProtected() && !value.reveal().isEmpty()) {
				result = result.replace(value.reveal(), mask);
			}
		}
		return result;
	}

	private static String redactAssignmentsByExplicitSliceRule(String text, RedactionPolicy policy) {
		Pattern pattern = Pattern.compile("(?m)^([ \\t]*)([^=:\\n]{1,80})([ \\t]*[=:][ \\t]*)(.+)$");
		Matcher matcher = pattern.matcher(text);
		StringBuffer output = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(2).trim();
			String value = matcher.group(4);
			SliceExpressionFactory slice = policy.sliceExpressionFor(key);
			if (slice != null) {
				String replacement = matcher.group(1) + matcher.group(2) + matcher.group(3)
						+ slice.mask(value, policy.mask());
				matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
			} else {
				matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(output);
		return output.toString();
	}

	private static String redactProtectedPlaceholders(String text, SecureValues values, RedactionPolicy policy) {
		String result = text;
		for (String key : values.values().keySet()) {
			SecureValue value = values.get(key);
			if (value != null && value.isProtected()) {
				String pattern = "\\{\\{\\s*" + Pattern.quote(key) + "\\s*\\}\\}";
				result = result.replaceAll(pattern, Matcher.quoteReplacement(policy.mask()));
			}
		}
		return result;
	}

	private static String redactAssignmentsByKey(String text, RedactionPolicy policy) {
		Pattern pattern = Pattern.compile("(?m)^([ \\t]*)([^=:\\n]{1,80})([ \\t]*[=:][ \\t]*)(.+)$");
		Matcher matcher = pattern.matcher(text);
		StringBuffer output = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(2).trim();
			String value = matcher.group(4);
			if (policy.isProtectedKey(key)) {
				SliceExpressionFactory slice = policy.sliceExpressionFor(key);
				String redactedValue = slice == null ? policy.mask() : slice.mask(value, policy.mask());
				String replacement = matcher.group(1) + matcher.group(2) + matcher.group(3) + redactedValue;
				matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
			} else {
				matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(output);
		return output.toString();
	}

	private static String redactJsonFieldsByKey(String text, RedactionPolicy policy) {
		Pattern pattern = Pattern.compile("(\\\"([^\\\"]+)\\\"\\s*:\\s*\\\")(.*?)(\\\")");
		Matcher matcher = pattern.matcher(text);
		StringBuffer output = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(2);
			String value = matcher.group(3);
			if (policy.isProtectedKey(key)) {
				SliceExpressionFactory slice = policy.sliceExpressionFor(key);
				String redactedValue = slice == null ? policy.mask() : slice.mask(value, policy.mask());
				String replacement = matcher.group(1) + redactedValue + matcher.group(4);
				matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
			} else {
				matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
			}
		}
		matcher.appendTail(output);
		return output.toString();
	}

	private static String redactJsonPaths(String text, RedactionPolicy policy) {
		if (!policy.hasProtectedPaths()) {
			return text;
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return text;
		}
		String prefix = text.substring(0, start);
		String json = text.substring(start, end + 1);
		String suffix = text.substring(end + 1);
		try {
			JsonElement element = JsonParser.parseString(json);
			if (!element.isJsonObject()) {
				return text;
			}
			redactJsonPath(element, "", policy);
			return prefix + GSON.toJson(element) + suffix;
		} catch (RuntimeException e) {
			return text;
		}
	}

	private static void redactJsonPath(JsonElement element, String path, RedactionPolicy policy) {
		if (element == null || !element.isJsonObject()) {
			return;
		}
		JsonObject object = element.getAsJsonObject();
		for (String key : object.keySet()) {
			String childPath = path + "/" + key;
			JsonElement child = object.get(key);
			if (policy.isProtectedPath(childPath)) {
				SliceExpressionFactory slice = policy.pathSliceExpressionFor(childPath);
				if (slice != null && child != null && child.isJsonPrimitive()) {
					String value = child.getAsString();
					object.add(key, new JsonPrimitive(slice.mask(value, policy.mask())));
				} else {
					object.add(key, new JsonPrimitive(policy.mask()));
				}
				continue;
			}
			if (child != null && child.isJsonObject()) {
				redactJsonPath(child, childPath, policy);
			}
			if (child != null && child.isJsonArray()) {
				redactJsonArrayPath(child.getAsJsonArray(), childPath, policy);
			}
		}
	}

	private static void redactJsonArrayPath(JsonArray array, String path, RedactionPolicy policy) {
		for (int i = 0; i < array.size(); i++) {
			JsonElement child = array.get(i);
			String childPath = path + "/" + i;
			if (policy.isProtectedPath(childPath)) {
				SliceExpressionFactory slice = policy.pathSliceExpressionFor(childPath);
				if (slice != null && child != null && child.isJsonPrimitive()) {
					array.set(i, new JsonPrimitive(slice.mask(child.getAsString(), policy.mask())));
				} else {
					array.set(i, new JsonPrimitive(policy.mask()));
				}
				continue;
			}
			if (child != null && child.isJsonObject()) {
				redactJsonPath(child, childPath, policy);
			}
			if (child != null && child.isJsonArray()) {
				redactJsonArrayPath(child.getAsJsonArray(), childPath, policy);
			}
		}
	}
}
