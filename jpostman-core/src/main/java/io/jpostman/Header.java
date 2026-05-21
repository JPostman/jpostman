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
 * Represents the HTTP headers of a Postman request.
 *
 * <p>
 * Headers are stored in insertion order. Disabled Postman headers are preserved
 * internally, but only enabled headers are exposed through
 * {@link #getParams()}, rendered in {@link #toString()}, and applied to
 * requests.
 * </p>
 */
public class Header {

	private static final Logger log = LoggerFactory.getLogger(Header.class);

	private final Map<String, Entry> params = new LinkedHashMap<>();

	/** @return true when no enabled headers are present. */
	public boolean isEmpty() {
		return getParams().isEmpty();
	}

	/** @return enabled headers in insertion order. */
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
	 * Returns an enabled header by name.
	 *
	 * @param key header name
	 * @return header value, or {@code null} when absent or disabled
	 */
	public String get(String key) {
		Entry info = getParam(key);
		return info != null && info.enabled ? info.value : null;
	}

	/**
	 * Returns unresolved {@code {{token}}} names found in header names and values.
	 * <p>
	 * Each token is returned as a key mapped to an empty string. For example,
	 * {@code {{userId}}} becomes {@code "userId" -> ""}.
	 *
	 * @return ordered token map found in header names and values
	 */
	public Map<String, String> params() {
		Map<String, String> result = new LinkedHashMap<>();
		Map<String, String> values = new LinkedHashMap<>();

		params.forEach((key, info) -> {
			values.put(key, info.value);
		});

		Params.addTokens(result, values);
		return result;
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Parse the {@code header} array from a Postman v2.1 {@code request} object.
	 * Disabled entries are preserved internally but excluded from public output.
	 *
	 * @param reqObj the {@code request} JSON object
	 * @return parsed {@link Header}; empty when none are present
	 */
	public static @NonNull Header from(JsonObject reqObj) {
		Header header = new Header();
		if (!reqObj.has("header")) {
			return header;
		}
		JsonElement el = reqObj.get("header");
		if (!el.isJsonArray()) {
			return header;
		}
		for (JsonElement item : el.getAsJsonArray()) {
			if (!item.isJsonObject()) {
				continue;
			}
			JsonObject h = item.getAsJsonObject();
			String key = h.has("key") && !h.get("key").isJsonNull() ? h.get("key").getAsString() : "";
			String value = h.has("value") && !h.get("value").isJsonNull() ? h.get("value").getAsString() : "";
			boolean enabled = !(h.has("disabled") && h.get("disabled").getAsBoolean());
			if (!key.isBlank()) {
				header.params.put(key, new Entry(value, enabled));
			}
		}
		return header;
	}

	/**
	 * Returns a fluent builder pre-populated from this header map.
	 *
	 * @return header parameter builder
	 */
	public Params<Header> builder() {
		Map<String, Entry> params = new LinkedHashMap<>(this.params);
		Params.Builder<Header> buildFn = () -> {
			Header header = new Header();
			params.forEach(header.params::put);
			return header;
		};
		return new Params<Header>(
				// ADD
				(String key, Object value) -> params.put(key, new Entry(String.valueOf(value), true)),
				// SET
				(String key, Object value) -> {
					Entry info = params.get(key);
					if (info == null) {
						throw new IllegalArgumentException("Header key not found: '" + key + "'");
					}
					params.put(key, new Entry(String.valueOf(value), info.enabled));
				},
				// RESOLVE
				vars -> {
					for (String k : new ArrayList<>(params.keySet())) {
						Entry info = params.get(k);
						if (info.enabled) {
							params.put(k, new Entry(Params.substituteVars(info.value, vars), true));
						}
					}
				},
				// BUILD
				buildFn);
	}

	/** Logs headers at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including enabled headers. */
	public String toDebugString() {
		if (isEmpty()) {
			return "  (no headers)";
		}
		return toString();
	}

	@Override
	public String toString() {
		return getParams().entrySet().stream().map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
				.collect(Collectors.joining());
	}
}
