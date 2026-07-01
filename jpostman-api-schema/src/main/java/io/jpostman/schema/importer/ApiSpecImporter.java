package io.jpostman.schema.importer;

import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParserOptions;

/**
 * Defines the contract for converting one external API document format into the
 * common JPostman API schema model.
 */
public interface ApiSpecImporter {
	/**
	 * Parses the supplied document content and returns a normalized API
	 * specification.
	 */
	ApiSpec importSpec(String content, ApiSpecParserOptions options);
}
