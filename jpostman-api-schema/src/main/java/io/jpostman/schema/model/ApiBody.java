package io.jpostman.schema.model;

/**
 * Represents the request body template or example body for an API operation.
 */
public class ApiBody {
	private ApiBodyType type = ApiBodyType.NONE;
	private String content;
	private String description;

	/**
	 * Creates a new ApiBody instance.
	 */
	public ApiBody() {
	}

	/**
	 * Creates a new ApiBody instance.
	 */
	public ApiBody(ApiBodyType type, String content) {
		this.type = type;
		this.content = content;
	}

	/**
	 * Returns the type.
	 */
	public ApiBodyType getType() {
		return type;
	}

	/**
	 * Sets the type.
	 */
	public void setType(ApiBodyType type) {
		this.type = type;
	}

	/**
	 * Returns the content.
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Sets the content.
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * Returns the description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description.
	 */
	public void setDescription(String description) {
		this.description = description;
	}
}
