package io.jpostman.schema.parser;

/**
 * User-facing parse exception for API schema imports.
 *
 * <p>
 * This exception is intentionally concise so the UI can show
 * {@link #getUserMessage()} directly near the editor.
 * </p>
 */
public class ApiSpecParseException extends IllegalArgumentException {
	private static final long serialVersionUID = 6913023205083657552L;
	private final ApiSpecFormat format;
	private final String userMessage;

	/**
	 * Creates a new ApiSpecParseException instance.
	 */
	public ApiSpecParseException(ApiSpecFormat format, String userMessage) {
		super(userMessage);
		this.format = format;
		this.userMessage = userMessage;
	}

	/**
	 * Creates a new ApiSpecParseException instance.
	 */
	public ApiSpecParseException(ApiSpecFormat format, String userMessage, Throwable cause) {
		super(userMessage, cause);
		this.format = format;
		this.userMessage = userMessage;
	}

	/**
	 * Returns the format.
	 */
	public ApiSpecFormat getFormat() {
		return format;
	}

	/**
	 * Returns the user message.
	 */
	public String getUserMessage() {
		return userMessage;
	}
}
