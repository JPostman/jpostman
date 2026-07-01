package io.jpostman.schema.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Postman-style {{KEY}} placeholders from strings.
 */
public final class EnvVarExtractor {
	private static final Pattern ENV_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

	/**
	 * Creates a new EnvVarExtractor instance.
	 */
	private EnvVarExtractor() {
	}

	/**
	 * Collects all {{KEY}} placeholders found in the supplied value.
	 */
	public static void collect(String value, Map<String, Object> envs) {
		if (value == null || value.isBlank() || envs == null) {
			return;
		}

		Matcher matcher = ENV_PATTERN.matcher(value);
		while (matcher.find()) {
			envs.putIfAbsent(matcher.group(1), "");
		}
	}

	/**
	 * Returns whether the supplied value contains a {{KEY}} placeholder.
	 */
	public static boolean containsKey(String value) {
		if (value == null) {
			return false;
		}
		return ENV_PATTERN.matcher(value).find();
	}

	/**
	 * Returns the placeholder key when the whole value is exactly one {{KEY}}
	 * token.
	 */
	public static String singleKey(String value) {
		if (value == null) {
			return null;
		}
		Matcher matcher = ENV_PATTERN.matcher(value.trim());
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return null;
	}
}
