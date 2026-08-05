package io.jpostman.annotations.runtime;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import io.jpostman.ApiResponse;

/**
 * Maps request-execution exceptions to framework-generated HTTP-style
 * responses.
 */
final class JPostmanHttpErrorMapper {

	private JPostmanHttpErrorMapper() {
	}

	static SyntheticResponse map(Throwable failure) {
		Throwable original = failure == null ? new IllegalStateException("Unknown request execution failure") : failure;

		Throwable cause = find(original, ConnectException.class);
		if (cause != null) {
			return response(503, "Service Unavailable", original, cause);
		}

		cause = first(original, HttpTimeoutException.class, SocketTimeoutException.class, TimeoutException.class);
		if (cause != null) {
			return response(504, "Gateway Timeout", original, cause);
		}

		cause = find(original, UnknownHostException.class);
		if (cause != null) {
			return response(502, "Bad Gateway", original, cause);
		}

		cause = first(original, NoRouteToHostException.class, RejectedExecutionException.class,
				CancellationException.class, InterruptedException.class);
		if (cause != null) {
			if (cause instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return response(503, "Service Unavailable", original, cause);
		}

		cause = first(original, SSLException.class, ProtocolException.class, EOFException.class, IOException.class);
		if (cause != null) {
			return response(502, "Bad Gateway", original, cause);
		}

		cause = find(original, IllegalArgumentException.class);
		if (cause != null) {
			return response(400, "Bad Request", original, cause);
		}

		cause = find(original, SecurityException.class);
		if (cause != null) {
			return response(403, "Forbidden", original, cause);
		}

		cause = find(original, UnsupportedOperationException.class);
		if (cause != null) {
			return response(501, "Not Implemented", original, cause);
		}

		cause = find(original, IllegalStateException.class);
		if (cause != null) {
			return response(500, "Internal Server Error", original, cause);
		}

		return response(500, "Internal Server Error", original, deepest(original));
	}

	@SafeVarargs
	private static Throwable first(Throwable failure, Class<? extends Throwable>... types) {
		if (types == null) {
			return null;
		}
		for (Class<? extends Throwable> type : types) {
			Throwable match = find(failure, type);
			if (match != null) {
				return match;
			}
		}
		return null;
	}

	private static Throwable find(Throwable failure, Class<? extends Throwable> type) {
		Throwable current = failure;
		for (int depth = 0; current != null && depth < 50; depth++) {
			if (type.isInstance(current)) {
				return current;
			}
			Throwable next = current.getCause();
			if (next == current) {
				break;
			}
			current = next;
		}
		return null;
	}

	private static Throwable deepest(Throwable failure) {
		Throwable current = failure;
		Throwable deepest = failure;
		for (int depth = 0; current != null && depth < 50; depth++) {
			deepest = current;
			Throwable next = current.getCause();
			if (next == current) {
				break;
			}
			current = next;
		}
		return deepest;
	}

	private static SyntheticResponse response(int statusCode, String reason, Throwable original, Throwable cause) {
		return new SyntheticResponse(statusCode, reason, original, cause == null ? original : cause);
	}

	static final class SyntheticResponse {
		private final int statusCode;
		private final String reason;
		private final Throwable original;
		private final Throwable cause;

		private SyntheticResponse(int statusCode, String reason, Throwable original, Throwable cause) {
			this.statusCode = statusCode;
			this.reason = reason;
			this.original = original;
			this.cause = cause;
		}

		int statusCode() {
			return statusCode;
		}

		String reason() {
			return reason;
		}

		Throwable original() {
			return original;
		}

		Throwable cause() {
			return cause;
		}

		ApiResponse apiResponse() {
			String body = body();
			return new ApiResponse(statusCode, body, body.getBytes(StandardCharsets.UTF_8), Collections.emptyMap());
		}

		private String body() {
			String type = cause == null ? original.getClass().getName() : cause.getClass().getName();
			String message = cause == null ? original.getMessage() : cause.getMessage();
			if (message == null || message.isBlank()) {
				message = reason;
			}
			return "{\n  \"statusCode\": " + statusCode + ",\n  \"error\": \"" + escape(reason) + "\",\n"
					+ "  \"message\": \"" + escape(message) + "\",\n  \"exception\": \"" + escape(type) + "\",\n"
					+ "  \"synthetic\": true\n}";
		}

		private static String escape(String value) {
			String text = value == null ? "" : value;
			StringBuilder out = new StringBuilder(text.length() + 16);
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				switch (ch) {
				case '\\':
					out.append("\\\\");
					break;
				case '"':
					out.append("\\\"");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (ch < 0x20) {
						out.append(String.format("\\u%04x", (int) ch));
					} else {
						out.append(ch);
					}
				}
			}
			return out.toString();
		}
	}
}
