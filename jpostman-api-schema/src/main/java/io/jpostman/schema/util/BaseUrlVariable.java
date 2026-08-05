package io.jpostman.schema.util;

import java.util.LinkedHashMap;
import java.util.Map;

import io.jpostman.schema.model.ApiSpec;

/**
 * Resolves the environment variable used as the base URL without changing the
 * variable's original case.
 */
public final class BaseUrlVariable {
	public static final String DEFAULT_KEY = "BASE_URL";

	/**
	 * Creates a new BaseUrlVariable instance.
	 */
	private BaseUrlVariable() {
	}

	/**
	 * Returns the base URL environment key. A placeholder-based base URL such as
	 * {@code {{base_url}}} or {@code https://{{base_url}}} preserves
	 * {@code base_url}; concrete base URLs use {@code BASE_URL}.
	 */
	public static String key(ApiSpec spec) {
		return key(spec == null ? null : spec.getBaseUrl());
	}

	/**
	 * Returns the base URL environment key for the supplied base URL value.
	 */
	public static String key(String baseUrl) {
		String placeholderKey = placeholderKey(baseUrl);
		return placeholderKey == null ? DEFAULT_KEY : placeholderKey;
	}

	/**
	 * Returns the exact Postman token used for the base URL.
	 */
	public static String token(ApiSpec spec) {
		return "{{" + key(spec) + "}}";
	}

	/**
	 * Returns whether the base URL is derived from an unresolved environment
	 * placeholder rather than a concrete URL.
	 */
	public static boolean isPlaceholderBased(ApiSpec spec) {
		return isPlaceholderBased(spec == null ? null : spec.getBaseUrl());
	}

	/**
	 * Returns whether the supplied base URL contains an unresolved placeholder.
	 */
	public static boolean isPlaceholderBased(String baseUrl) {
		return placeholderKey(baseUrl) != null;
	}

	/**
	 * Returns a placeholder key only when the placeholder itself represents the URL
	 * host. Path placeholders such as {@code https://api.test/{{tenant}}} are not
	 * treated as the base URL variable.
	 */
	private static String placeholderKey(String baseUrl) {
		if (baseUrl == null) {
			return null;
		}
		String candidate = baseUrl.trim();
		int schemeIndex = candidate.indexOf("://");
		if (schemeIndex >= 0) {
			candidate = candidate.substring(schemeIndex + 3);
		}
		while (candidate.endsWith("/")) {
			candidate = candidate.substring(0, candidate.length() - 1);
		}
		return EnvVarExtractor.singleKey(candidate);
	}

	/**
	 * Records the base URL in the environment map without replacing a concrete
	 * value with an unresolved placeholder-derived value.
	 */
	public static void record(ApiSpec spec) {
		if (spec == null || spec.getEnvs() == null || spec.getBaseUrl() == null || spec.getBaseUrl().isBlank()) {
			return;
		}

		String key = key(spec);
		if (isPlaceholderBased(spec)) {
			recordPlaceholder(spec, spec.getEnvs(), key);
			return;
		}
		spec.getEnvs().put(key, spec.getBaseUrl());
	}

	/**
	 * Returns a copy of the environment values safe for export. This also repairs
	 * models saved by older versions that stored a placeholder-derived base URL
	 * under the generated {@code BASE_URL} alias.
	 */
	public static Map<String, Object> valuesForExport(ApiSpec spec) {
		Map<String, Object> values = new LinkedHashMap<>();
		if (spec == null || spec.getEnvs() == null) {
			return values;
		}
		values.putAll(spec.getEnvs());
		if (isPlaceholderBased(spec)) {
			recordPlaceholder(spec, values, key(spec));
		}
		return values;
	}

	/**
	 * Adds the real placeholder key while removing only the invalid alias/value
	 * generated from the same unresolved base URL. Unrelated concrete variables are
	 * preserved.
	 */
	private static void recordPlaceholder(ApiSpec spec, Map<String, Object> values, String key) {
		Object canonicalValue = values.get(DEFAULT_KEY);
		if (!DEFAULT_KEY.equals(key) && sameValue(canonicalValue, spec.getBaseUrl())) {
			values.remove(DEFAULT_KEY);
		}

		Object currentValue = values.get(key);
		if (sameValue(currentValue, spec.getBaseUrl())) {
			values.put(key, "");
		} else {
			values.putIfAbsent(key, "");
		}
	}

	/**
	 * Compares an environment value with a generated base URL value.
	 */
	private static boolean sameValue(Object value, String expected) {
		return value != null && expected != null && expected.equals(String.valueOf(value));
	}
}
