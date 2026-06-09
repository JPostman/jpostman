package io.jpostman.secure;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defines which keys, fields, and JSON paths should be redacted.
 */
public final class RedactionPolicy {

	static final String DEFAULT_MASK = "********";
	static final String REGEX_PREFIX = "regex:";

	private final Set<String> protectedKeys;
	private final Map<String, SliceExpressionFactory> sliceExpressions;
	private final Set<String> protectedPaths;
	private final Map<String, SliceExpressionFactory> pathSliceExpressions;
	private final Set<Pattern> protectedKeyPatterns;
	private final Set<Pattern> protectedPathPatterns;
	private final String mask;
	private final SliceExpressionFactory sliceExpressionFactory;
	private final Set<String> headers = new LinkedHashSet<>();

	private RedactionPolicy(Set<String> protectedKeys, Map<String, SliceExpressionFactory> sliceExpressions,
			Set<String> protectedPaths, Map<String, SliceExpressionFactory> pathSliceExpressions,
			Set<Pattern> protectedKeyPatterns, Set<Pattern> protectedPathPatterns, String mask,
			SliceExpressionFactory sliceExpressionFactory) {
		this.protectedKeys = protectedKeys;
		this.sliceExpressions = sliceExpressions;
		this.protectedPaths = protectedPaths;
		this.pathSliceExpressions = pathSliceExpressions;
		this.protectedKeyPatterns = protectedKeyPatterns;
		this.protectedPathPatterns = protectedPathPatterns;
		this.mask = mask;
		this.sliceExpressionFactory = sliceExpressionFactory;
	}

	/**
	 * Returns the default redaction policy.
	 *
	 * @return default redaction policy
	 */
	public static RedactionPolicy defaults() {
		return builder().protectKey("authorization", "proxy-authorization", "api-key", "x-api-key", "access-token",
				"refresh-token", "token", "password", "secret", "set-cookie")
				.build()
				.headers("authorization", "proxy-authorization", "cookie", "set-cookie", "api-key", "x-api-key",
						"x-auth-token", "access-token", "refresh-token");
	}

