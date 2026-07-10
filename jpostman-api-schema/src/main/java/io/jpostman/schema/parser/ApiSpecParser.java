package io.jpostman.schema.parser;

import io.jpostman.schema.importer.GraphQlImporter;
import io.jpostman.schema.importer.OpenApiImporter;
import io.jpostman.schema.importer.PostmanImporter;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.util.ApiMethodNameAllocator;
import io.jpostman.schema.util.ApiSpecEnvScanner;
import io.jpostman.schema.util.BaseUrlOverride;

/**
 * Entry point for parsing pasted API documents into the common JPostman API
 * schema model.
 */
public final class ApiSpecParser {
	/**
	 * Creates a new ApiSpecParser instance.
	 */
	private ApiSpecParser() {
	}

	/**
	 * Parses the supplied API document into the common JPostman API schema model.
	 */
	public static ApiSpec parse(String content) {
		return parse(content, ApiSpecParserOptions.defaults());
	}

	/**
	 * Parses the supplied API document into the common JPostman API schema model.
	 */
	public static ApiSpec parse(String content, ApiSpecParserOptions options) {
		ApiSpecParserOptions safeOptions = options == null ? ApiSpecParserOptions.defaults() : options;
		ApiSpecFormat format = ApiSpecDetector.detect(content);

		if (content == null || content.isBlank()) {
			throw new ApiSpecParseException(ApiSpecFormat.UNKNOWN,
					"The API document is empty. Paste an OpenAPI/Swagger, Postman Collection, or GraphQL schema document.");
		}

		ApiSpec spec;
		try {
			switch (format) {
			case OPENAPI:
				spec = new OpenApiImporter().importSpec(content, safeOptions);
				break;
			case POSTMAN:
				spec = new PostmanImporter().importSpec(content, safeOptions);
				break;
			case GRAPHQL:
				spec = new GraphQlImporter().importSpec(content, safeOptions);
				break;
			default:
				throw new ApiSpecParseException(ApiSpecFormat.UNKNOWN,
						"Unsupported API document format. Expected OpenAPI/Swagger, Postman Collection, or GraphQL schema.");
			}
		} catch (ApiSpecParseException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiSpecParseException(format, buildUserMessage(format, e), e);
		}

		BaseUrlOverride.apply(spec);
		ApiMethodNameAllocator.apply(spec);
		ApiSpecEnvScanner.scan(spec);
		return spec;
	}

	/**
	 * Builds the user message.
	 */
	private static String buildUserMessage(ApiSpecFormat format, Exception e) {
		String details = rootMessage(e);
		if (format == ApiSpecFormat.OPENAPI) {
			return "Invalid OpenAPI/Swagger document. Check required fields like openapi/swagger, info, and paths. Details: "
					+ details;
		}
		if (format == ApiSpecFormat.POSTMAN) {
			return "Invalid Postman Collection document. Check that the JSON is valid and contains info and item requests. Details: "
					+ details;
		}
		if (format == ApiSpecFormat.GRAPHQL) {
			return "Invalid GraphQL schema. Check type definitions, braces, field arguments, and return types. Details: "
					+ details;
		}
		return "Unsupported API document format. Expected OpenAPI/Swagger, Postman Collection, or GraphQL schema.";
	}

	/**
	 * Handles root message logic for this class.
	 */
	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		String message = current.getMessage();
		if (message == null || message.isBlank()) {
			message = throwable.getClass().getSimpleName();
		}
		return message.replace('\n', ' ').replace('\r', ' ').trim();
	}
}
