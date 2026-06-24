package io.jpostman;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.jpostman.Params.Entry;

/**
 * Represents a Postman environment and its key/value variables.
 *
 * <p>
 * Disabled variables are preserved internally, but they are excluded from
 * {@link #getParams()} so they do not participate in {@code {{name}}}
 * substitution.
 * </p>
 */
public class Environment {

	private static final Logger log = LoggerFactory.getLogger(Environment.class);

	private final String name;
	private final Map<String, Entry> params = new LinkedHashMap<>();

	/**
	 * Creates an environment container with the supplied display name.
	 *
	 * @param name environment name
	 */
	public Environment(String name) {
		this.name = name;
	}

	/** @return environment name. */
	public String getName() {
		return name;
	}

	/** @return enabled environment variables in insertion order. */
	public Map<String, String> getParams() {
		Map<String, String> enabled = new LinkedHashMap<>();
		params.forEach((key, info) -> {
			enabled.put(key, info.value);
		});
		return enabled;
	}

	/**
	 * Looks up parameter metadata by key.
	 *
	 * @param key parameter name
	 * @return matching {@link Entry}, or {@code null} when absent
	 */
	public Entry entry(String key) {
		return params.get(key);
	}

	/**
	 * Removes parameter metadata by key.
	 *
	 * @param key parameter name
	 * @return removed {@link Entry}, or {@code null} when absent
	 */
	public Entry removeKey(String key) {
		return params.remove(key);
	}

	/**
	 * Returns the stored value for a key, even when the entry is disabled.
	 *
	 * @param key parameter name
	 * @return stored value, or {@code null} when absent
	 */
	public String raw(String key) {
		Entry entry = entry(key);
		return entry != null ? entry.value : null;
	}

	/**
	 * Returns the enabled value for a key.
	 *
	 * @param key parameter name
	 * @return value when present and enabled; otherwise {@code null}
	 */
	public String get(String key) {
		Entry entry = entry(key);
		return entry != null && entry.enabled ? entry.value : null;
	}

	/**
	 * Returns a fluent builder pre-populated with this environment's variables.
	 *
	 * <p>
	 * {@code add(...)} creates enabled variables and {@code set(...)} updates
	 * existing variables while preserving their enabled/disabled state.
	 * </p>
	 *
	 * @return environment builder
	 */
	public Params<Environment> builder() {
		String envName = this.name;
		Map<String, Entry> params = new LinkedHashMap<>(this.params);
		Params.Builder<Environment> buildFn = () -> {
			@NonNull
			Environment env = new Environment(envName);
			params.forEach(env.params::put);
			return env;
		};
		return new Params<Environment>(
				// ADD
				(String key, Object value) -> params.put(key, new Entry(String.valueOf(value), true)),
				// SET
				(String key, Object value) -> {
					Entry info = params.get(key);
					if (info == null) {
						throw new IllegalArgumentException("Environment key not found: '" + key + "'");
					}
					params.put(key, new Entry(String.valueOf(value), info.enabled));
				},
				// RESOLVE
				vars -> vars.forEach((key, value) -> params.put(key, new Entry(String.valueOf(value), true))),
				// BUILD
				buildFn);
	}

	/**
	 * Loads a Postman environment from a file path and closes the file internally.
	 *
	 * @param filePath absolute or relative path to a
	 *                 {@code *.postman_environment.json} file
	 * @return parsed environment
	 * @throws IOException if the file cannot be read or parsed
	 */
	public static Environment load(String filePath) throws IOException {
		return load(new FileInputStream(filePath));
	}

	/**
	 * Loads a Postman environment from an input stream. The stream is closed by
	 * this method.
	 *
	 * @param is input stream positioned at the start of the environment JSON
	 * @return parsed environment
	 * @throws IOException if the stream cannot be read or parsed
	 */
	public static Environment load(InputStream is) throws IOException {
		try (Reader reader = new InputStreamReader(is)) {
			return load(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	/**
	 * Load an environment from an already parsed JSON object. Disabled entries and
	 * entries without a key are preserved/skipped respectively. Missing values are
	 * stored as empty strings.
	 *
	 * @param root environment root JSON object
	 * @return populated environment
	 * @throws IOException kept for API symmetry with file and stream loaders
	 */
	public static Environment load(JsonObject root) throws IOException {
		String envName = root.has("name") && !root.get("name").isJsonNull() ? root.get("name").getAsString()
				: "Unknown Environment";
		Environment env = new Environment(envName);

		if (root.has("values") && root.get("values").isJsonArray()) {
			for (JsonElement el : root.getAsJsonArray("values")) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject var = el.getAsJsonObject();
				if (var.has("key") && !var.get("key").isJsonNull()) {
					String key = var.get("key").getAsString();
					String value = var.has("value") && !var.get("value").isJsonNull() ? var.get("value").getAsString()
							: "";
					boolean enabled = !var.has("enabled") || var.get("enabled").getAsBoolean();
					env.params.put(key, new Entry(value, enabled));
				}
			}
		}
		return env;
	}

	/**
	 * Resolves the supplied unresolved token map using this environment's enabled
	 * parameters.
	 * <p>
	 * For every key already present in {@code result}, this method checks whether
	 * this environment contains a matching enabled parameter. When a match exists,
	 * the empty/default value in {@code result} is replaced with the environment
	 * value. Missing or disabled parameters are left unchanged.
	 *
	 * @param result unresolved token map, usually produced by
	 *               {@code Request.params()}; keys are token names and values are
	 *               usually empty strings
	 * @return the same token map instance with matching environment values applied
	 */
	public Map<String, String> resolve(Map<String, String> result) {
		if (result == null) {
			return Map.of();
		}
		for (String key : result.keySet()) {
			if (params.containsKey(key)) {
				result.put(key, params.get(key).value);
			}
		}
		return result;
	}

	/** Logs the environment name and enabled variables. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including enabled variables. */
	public String toDebugString() {
		Map<String, String> enabled = getParams();
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("=== Environment: %s (%d variable%s) ===", name, enabled.size(),
				enabled.size() == 1 ? "" : "s"));
		if (enabled.isEmpty()) {
			sb.append("\n  (no variables)");
		} else {
			sb.append('\n' + toString());
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return getParams().entrySet().stream().map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
				.collect(Collectors.joining());
	}
}
