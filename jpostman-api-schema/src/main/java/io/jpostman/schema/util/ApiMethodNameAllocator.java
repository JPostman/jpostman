package io.jpostman.schema.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiSpec;

/**
 * Converts operation method names into valid, unique Java-style identifiers.
 */
public final class ApiMethodNameAllocator {
	private ApiMethodNameAllocator() {
	}

	/**
	 * Normalizes and de-duplicates method names across the entire specification.
	 */
	public static void apply(ApiSpec spec) {
		if (spec == null) {
			return;
		}
		Set<String> used = new HashSet<>();
		for (ApiOperation operation : spec.getOperations()) {
			allocate(operation, used);
		}
		for (ApiFolder folder : spec.getFolders()) {
			allocate(folder, used);
		}
	}

	private static void allocate(ApiFolder folder, Set<String> used) {
		if (folder == null) {
			return;
		}
		for (ApiOperation operation : folder.getOperations()) {
			allocate(operation, used);
		}
		for (ApiFolder child : folder.getFolders()) {
			allocate(child, used);
		}
	}

	private static void allocate(ApiOperation operation, Set<String> used) {
		if (operation == null) {
			return;
		}
		String source = firstNonBlank(operation.getMethodName(), operation.getName(), "request");
		String base = toJavaIdentifier(source);
		String candidate = base;
		int suffix = 2;
		while (!used.add(candidate)) {
			candidate = base + suffix++;
		}
		operation.setMethodName(candidate);
	}

	/**
	 * Converts text such as "Get all products" into "getAllProducts".
	 */
	static String toJavaIdentifier(String value) {
		String safe = value == null ? "request" : value.trim();
		String[] parts = safe.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
				.replaceAll("[^A-Za-z0-9_$]+", " ").trim().split("\\s+");
		StringBuilder result = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (result.length() == 0) {
				result.append(Character.toLowerCase(part.charAt(0))).append(part.substring(1));
			} else {
				result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		if (result.length() == 0) {
			result.append("request");
		}
		if (!Character.isJavaIdentifierStart(result.charAt(0))) {
			result.insert(0, "request");
		}
		for (int i = 1; i < result.length(); i++) {
			if (!Character.isJavaIdentifierPart(result.charAt(i))) {
				result.setCharAt(i, '_');
			}
		}
		String candidate = result.toString();
		if (JAVA_KEYWORDS.contains(candidate.toLowerCase(Locale.ROOT))) {
			return candidate + "Request";
		}
		return candidate;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte",
			"case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else",
			"enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import",
			"instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected",
			"public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
			"throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null");
}
