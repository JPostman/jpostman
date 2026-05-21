package io.jpostman;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jpostman.Params.Entry;

/**
 * Represents a request URL and its query parameters.
 *
 * <p>
 * Postman may store query parameters in two places:
 * </p>
 * <ul>
 * <li>inside the raw URL, for example {@code /users?id=10}</li>
 * <li>inside {@code request.url.query[]}</li>
 * </ul>
 *
 * <p>
 * The original URL is preserved for debug output. Query parameters are stored
 * with their enabled/disabled state. Disabled parameters are kept internally
 * but are excluded from the generated URL.
 * </p>
 */
public class Url {

	private static final Logger log = LoggerFactory.getLogger(Url.class);

	private String raw;
	private final String original;
	private final Map<String, Entry> params = new LinkedHashMap<>();

	/**
	 * Creates a URL from a raw URL string.
	 *
	 * @param value raw URL value
	 */
	public Url(String value) {
		this(value, value);
	}

	/**
	 * Creates a URL while preserving the original unresolved URL value.
	 *
	 * <p>
	 * The original value is retained for debugging and trace output, while the
	 * provided value is parsed into base URL and query parameters.
	 * </p>
	 *
	 * @param original original unresolved URL
	 * @param value    URL value to parse
	 */
	private Url(String original, String value) {
		this.original = original == null ? "" : original;
		parse(value == null ? "" : value);
	}

	/**
	 * Parses URL information from a Postman request object.
	 *
	 * @param reqObj the {@code request} JSON object
	 * @return parsed URL
	 */
	public static @NonNull Url from(JsonObject reqObj) {
		if (!reqObj.has("url") || reqObj.get("url").isJsonNull()) {
			return new Url("");
		}

		JsonElement urlEl = reqObj.get("url");
		if (urlEl.isJsonPrimitive()) {
			return new Url(urlEl.getAsString());
		}

		if (!urlEl.isJsonObject()) {
			return new Url("");
		}

		JsonObject urlObj = urlEl.getAsJsonObject();
		JsonElement rawEl = urlObj.get("raw");
		if (rawEl == null || rawEl.isJsonNull() || !rawEl.isJsonPrimitive()) {
			return new Url("");
		}

		Url url = new Url(rawEl.getAsString());

		if (urlObj.has("query") && urlObj.get("query").isJsonArray()) {
			for (JsonElement item : urlObj.getAsJsonArray("query")) {
				if (!item.isJsonObject()) {
					continue;
				}
				JsonObject q = item.getAsJsonObject();
				String key = q.has("key") && !q.get("key").isJsonNull() ? q.get("key").getAsString() : "";
				String value = q.has("value") && !q.get("value").isJsonNull() ? q.get("value").getAsString() : "";
				boolean enabled = !(q.has("disabled") && q.get("disabled").getAsBoolean());

				if (!key.isBlank()) {
					url.params.put(key, new Entry(value, enabled));
				}
			}
		}

		return url;
	}

	/**
	 * Parses a raw URL into base URL and enabled query parameters.
	 *
	 * @param value raw URL value
	 */
	private void parse(String value) {
		params.clear();

		if (value.isEmpty()) {
			raw = "";
			return;
		}

		int fragmentIndex = value.indexOf('#');
		String fragment = "";
		String withoutFragment = value;

		if (fragmentIndex >= 0) {
			fragment = value.substring(fragmentIndex);
			withoutFragment = value.substring(0, fragmentIndex);
		}

		int queryIndex = withoutFragment.indexOf('?');
		if (queryIndex < 0) {
			raw = withoutFragment + fragment;
			return;
		}

		raw = withoutFragment.substring(0, queryIndex) + fragment;
		String queryString = withoutFragment.substring(queryIndex + 1);
		for (String pair : queryString.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			String[] parts = pair.split("=", 2);
			String key = parts[0];
			String valuePart = parts.length > 1 ? parts[1] : "";

			if (!key.isBlank()) {
				params.put(key, new Entry(valuePart, true));
			}
		}
	}

	/**
	 * Returns unresolved {@code {{token}}} names found in the original URL.
	 *
	 * <p>
	 * The returned map preserves discovery order and initializes every token value
	 * to an empty string, so callers can fill only the values they need.
	 * </p>
	 *
	 * @return ordered token map, for example {@code {base_url="", token=""}}
	 */
	public Map<String, String> params() {
		Map<String, String> result = new LinkedHashMap<>();
		Params.addTokens(result, getOriginal());
		return result;
	}

	/** @return base URL without query parameters */
	public String getRaw() {
		return raw;
	}

	/** @return original unresolved URL exactly as provided */
	public String getOriginal() {
		return original;
	}

	/** @return enabled query parameters in insertion order */
	public Map<String, String> getParams() {
		Map<String, String> enabled = new LinkedHashMap<>();
		params.forEach((key, info) -> {
			if (info.enabled) {
				enabled.put(key, info.value);
			}
		});
		return enabled;
	}

	/**
	 * Looks up parameter metadata by key.
	 *
	 * @param key parameter name
	 * @return matching {@link Entry}, or {@code null} when absent
	 */
	public Entry getParam(String key) {
		return params.get(key);
	}

	/**
	 * Looks up an enabled query parameter by key.
	 *
	 * @param key query parameter name
	 * @return query value, or {@code null} when absent or disabled
	 */
	public String get(String key) {
		Entry info = getParam(key);
		return info != null && info.enabled ? info.value : null;
	}

	/** @return true when the base URL is empty */
	public boolean isEmpty() {
		return raw.isEmpty();
	}

	/**
	 * Returns a fluent builder pre-populated from this URL and query parameters.
	 *
	 * @return URL/query parameter builder
	 */
	public Params<Url> builder() {
		final String[] value = { raw };
		Map<String, Entry> params = new LinkedHashMap<>(this.params);
		return new Params<>(
				// ADD
				(key, val) -> params.put(key, new Entry(String.valueOf(val), true)),
				// SET
				(key, val) -> {
					Entry info = params.get(key);
					if (info == null) {
						throw new IllegalArgumentException("URL query parameter not found: '" + key + "'");
					}
					params.put(key, new Entry(String.valueOf(val), info.enabled));
				},
				// RESOLVE
				vars -> {
					value[0] = Params.substituteVars(value[0], vars);

					for (String key : new ArrayList<>(params.keySet())) {
						Entry info = params.get(key);
						if (info.enabled) {
							params.put(key, new Entry(Params.substituteVars(info.value, vars), true));
						}
					}
				},
				// BUILD
				() -> {
					Url url = new Url(original, value[0]);
					params.forEach(url.params::put);
					return url;
				});
	}

	/** Logs URL details at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		return String.format("=== Original URL: %s ===\n", original) + toString();
	}

	@Override
	public String toString() {
		Map<String, String> enabled = getParams();
		if (enabled.isEmpty()) {
			return raw;
		}
		String base = raw;
		String fragment = "";

		int fragmentIndex = base.indexOf('#');
		if (fragmentIndex >= 0) {
			fragment = base.substring(fragmentIndex);
			base = base.substring(0, fragmentIndex);
		}

		String queryString = enabled.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("&"));

		return base + "?" + queryString + fragment;
	}
}
