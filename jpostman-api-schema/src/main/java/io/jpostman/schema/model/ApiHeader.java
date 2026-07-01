package io.jpostman.schema.model;

/**
 * Represents one normal request header in the common API schema model.
 */
public class ApiHeader {
	private String name;
	private String value;
	private String description;
	private boolean required;

	/**
	 * Creates a new ApiHeader instance.
	 */
	public ApiHeader() {
	}

	/**
	 * Creates a new ApiHeader instance.
	 */
	public ApiHeader(String name, String value, boolean required) {
		this.name = name;
		this.value = value;
		this.required = required;
	}

	/**
	 * Returns the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the value.
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the value.
	 */
	public void setValue(String value) {
		this.value = value;
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

	/**
	 * Returns whether required is enabled or true.
	 */
	public boolean isRequired() {
		return required;
	}

	/**
	 * Sets the required.
	 */
	public void setRequired(boolean required) {
		this.required = required;
	}
}
