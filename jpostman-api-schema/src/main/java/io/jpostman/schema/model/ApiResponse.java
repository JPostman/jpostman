package io.jpostman.schema.model;

/**
 * Represents one normalized API response parsed from OpenAPI, Swagger,
 * Postman, or GraphQL metadata.
 */
public class ApiResponse {
	private String code;
	private String description;
	private String contentType;
	private ApiBody body;
	private ApiBody example;

	/**
	 * Creates a new ApiResponse instance.
	 */
	public ApiResponse() {
	}

	/**
	 * Creates a new ApiResponse instance.
	 */
	public ApiResponse(String code, String description) {
		this.code = code;
		this.description = description;
	}

	/**
	 * Returns the response status code.
	 */
	public String getCode() {
		return code;
	}

	/**
	 * Sets the response status code.
	 */
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * Returns the response description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the response description.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the response content type.
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Sets the response content type.
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Returns the response body template.
	 */
	public ApiBody getBody() {
		return body;
	}

	/**
	 * Sets the response body template.
	 */
	public void setBody(ApiBody body) {
		this.body = body;
	}

	/**
	 * Returns the response example body.
	 */
	public ApiBody getExample() {
		return example;
	}

	/**
	 * Sets the response example body.
	 */
	public void setExample(ApiBody example) {
		this.example = example;
	}
}
