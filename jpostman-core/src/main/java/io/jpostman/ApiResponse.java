package io.jpostman;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

/**
 * Framework-neutral HTTP response returned by JPostman executors.
 */
public class ApiResponse {
	
	private static final Logger log = LoggerFactory.getLogger(ApiResponse.class);

	private final int statusCode;
	private final String body;
	private final byte[] bodyBytes;
	private final Map<String, List<String>> headers;

	/**
	 * Creates a response wrapper.
	 *
	 * @param statusCode HTTP status code
	 * @param body       response body as text
	 * @param bodyBytes  raw response body bytes
	 * @param headers    response headers
	 */
	public ApiResponse(int statusCode, String body, byte[] bodyBytes, Map<String, List<String>> headers) {
		this.statusCode = statusCode;
		this.body = body == null ? "" : body;
		this.bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes.clone();
		this.headers = headers == null ? Map.of() : Collections.unmodifiableMap(headers);
	}

	/** @return HTTP status code. */
	public int statusCode() {
		return statusCode;
	}

	/** @return response body as text. */
	public String getBody() {
		return body;
	}

	/**
	 * Attempts to parse a raw string as JSON, returning {@code null} on failure.
	 */
	public JsonElement parse() {
		return parse(getBody());
	}

	/**
	 * Attempts to parse a raw string as JSON, returning {@code null} on failure.
	 */
	public JsonElement parse(String raw) {
		return Body.parse(raw);
	}

	/** @return raw response body bytes. */
	public byte[] asByteArray() {
		return bodyBytes.clone();
	}

	/** @return response headers. */
	public Map<String, List<String>> getHeaders() {
		return headers;
	}

	/**
	 * Checks whether a value exists in the JSON response body using a simple
	 * dot/bracket path.
	 *
	 * <p>
	 * This method returns {@code true} when the path resolves to a JSON element,
	 * even if the value is JSON {@code null}. It returns {@code false} when the
	 * body is not valid JSON or the path cannot be found.
	 * </p>
	 *
	 * <p>
	 * Examples:
	 * </p>
	 *
	 * <pre>
	 * boolean hasToken = response.exists("accessToken");
	 * boolean hasProduct = response.exists("products[0]");
	 * boolean hasTitle = response.exists("products[0].title");
	 * </pre>
	 *
	 * @param path simple JSON path
	 * @return {@code true} if the path exists
	 */
	public boolean exists(String path) {
	    return Params.pathElement(parse(), path) != null;
	}

	/**
	 * Reads a value from the JSON response body using a simple dot/bracket path.
	 *
	 * <p>
	 * Examples:
	 * </p>
	 *
	 * <pre>
	 * String token = response.path("accessToken");
	 * Integer id = response.path("products[0].id");
	 * String title = response.path("products[0].title");
	 * </pre>
	 *
	 * @param path simple JSON path
	 * @param <T> expected return type
	 * @return selected value converted to a Java value
	 */
	public <T> T path(String path) {
		return Params.path(parse(), path);
	}

	/**
	 * Returns the response body formatted for display.
	 *
	 * <p>
	 * JSON objects and arrays are returned as pretty-printed JSON. JSON string
	 * primitives are returned as plain text without JSON quotes. If the response
	 * body could not be parsed, the original raw body is returned.
	 * </p>
	 *
	 * @return formatted response body text
	 */
	public String pretty() {
		JsonElement parsed = parse();

		if (parsed == null) {
			return body;
		}

		return Body.pretty(parsed);
	}


	/**
	 * Returns a readable response summary including status code, and body.
	 *
	 * @return formatted response summary
	 */
	public String log() {
		return log(false);
	}

	
	/**
	 * Returns a readable response summary including status code, headers, and body.
	 *
	 * @return formatted response summary
	 */
	public String log(boolean all) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Status Code: %d\n", statusCode));
		if (all) {
			sb.append(headers.entrySet().stream().map(e -> String.format("  %-35s = %s\n", e.getKey(), e.getValue()))
					.collect(Collectors.joining()));
		}
		sb.append(String.format("Body: %s\n", pretty()));
		return sb.toString();
	}

	/** Logs this body at TRACE level. */
	public void print() {
		log.trace(log());
	}

	@Override
	public String toString() {
		return "ApiResponse{statusCode=" + statusCode + ", bodyLength=" + body.length() + "}";
	}
}