	/**
	 * Adds header names whose values should be fully masked.
	 *
	 * @param names header names
	 * @return this policy
	 */
	public RedactionPolicy headers(String... names) {
		RedactionPolicy result = addRules();

		if (names != null) {
			for (String name : names) {
				if (name != null && !name.trim().isEmpty()) {
					result.headers.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
		}

		return result;
	}

	/**
	 * Checks whether a header value should be fully masked.
	 *
	 * @param name header name
	 * @return true if the header is protected
	 */
	public boolean isHeaderProtected(String name) {
		return name != null && headers.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Creates a redaction policy builder.
	 *
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the mask used for protected values.
	 *
	 * @return mask text
	 */
	public String mask() {
		return mask;
	}

	/**
	 * Checks whether a key is protected.
	 *
	 * @param key key to check
	 * @return true if the key should be redacted
	 */
	public boolean isProtectedKey(String key) {
		if (protectedKeys.contains(normalize(key)) || protectedKeys.contains(key)) {
			return true;
		}

		for (Pattern pattern : protectedKeyPatterns) {
			if (pattern.matcher(key == null ? "" : key).matches()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns the protected keys configured by this policy.
	 *
	 * @return protected keys
	 */
	public Set<String> protectedKeys() {
		return new LinkedHashSet<>(protectedKeys);
	}

	/**
	 * Returns a slice expression for a protected key.
	 *
	 * @param key field key
	 * @return slice expression, or {@code null} for full redaction
	 */
	SliceExpressionFactory sliceExpressionFor(String key) {
		SliceExpressionFactory expression = sliceExpressions.get(normalize(key));
		if (expression == null) {
			expression = sliceExpressions.get(key);
		}
		return expression;
	}

	/**
	 * Checks whether this policy contains JSON path rules.
	 *
	 * @return true if path rules are configured
	 */
	boolean hasProtectedPaths() {
		return !protectedPaths.isEmpty() || !protectedPathPatterns.isEmpty();
	}

	/**
	 * Checks whether a JSON path is protected.
	 *
	 * @param path JSON path, for example {@code /key2/subkey}
	 * @return true if the path should be redacted
	 */
	boolean isProtectedPath(String path) {
		String normalized = normalizePath(path);

		if (protectedPaths.contains(normalized)) {
			return true;
		}

		for (String protectedPath : protectedPaths) {
			if (pathMatches(protectedPath, normalized)) {
				return true;
			}
		}

		for (Pattern pattern : protectedPathPatterns) {
			if (pattern.matcher(normalized).matches()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns a slice expression for a protected JSON path.
	 *
	 * @param path JSON path
	 * @return slice expression, or {@code null} for full redaction
	 */
	SliceExpressionFactory pathSliceExpressionFor(String path) {
		String normalized = normalizePath(path);
		SliceExpressionFactory expression = pathSliceExpressions.get(normalized);

		if (expression != null) {
			return expression;
		}

		for (Map.Entry<String, SliceExpressionFactory> entry : pathSliceExpressions.entrySet()) {
			if (pathMatches(entry.getKey(), normalized)) {
				return entry.getValue();
			}
		}

		return null;
	}

	/**
	 * Returns a new policy with additional redaction rules.
	 *
	 * @param rules field or JSON path rules
	 * @return new redaction policy
	 */
	public RedactionPolicy addRules(String... rules) {
		Builder builder = toBuilder();
		if (rules != null) {
			for (String rule : rules) {
				builder.protectRule(rule);
			}
		}
		RedactionPolicy result = builder.build();
		result.headers.addAll(headers);
		return result;
	}

	/**
	 * Returns a new policy without the supplied redaction rules.
	 *
	 * @param rules field or JSON path rules to remove
	 * @return new redaction policy
	 */
	public RedactionPolicy removeRules(String... rules) {
		Builder builder = toBuilder();

		if (rules != null) {
			for (String rule : rules) {
				if (rule == null || rule.isBlank()) {
					continue;
				}

				String trimmed = rule.trim();

				if (trimmed.startsWith("regex:")) {
					builder.removeRegexRule(trimmed.substring("regex:".length()).trim());
					continue;
				}

				String key = keyOf(trimmed);

				builder.protectedKeys.remove(normalize(key));
				builder.protectedKeys.remove(key);
				builder.sliceExpressions.remove(normalize(key));
				builder.sliceExpressions.remove(key);

				builder.protectedPaths.remove(normalizePath(key));
				builder.pathSliceExpressions.remove(normalizePath(key));
			}
		}

		RedactionPolicy result = builder.build();
		result.headers.addAll(headers);
		return result;
	}

	private Builder toBuilder() {
		Builder builder = new Builder();
		builder.protectedKeys.addAll(protectedKeys);
		builder.sliceExpressions.putAll(sliceExpressions);
		builder.protectedPaths.addAll(protectedPaths);
		builder.pathSliceExpressions.putAll(pathSliceExpressions);
		builder.protectedKeyPatterns.addAll(protectedKeyPatterns);
		builder.protectedPathPatterns.addAll(protectedPathPatterns);
		builder.mask = mask;
		builder.sliceExpressionFactory = sliceExpressionFactory;
		return builder;
	}

	private static String keyOf(String rule) {
		String trimmed = rule.trim();
		int bracketIndex = trimmed.indexOf('[');
		if (bracketIndex < 0) {
			return trimmed;
		}
		return trimmed.substring(0, bracketIndex).trim();
	}

	static String normalize(String key) {
		if (key == null) {
			return "";
		}
		return key.trim().toLowerCase().replace("-", "").replace("_", "").replace(" ", "");
	}

	static String normalizePath(String path) {
		return JsonPathRules.normalizePath(path);
	}

	private static boolean pathMatches(String pattern, String path) {
		return JsonPathRules.matches(pattern, path);
	}


	public static final class Builder {
		private final Set<String> protectedKeys = new LinkedHashSet<>();
		private final Map<String, SliceExpressionFactory> sliceExpressions = new LinkedHashMap<>();
		private final Set<String> protectedPaths = new LinkedHashSet<>();
		private final Map<String, SliceExpressionFactory> pathSliceExpressions = new LinkedHashMap<>();
		private final Set<Pattern> protectedKeyPatterns = new LinkedHashSet<>();
		private final Set<Pattern> protectedPathPatterns = new LinkedHashSet<>();
		private String mask = DEFAULT_MASK;
		private SliceExpressionFactory sliceExpressionFactory = new DefaultSliceExpressionFactory();

		/**
		 * Adds protected keys using normalized matching.
		 *
		 * @param keys keys to protect
		 * @return this builder
		 */
		public Builder protectKey(String... keys) {
			return protectKey(true, keys);
		}

		/**
		 * Adds protected keys.
		 *
		 * @param normalize whether keys should be normalized before storing
		 * @param keys      keys to protect
		 * @return this builder
		 */
		public Builder protectKey(boolean normalize, String... keys) {
			if (keys == null) {
				return this;
			}
			for (String key : keys) {
				if (key == null || key.isBlank()) {
					continue;
				}
				protectedKeys.add(normalize ? normalize(key) : key);
			}
			return this;
		}

		/**
		 * Adds a protected field or JSON path rule.
		 *
		 * @param rule protected rule
		 * @return this builder
		 */
		public Builder protectRule(String rule) {
			return protectRule(true, rule);
		}

		/**
		 * Adds a protected field or JSON path rule.
		 *
		 * @param normalize whether field keys should be normalized before storing
		 * @param rule      protected rule
		 * @return this builder
		 */
		public Builder protectRule(boolean normalize, String rule) {
			if (rule == null || rule.isBlank()) {
				return this;
			}

			String trimmed = rule.trim();

			if (trimmed.startsWith(REGEX_PREFIX)) {
				return protectRegexRule(trimmed.substring(REGEX_PREFIX.length()).trim());
			}

			if (trimmed.startsWith("/")) {
				return protectPathRule(JsonPathRules.normalizeRule(trimmed));
			}

			int bracketIndex = trimmed.indexOf('[');
			if (bracketIndex < 0) {
				protectKey(normalize, trimmed);
				return this;
			}

			String key = trimmed.substring(0, bracketIndex).trim();
			String slice = trimmed.substring(bracketIndex).trim();
			if (key.isEmpty()) {
				throw new IllegalArgumentException("rule key cannot be blank: " + rule);
			}

			protectKey(normalize, key);
			sliceExpressions.put(normalize ? normalize(key) : key, parsedSlice(slice));
			return this;
		}

		private Builder protectRegexRule(String pattern) {
			if (pattern == null || pattern.isBlank()) {
				throw new IllegalArgumentException("regex rule cannot be blank");
			}

			Pattern compiled = Pattern.compile(pattern);

			if (pattern.startsWith("/")) {
				protectedPathPatterns.add(compiled);
			} else {
				protectedKeyPatterns.add(compiled);
			}

			return this;
		}

		private void removeRegexRule(String pattern) {
			if (pattern == null || pattern.isBlank()) {
				return;
			}

			if (pattern.startsWith("/")) {
				protectedPathPatterns.removeIf(item -> item.pattern().equals(pattern));
			} else {
				protectedKeyPatterns.removeIf(item -> item.pattern().equals(pattern));
			}
		}

		private Builder protectPathRule(String rule) {
			int bracketIndex = rule.indexOf('[');
			if (bracketIndex < 0) {
				protectedPaths.add(normalizePath(rule));
				return this;
			}

			String path = rule.substring(0, bracketIndex).trim();
			String slice = rule.substring(bracketIndex).trim();
			if (path.isEmpty()) {
				throw new IllegalArgumentException("path rule cannot be blank: " + rule);
			}

			protectedPaths.add(normalizePath(path));
			pathSliceExpressions.put(normalizePath(path), parsedSlice(slice));
			return this;
		}

		private SliceExpressionFactory parsedSlice(String slice) {
			SliceExpressionFactory parsed = sliceExpressionFactory.parse(slice);
			return new SliceExpressionFactory() {
				@Override
				public SliceExpressionFactory parse(String expression) {
					return sliceExpressionFactory.parse(expression);
				}

				@Override
				public String mask(String source, String mask) {
					return sliceExpressionFactory.mask(parsed, source, mask);
				}
			};
		}

		/**
		 * Sets a custom slice expression factory.
		 *
		 * @param sliceExpressionFactory custom factory
		 * @return this builder
		 */
		public Builder sliceExpressionFactory(SliceExpressionFactory sliceExpressionFactory) {
			if (sliceExpressionFactory != null) {
				this.sliceExpressionFactory = sliceExpressionFactory;
			}
			return this;
		}

		/**
		 * Sets the mask text.
		 *
		 * @param mask mask text
		 * @return this builder
		 */
		public Builder mask(String mask) {
			if (mask != null && !mask.isBlank()) {
				this.mask = mask;
			}
			return this;
		}

		/**
		 * Builds the redaction policy.
		 *
		 * @return redaction policy
		 */
		public RedactionPolicy build() {
			return new RedactionPolicy(new LinkedHashSet<>(protectedKeys), new LinkedHashMap<>(sliceExpressions),
					new LinkedHashSet<>(protectedPaths), new LinkedHashMap<>(pathSliceExpressions),
					new LinkedHashSet<>(protectedKeyPatterns), new LinkedHashSet<>(protectedPathPatterns), mask,
					sliceExpressionFactory);
		}
	}
}
