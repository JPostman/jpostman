package io.jpostman;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.google.gson.Gson;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Generic fluent builder for Postman parameter maps. Wraps domain-specific
 * add/set/resolve/build logic via lambdas so that {@link Header}, {@link Body},
 * {@link Auth}, {@link Url}, and {@link Environment} can share one builder
 * class instead of duplicating the same fluent API.
 *
 * @param <T> the type produced by {@link #end()}
 */
public class Params<T> {

	private static final Gson GSON = new Gson();
	private static final Handlebars HANDLEBARS = new Handlebars().with(EscapingStrategy.NOOP);
	public static final Pattern HANDLEBARS_TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*\\}\\}");
	private static final ThreadLocal<Boolean> PARTIAL_RESOLVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	@FunctionalInterface
	public interface Builder<T> {
		T build();
	}

	/** Adds or overwrites a request-part value. */
	private final BiConsumer<String, Object> onPut;

	/** Updates an existing key and lets the target object throw when missing. */
	private final BiConsumer<String, Object> onSet;

	/** Resolves template placeholders using the supplied variables. */
	private final Consumer<Map<String, ?>> onResolve;

	/** Builds the final target object from the builder state. */
	private final Builder<T> onBuild;

	Params(BiConsumer<String, Object> onPut, BiConsumer<String, Object> onSet, Consumer<Map<String, ?>> onResolve,
			Builder<T> onBuild) {
		this.onPut = onPut;
		this.onSet = onSet;
		this.onResolve = onResolve;
		this.onBuild = onBuild;
	}

	/** Adds or overwrites the key unconditionally. */
	public Params<T> add(String key, Object value) {
		onPut.accept(key, value);
		return this;
	}

	/**
	 * Updates an existing key.
	 *
	 * @throws IllegalArgumentException if the target object requires the key to
	 *                                  exist and the key is missing
	 */
	public Params<T> set(String key, Object value) {
		onSet.accept(key, value);
		return this;
	}

	/** Substitutes all {@code {{key}}} tokens using the supplied variable map. */
	public Params<T> resolve(Map<String, ?> vars) {
		if (vars != null) {
			onResolve.accept(vars);
		}
		return this;
	}

	/** Produces the final object. */
	public T end() {
		return onBuild.build();
	}

	/**
	 * Resolves only variables present in the local parameter map, then produces the
	 * final object.
	 *
	 * <p>
	 * Values resolved here have priority over later request-level resolution
	 * because replaced tokens are no longer available for {@code build(env)}.
	 * Variables not present in {@code vars} are intentionally left unchanged so
	 * {@code build(env)} can resolve them later.
	 * </p>
	 *
	 * @param vars local variables used only for this builder
	 * @return final object
	 */
	public T end(Map<String, ?> vars) {
		if (vars != null) {
			PARTIAL_RESOLVE.set(Boolean.TRUE);
			try {
				onResolve.accept(vars);
			} finally {
				PARTIAL_RESOLVE.remove();
			}
		}
		return end();
	}

	/**
	 * Creates an ordered variable map from alternating key/value pairs.
	 *
	 * @param values alternating key/value pairs
	 * @return ordered variable map
	 */
	public static Map<String, Object> asMap(Object... values) {
		return toMap(false, values);
	}

	/**
	 * Creates an ordered variable map and JSON-stringifies String values.
	 *
	 * @param values alternating key/value pairs
	 * @return ordered JSON-ready variable map
	 */
	public static Map<String, Object> asJson(Object... values) {
		return toMap(true, values);
	}

	/**
	 * Converts alternating key/value pairs to an ordered map.
	 *
	 * @param json   whether to JSON-stringify String values
	 * @param values alternating key/value pairs
	 * @return ordered variable map
	 */
	private static Map<String, Object> toMap(boolean json, Object... values) {
		if (values == null || values.length == 0) {
			return Map.of();
		}

		if (values.length % 2 != 0) {
			throw new IllegalArgumentException("Values must be provided as key/value pairs.");
		}

		Map<String, Object> result = new LinkedHashMap<>();

		for (int i = 0; i < values.length; i += 2) {
			Object rawKey = values[i];
			Object rawValue = values[i + 1];

			if (!(rawKey instanceof String)) {
				throw new IllegalArgumentException("Key must be String. Found: " + rawKey);
			}

			result.put((String) rawKey, convertValue(rawValue, json));
		}

		return result;
	}

	/**
	 * Creates a mutable ordered map by merging the supplied maps.
	 *
	 * <p>
	 * When the same key exists in multiple maps, the later map wins.
	 * </p>
	 *
	 * @param maps source maps; null maps are ignored
	 * @return mutable merged map
	 */
	@SafeVarargs
	public static Map<String, Object> copy(Map<String, ?>... maps) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map<String, ?> map : maps) {
			if (map != null) {
				result.putAll(map);
			}
		}
		return result;
	}

	/**
	 * Creates a mutable list from the supplied values.
	 *
	 * @param values list values
	 * @param <T>    value type
	 * @return mutable list containing the supplied values
	 */
	@SafeVarargs
	public static <T> List<T> asList(T... values) {
		List<T> result = new ArrayList<>();
		for (T value : values) {
			result.add(value);
		}
		return result;
	}

	/**
	 * Resolves variables using local key/value pairs, then produces the final
	 * object.
	 *
	 * @param values alternating key/value pairs
	 * @return final object
	 */
	public T map(Object... values) {
		return end(toMap(false, values));
	}

	/**
	 * Resolves variables using local key/value pairs where String values are
	 * JSON-stringified, then produces the final object.
	 *
	 * @param values alternating key/value pairs
	 * @return final object
	 */
	public T json(Object... values) {
		return end(toMap(true, values));
	}

	private static Object convertValue(Object value, boolean stringifyStrings) {
		return stringifyStrings && value instanceof String ? GSON.toJson(value) : value;
	}

	/**
	 * Adds all Handlebars-style {@code {{token}}} placeholders found in a text
	 * value to the supplied target map.
	 *
	 * <p>
	 * Every discovered token is inserted with an empty string value and existing
	 * entries are preserved. Passing {@code null} for either argument is treated as
	 * a no-op.
	 * </p>
	 *
	 * @param target destination token map
	 * @param value  text to scan for tokens
	 * @return {@code target}, or an empty map when {@code target} is {@code null}
	 */
	public static Map<String, String> addTokens(Map<String, String> target, String value) {
		if (target == null || value == null) {
			return Map.of();
		}
		Matcher matcher = HANDLEBARS_TOKEN.matcher(value);
		while (matcher.find()) {
			target.putIfAbsent(matcher.group(1), "");
		}
		return target;
	}

	/**
	 * Adds all Handlebars-style {@code {{token}}} placeholders found in a map's
	 * keys and values to the supplied target map.
	 *
	 * <p>
	 * This is useful for request parts where both parameter names and parameter
	 * values may contain unresolved tokens. Existing entries in {@code target} are
	 * not overwritten.
	 * </p>
	 *
	 * @param target destination token map
	 * @param values map whose keys and values should be scanned
	 * @return {@code target}, or an empty map when {@code target} is {@code null}
	 */
	public static Map<String, String> addTokens(Map<String, String> target, Map<String, String> values) {
		if (target == null || values == null) {
			return Map.of();
		}
		values.forEach((key, value) -> {
			addTokens(target, key);
			addTokens(target, value);
		});
		return target;
	}

	/**
	 * Replaces all {@code {{key}}} tokens in {@code value} with entries from
	 * {@code vars} using Handlebars. Unknown tokens use normal Handlebars behavior
	 * and render as empty strings.
	 *
	 * @param value source text; may be {@code null}
	 * @param vars  variable map; may be {@code null}
	 * @return substituted text, or {@code null} when {@code value} is null
	 */
	public static String substituteVars(String value, Map<String, ?> vars) {
		if (value == null || vars == null) {
			return value;
		}
		if (Boolean.TRUE.equals(PARTIAL_RESOLVE.get())) {
			return renderProvidedTokensOnly(value, vars);
		}
		return renderHandlebars(value, vars);
	}

	/**
	 * Resolves only tokens that exist in {@code vars}; all other tokens remain in
	 * their original {@code {{token}}} form for later resolution.
	 */
	private static String renderProvidedTokensOnly(String value, Map<String, ?> vars) {
		Matcher matcher = HANDLEBARS_TOKEN.matcher(value);
		StringBuffer resolved = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			if (vars.containsKey(key)) {
				matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(vars.get(key))));
			} else {
				matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group(0)));
			}
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

	/**
	 * Resolves a template using Handlebars, falling back to direct replacement when
	 * the template cannot be compiled.
	 */
	private static String renderHandlebars(String value, Map<String, ?> vars) {
		try {
			Template template = HANDLEBARS.compileInline(value);
			return template.apply(vars);
		} catch (Exception ex) {
			// Keep the old deterministic behavior as a safe fallback if a template
			// contains syntax Handlebars cannot compile.
			String resolved = value;
			for (Map.Entry<String, ?> e : vars.entrySet()) {
				resolved = resolved.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
			}
			return resolved;
		}
	}

	/**
	 * Fills an unresolved token map from the supplied parameter map.
	 *
	 * <p>
	 * Only keys already present in {@code result} are updated. Keys that do not
	 * exist in {@code params} keep their current value, usually an empty string.
	 * </p>
	 *
	 * @param result unresolved token map to update
	 * @param params source parameter values
	 * @return {@code result}, or an empty map when either argument is {@code null}
	 */
	public static Map<String, String> resolve(Map<String, String> result, Map<String, String> params) {
		if (result == null || params == null) {
			return Map.of();
		}
		for (String key : result.keySet()) {
			if (params.containsKey(key)) {
				result.put(key, params.get(key));
			}
		}
		return result;
	}

	/**
	 * Reads a value from a JSON element using a simple dot/bracket path.
	 *
	 * @param root parsed JSON root
	 * @param path simple JSON path (e.g., "user.id", "products[0].title")
	 * @param <T>  expected return type
	 * @return selected value converted to a Java value
	 * @throws IllegalArgumentException if the path is not found or invalid
	 * @throws ClassCastException       if the caller assigns the value to the wrong
	 *                                  type
	 */
	@SuppressWarnings("unchecked")
	public static <T> T path(JsonElement root, String path) {
		JsonElement element = pathElement(root, path);
		if (element == null) {
			throw new IllegalArgumentException("JSON path not found: " + path);
		}
		// Convert JsonElement to Java value (inlined jsonValue logic)
		if (element.isJsonPrimitive()) {
			JsonPrimitive p = element.getAsJsonPrimitive();
			if (p.isBoolean()) {
				return (T) (Boolean) p.getAsBoolean();
			}
			if (p.isNumber()) {
				String s = p.getAsString();
				// Heuristic: integer if no decimal or exponent part
				if (!s.contains(".") && !s.contains("e") && !s.contains("E")) {
					try {
						return (T) Integer.valueOf(s);
					} catch (NumberFormatException e) {
						return (T) Long.valueOf(s);
					}
				}
				return (T) Double.valueOf(s);
			}
			return (T) p.getAsString();
		}
		// For JsonObject or JsonArray, return the element itself
		return (T) element;
	}

	/**
	 * Reads a JSON element from a parsed JSON root using a simple dot/bracket path.
	 *
	 * @param root parsed JSON root
	 * @param path simple JSON path
	 * @return matching JSON element, or {@code null} when the path is not found or
	 *         the selected value is JSON {@code null}
	 */
	public static JsonElement pathElement(JsonElement root, String path) {
		if (root == null || root.isJsonNull())
			return null;
		if (path == null || (path = path.trim()).isEmpty())
			return root;

		JsonElement current = root;
		for (String segment : path.split("\\.")) {
			if (current == null || current.isJsonNull())
				return null;

			int bracketIdx = segment.indexOf('[');
			if (bracketIdx >= 0) {
				// Extract field name before first bracket (if any)
				String fieldName = bracketIdx == 0 ? null : segment.substring(0, bracketIdx);
				if (fieldName != null) {
					if (!current.isJsonObject())
						return null;
					current = current.getAsJsonObject().get(fieldName);
					if (current == null || current.isJsonNull())
						return null;
				}
				// Process all bracket indices in this segment (e.g., "[0][1]")
				int start = bracketIdx;
				while (start >= 0) {
					int end = segment.indexOf(']', start);
					if (end < 0)
						throw new IllegalArgumentException("Invalid path segment: " + segment);
					int index = Integer.parseInt(segment.substring(start + 1, end));
					if (!current.isJsonArray())
						return null;
					JsonArray arr = current.getAsJsonArray();
					if (index < 0 || index >= arr.size())
						return null;
					current = arr.get(index);
					start = segment.indexOf('[', end + 1);
				}
			} else {
				// Simple object field access
				if (!current.isJsonObject())
					return null;
				current = current.getAsJsonObject().get(segment);
			}
		}
		return current == null || current.isJsonNull() ? null : current;
	}

	/**
	 * Stores one Postman parameter value together with its enabled/disabled state.
	 *
	 * <p>
	 * Postman can keep disabled headers, query parameters, and environment
	 * variables in the exported JSON. Keeping this metadata lets the parser
	 * preserve the original collection structure while the public API can still
	 * expose only enabled values for execution and variable substitution.
	 * </p>
	 */
	public static class Entry {

		/** Raw parameter value. Disabled parameters keep their value here too. */
		final String value;

		/** Whether this parameter should participate in execution/resolution output. */
		boolean enabled;

		/**
		 * Creates parameter metadata.
		 *
		 * @param value   parameter value; converted to an empty string when
		 *                {@code null}
		 * @param enabled true when the parameter should be active
		 */
		Entry(String value, boolean enabled) {
			this.value = value;
			this.enabled = enabled;
		}

		/**
		 * Returns the raw parameter value.
		 *
		 * @return parameter value, or an empty string when constructed with
		 *         {@code null}
		 */
		public String getValue() {
			return value;
		}

		/**
		 * Returns whether this parameter is enabled.
		 *
		 * @return {@code true} when enabled
		 */
		public boolean isEnabled() {
			return enabled;
		}

		/**
		 * Enables or disables this parameter.
		 *
		 * @param enabled {@code true} to enable the parameter; {@code false} to disable
		 *                it
		 */
		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		@Override
		public String toString() {
			return String.format("value=%s, enabled=%b", value, enabled);
		}
	}
}
