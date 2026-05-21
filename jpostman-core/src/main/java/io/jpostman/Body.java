package io.jpostman;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

/**
 * Represents a Postman request body.
 *
 * <p>
 * The class keeps the original body mode ({@code raw}, {@code formdata},
 * {@code urlencoded}, {@code graphql}, or {@code none}) and the raw text that
 * can be sent to Rest Assured. JSON parsing is handled inside the builder when
 * JSON field mutation is needed.
 * </p>
 */
public class Body {

	private static final String NONE = "none";
	private static final Gson GSON = new Gson();
	private static final Gson PRETTY_GSON = GSON.newBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final Logger log = LoggerFactory.getLogger(Body.class);

	private final String mode;
	private final String raw;
	private final String language;

	Body(String mode, String raw, String language) {
		this.mode = mode;
		this.raw = raw;
		this.language = language;
	}

	/**
	 * Parses the {@code body} section from a Postman request JSON object.
	 *
	 * @param reqObj request JSON object that may contain a {@code body} field
	 * @return parsed body; returns {@code none} body when the field is absent,
	 *         null, or not a JSON object
	 */
	public static Body from(JsonObject reqObj) {
		if (reqObj == null || !reqObj.has("body") || reqObj.get("body").isJsonNull()
				|| !reqObj.get("body").isJsonObject()) {
			return new Body(NONE, "", "");
		}

		JsonObject bodyObj = reqObj.getAsJsonObject("body");
		String mode = getString(bodyObj, "mode", NONE);

		switch (mode) {
		case "raw":
			String raw = getString(bodyObj, "raw", "");
			String language = getLanguage(bodyObj);
			return new Body(mode, raw, language);
		case "graphql":
			String graphql = bodyObj.has("graphql") && !bodyObj.get("graphql").isJsonNull()
					? bodyObj.get("graphql").toString()
					: "";
			return new Body(mode, graphql, "");
		case "formdata":
		case "urlencoded":
			JsonElement payload = bodyObj.has(mode) && !bodyObj.get(mode).isJsonNull() ? bodyObj.get(mode) : null;
			JsonElement parsed = parseBodyPayload(payload);
			return new Body(mode, parsed == null ? "" : parsed.toString(), "");
		default:
			return new Body(mode, "", "");
		}
	}

	/**
	 * Converts Postman array/string payload forms into a single JSON element used
	 * internally for form-data and URL-encoded bodies.
	 *
	 * <p>
	 * Postman normally exports these payloads as arrays. Some tests and
	 * older/custom exports may store JSON-looking text instead. A non-blank string
	 * is parsed as JSON when possible; invalid strings are preserved as string
	 * primitives. Blank strings are treated as no body.
	 * </p>
	 */
	private static JsonElement parseBodyPayload(JsonElement payload) {
		if (payload == null) {
			return null;
		}

		if (payload.isJsonPrimitive() && payload.getAsJsonPrimitive().isString()) {
			String raw = payload.getAsString();
			if (raw.isBlank()) {
				return null;
			}
			try {
				return JsonParser.parseString(raw);
			} catch (JsonSyntaxException ignored) {
				return new JsonPrimitive(raw);
			}
		}
		return payload.deepCopy();
	}

	/**
	 * Returns a string member from a JSON object or a default value when absent.
	 */
	private static String getString(JsonObject obj, String key, String def) {
		JsonElement value = obj.get(key);
		return value != null && !value.isJsonNull() && value.isJsonPrimitive() ? value.getAsString() : def;
	}

	/** Extracts the raw body language option from a Postman body object. */
	private static String getLanguage(JsonObject bodyObj) {
		if (bodyObj.has("options") && bodyObj.get("options").isJsonObject()) {
			JsonObject options = bodyObj.getAsJsonObject("options");
			if (options.has("raw") && options.get("raw").isJsonObject()) {
				return getString(options.getAsJsonObject("raw"), "language", "");
			}
		}
		return "";
	}

	/**
	 * Returns unresolved {@code {{token}}} names found in this body.
	 *
	 * <p>
	 * The returned map preserves discovery order and initializes every token value
	 * to an empty string, so callers can fill only the values they need.
	 * </p>
	 *
	 * @return ordered token map, for example {@code {userId="", token=""}}
	 */
	public Map<String, String> params() {
		Map<String, String> result = new LinkedHashMap<>();
		Params.addTokens(result, getRaw());
		return result;
	}

	/**
	 * Returns a fluent builder pre-populated from this body.
	 *
	 * <p>
	 * Template replacement is handled through {@link Params}. JSON parsing is
	 * performed only inside the builder, so raw template bodies like
	 * {@code {"username": {{TOKEN}}}} can be resolved first and mutated after they
	 * become valid JSON.
	 * </p>
	 *
	 * @return body parameter builder
	 */
	public Params<Body> builder() {
		final String bodyMode = this.mode;
		final String bodyLang = this.language;
		final String[] workingRaw = new String[] { raw };
		final JsonElement[] workingParsed = new JsonElement[] { initialParsed(bodyMode, raw) };
		final List<BodyMutation> pendingMutations = new ArrayList<>();

		return new Params<>(
				// ADD
				(key, value) -> pendingMutations.add(new BodyMutation(key, value, true)),
				// SET
				(key, value) -> pendingMutations.add(new BodyMutation(key, value, false)),
				// RESOLVE
				vars -> {
					if (workingParsed[0] != null) {
						applyPendingMutations(workingRaw, workingParsed, pendingMutations);
						workingParsed[0] = substituteJson(workingParsed[0], vars);
					} else {
						workingRaw[0] = Params.substituteVars(workingRaw[0], vars);
						workingParsed[0] = parse(workingRaw[0]);

						applyPendingMutations(workingRaw, workingParsed, pendingMutations);

						if (workingParsed[0] != null) {
							workingParsed[0] = substituteJson(workingParsed[0], vars);
						}
					}

					if (workingParsed[0] != null) {
						workingRaw[0] = workingParsed[0].toString();
					}
				},
				// BUILD
				() -> {
					applyPendingMutations(workingRaw, workingParsed, pendingMutations);
					JsonElement result = workingParsed[0] == null ? null : workingParsed[0].deepCopy();
					String resultRaw = result == null ? workingRaw[0] : result.toString();
					return new Body(bodyMode, resultRaw, bodyLang);
				});
	}

