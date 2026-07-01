package io.jpostman.schema.parser;

/**
 * Detects the external document format before parsing it into the common model.
 */
public final class ApiSpecDetector {
	/**
	 * Creates a new ApiSpecDetector instance.
	 */
	private ApiSpecDetector() {
	}

	/**
	 * Detects the most likely API document format from the supplied content.
	 */
	public static ApiSpecFormat detect(String content) {
		if (content == null || content.isBlank()) {
			return ApiSpecFormat.UNKNOWN;
		}

		String text = content.trim();
		String lower = text.toLowerCase();

		if (lower.contains("\"openapi\"") || lower.contains("openapi:") || lower.contains("\"swagger\"")
				|| lower.contains("swagger:")) {
			return ApiSpecFormat.OPENAPI;
		}

		if (lower.contains("schema.getpostman.com")
				|| (lower.contains("\"info\"") && lower.contains("\"item\"") && lower.contains("\"request\""))) {
			return ApiSpecFormat.POSTMAN;
		}

		if (lower.contains("type query") || lower.contains("type mutation") || lower.contains("schema {")
				|| lower.contains("extend type query")) {
			return ApiSpecFormat.GRAPHQL;
		}

		return ApiSpecFormat.UNKNOWN;
	}
}
