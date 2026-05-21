package io.jpostman;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the authentication configuration of a Postman request or
 * collection.
 *
 * <p>
 * Supported auth types (Postman v2.1):
 * <ul>
 * <li>{@code noauth} — no authentication</li>
 * <li>{@code bearer} — Bearer token; key {@code "token"}</li>
 * <li>{@code basic} — Basic auth; keys {@code "username"},
 * {@code "password"}</li>
 * <li>{@code apikey} — API key; keys {@code "key"}, {@code "value"},
 * {@code "in"}</li>
 * <li>{@code oauth2} — OAuth 2.0; keys vary (e.g. {@code "accessToken"})</li>
 * <li>{@code oauth1} — OAuth 1.0; keys vary</li>
 * <li>{@code digest} — Digest auth; keys {@code "username"},
 * {@code "password"}</li>
 * <li>{@code hawk} — Hawk auth</li>
 * <li>{@code awsv4} — AWS Signature v4</li>
 * <li>{@code ntlm} — NTLM auth</li>
 * </ul>
 *
 * Use {@link #from(JsonObject)} to parse from a raw request or collection
 * object.
 */
public class Auth {

	private static final Logger log = LoggerFactory.getLogger(Auth.class);

	private final String raw;
	private final String type;
	private final Map<String, String> params;

	Auth(String raw, String type, Map<String, String> params) {
		this.raw = raw;
		this.type = type;
		this.params = Collections.unmodifiableMap(params);
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Parse the {@code auth} object from a Postman v2.1 {@code request} or
	 * collection root JSON object.
	 *
	 * @param obj the JSON object that may contain an {@code auth} field
	 * @return populated {@link Auth}; type is {@code "noauth"} when absent
	 */
	public static @NonNull Auth from(JsonObject obj) {
		if (!obj.has("auth") || obj.get("auth").isJsonNull()) {
			return new Auth("", "noauth", new LinkedHashMap<>());
		}
		if (!obj.get("auth").isJsonObject()) {
			return new Auth("", "noauth", new LinkedHashMap<>());
		}
		JsonObject authObj = obj.getAsJsonObject("auth");
		String type = authObj.has("type") && !authObj.get("type").isJsonNull() ? authObj.get("type").getAsString()
				: "noauth";

		Map<String, String> params = new LinkedHashMap<>();
		String raw = "";

		// Postman v2.1 stores auth params as an array: [{key, value, type}, ...]
		// The array key matches the auth type (e.g. "bearer", "basic", "apikey").
		if (authObj.has(type)) {
			JsonElement val = authObj.get(type);
			raw = val.toString();
			if (val.isJsonArray()) {
				for (JsonElement el : authObj.getAsJsonArray(type)) {
					if (!el.isJsonObject())
						continue;
					JsonObject entry = el.getAsJsonObject();
					String key = entry.has("key") && !entry.get("key").isJsonNull() ? entry.get("key").getAsString()
							: "";
					String value = entry.has("value") && !entry.get("value").isJsonNull()
							? entry.get("value").getAsString()
							: "";
					if (!key.isEmpty())
						params.put(key, value);
				}
			}
		}

		// Fallback: Postman v2.0 stores params as a flat object directly under the type
		// key.
		if (params.isEmpty() && authObj.has(type) && authObj.get(type).isJsonObject()) {
			JsonObject flat = authObj.getAsJsonObject(type);
			for (Map.Entry<String, JsonElement> entry : flat.entrySet()) {
				params.put(entry.getKey(), entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : "");
			}
		}

		return new Auth(raw, type, params);
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	/**
	 * Auth type — e.g. {@code "bearer"}, {@code "basic"}, {@code "apikey"},
	 * {@code "oauth2"}, {@code "noauth"}.
	 */
	public String getType() {
		return type;
	}

	/** @return immutable auth parameter map. */
	public Map<String, String> getParams() {
		return params;
	}

	/**
	 * Returns a single auth parameter by key.
	 *
	 * @param key auth parameter name
	 * @return parameter value, or an empty string when absent
	 */
	public String get(String key) {
		return params.getOrDefault(key, "");
	}

	/** @return true when the auth type is {@code noauth}. */
	public boolean isNoAuth() {
		return "noauth".equals(type);
	}

	/** @return raw auth parameter JSON for the selected auth type. */
	public String getRaw() {
		return raw;
	}

	/**
	 * Returns unresolved {@code {{token}}} names found in this auth configuration.
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
		Params.addTokens(result, raw);
		return result;
	}

	/**
	 * Returns a fluent builder pre-populated from this auth's parameter map.
	 *
	 * @return auth parameter builder
	 */
	public Params<Auth> builder() {
		Map<String, String> params = new LinkedHashMap<>(this.params);
		Params.Builder<Auth> buildFn = () -> new Auth(this.raw, this.type, new LinkedHashMap<>(params));
		return new Params<Auth>(
				// ADD
				(String key, Object value) -> params.put(key, String.valueOf(value)),
				// SET (Updates an existing key; throws if the key does not exist)
				(String key, Object value) -> {
					if (!params.containsKey(key))
						throw new IllegalArgumentException("Auth key not found: '" + key + "'");
					params.put(key, String.valueOf(value));
				},
				// RESOLVE
				vars -> {
					for (String k : new ArrayList<>(params.keySet())) {
						params.put(k, Params.substituteVars(params.get(k), vars));
					}
				},
				// BUILD
				buildFn);
	}

	/** Logs auth details at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		if (params.isEmpty()) {
			return "  (noauth)";
		}
		return toString();
	}

	@Override
	public String toString() {
		if (isNoAuth())
			return "[noauth]";
		return "[" + type + "] " + params;
	}
}