	/**
	 * Returns the parsed JSON object/array used as the starting point for
	 * mutations.
	 */
	private static JsonElement initialParsed(String mode, String raw) {
		JsonElement parsed = parse(raw);
		if (parsed == null && !"raw".equals(mode)) {
			return new JsonObject();
		}
		return parsed;
	}

	/** Applies queued body field changes to the current JSON object body. */
	private static void applyPendingMutations(String[] workingRaw, JsonElement[] workingParsed,
			List<BodyMutation> pendingMutations) {
		if (pendingMutations.isEmpty()) {
			return;
		}
		JsonElement parsed = workingParsed[0];
		if (parsed == null || !parsed.isJsonObject()) {
			throw new IllegalArgumentException("Body builder add/set requires a JSON object body: " + workingRaw[0]);
		}
		JsonObject obj = parsed.getAsJsonObject();
		for (BodyMutation mutation : new ArrayList<>(pendingMutations)) {
			if (!mutation.addIfMissing && !obj.has(mutation.key)) {
				throw new IllegalArgumentException("Body key not found: '" + mutation.key + "'");
			}
			obj.add(mutation.key, GSON.toJsonTree(mutation.value));
		}
		pendingMutations.clear();
	}

	/**
	 * Attempts to parse a raw string as JSON, returning {@code null} on failure.
	 */
	public static JsonElement parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return JsonParser.parseString(raw);
		} catch (JsonSyntaxException ignored) {
			return null;
		}
	}

	/** Recursively substitutes string values inside a parsed JSON tree. */
	private static JsonElement substituteJson(JsonElement el, Map<String, ?> vars) {
		if (el.isJsonNull()) {
			return el;
		}
		if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
			return GSON.toJsonTree(Params.substituteVars(el.getAsString(), vars));
		}
		if (el.isJsonArray()) {
			JsonArray arr = el.getAsJsonArray();
			for (int i = 0; i < arr.size(); i++) {
				arr.set(i, substituteJson(arr.get(i), vars));
			}
			return arr;
		}
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			for (String key : new ArrayList<>(obj.keySet())) {
				obj.add(key, substituteJson(obj.get(key), vars));
			}
			return obj;
		}
		return el;
	}

	/**
	 * @return Postman body mode, for example {@code raw}, {@code formdata}, or
	 *         {@code none}.
	 */
	public String getMode() {
		return mode;
	}

	/** @return raw body text that will be sent with the request. */
	public String getRaw() {
		return raw;
	}

	/** @return raw body language from Postman options, for example {@code json}. */
	public String getLanguage() {
		return language;
	}

	/**
	 * Returns the body formatted for display.
	 * <p>
	 * JSON objects and arrays are returned as pretty-printed JSON. JSON string
	 * primitives are returned as plain text without JSON quotes. If the body could
	 * not be parsed, the original raw body is returned.
	 *
	 * @return formatted body text
	 */
	public String pretty() {
		JsonElement parsed = getParsed();
		if (parsed == null) {
			return raw;
		}
		return pretty(parsed);
	}
	
	/**
	 * Formats a parsed JSON value for display.
	 *
	 * <p>
	 * JSON objects and arrays are returned as pretty-printed JSON. JSON string
	 * primitives are returned as plain text without the surrounding JSON quotes.
	 * Other JSON primitives, such as numbers and booleans, are returned using JSON
	 * formatting.
	 * </p>
	 *
	 * <p>
	 * If {@code parsed} is {@code null}, an empty string is returned.
	 * </p>
	 *
	 * @param parsed parsed JSON value to format; may be {@code null}
	 * @return formatted display text
	 */
	public static String pretty(JsonElement parsed) {
		if (parsed == null) {
			return "";
		}

		if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
			return parsed.getAsString();
		}

		return PRETTY_GSON.toJson(parsed);
	}

	/**
	 * Lazily parses the current raw body as JSON.
	 *
	 * @return parsed JSON body when the raw body is valid JSON; otherwise
	 *         {@code null}
	 */
	public JsonElement getParsed() {
		return parse(raw);
	}

	/** @return true when there is no sendable body content. */
	public boolean isEmpty() {
		return NONE.equals(mode) || raw.isEmpty();
	}

	/** Logs this body at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		return toString();
	}

	@Override
	public String toString() {
		if ("raw".equals(mode)) {
			return "[" + mode + (language.isEmpty() ? "" : "/" + language) + "] " + pretty();
		}

		JsonElement parsed = getParsed();
		if (parsed != null) {
			return "[" + mode + "] " + parsed;
		}
		return "[" + mode + "]";
	}

	/**
	 * Queues a body field mutation until the body can be parsed as a JSON object.
	 */
	private static final class BodyMutation {
		private final String key;
		private final Object value;
		private final boolean addIfMissing;

		private BodyMutation(String key, Object value, boolean addIfMissing) {
			this.key = key;
			this.value = value;
			this.addIfMissing = addIfMissing;
		}
	}
}
