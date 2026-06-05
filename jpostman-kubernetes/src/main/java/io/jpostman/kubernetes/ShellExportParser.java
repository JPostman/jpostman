package io.jpostman.kubernetes;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses simple shell export lines into key/value pairs.
 */
final class ShellExportParser {

	private ShellExportParser() {
	}

	/**
	 * Parses simple {@code export KEY=VALUE} shell lines.
	 *
	 * @param script shell text
	 * @return parsed key/value pairs
	 */
	public static Map<String, String> parse(String script) {
		Map<String, String> values = new HashMap<>();

		if (script == null || script.isBlank()) {
			return values;
		}

		String[] lines = script.split("\\R");

		for (String line : lines) {
			String trimmedLine = line.trim();

			if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
				continue;
			}

			if (!trimmedLine.startsWith("export ")) {
				continue;
			}

			String assignment = trimmedLine.substring("export ".length()).trim();
			int equalsIndex = assignment.indexOf('=');

			if (equalsIndex <= 0) {
				continue;
			}

			String key = assignment.substring(0, equalsIndex).trim();
			String value = assignment.substring(equalsIndex + 1).trim();

			values.put(key, unquote(value));
		}

		return values;
	}

	private static String unquote(String value) {
		String trimmedValue = value.trim();

		if (trimmedValue.length() >= 2) {
			if ((trimmedValue.startsWith("\"") && trimmedValue.endsWith("\""))
					|| (trimmedValue.startsWith("'") && trimmedValue.endsWith("'"))) {
				return trimmedValue.substring(1, trimmedValue.length() - 1);
			}
		}

		return trimmedValue;
	}
}
