package io.jpostman.schema.model;

/**
 * Represents authentication metadata for an API operation without mixing auth
 * into normal headers.
 */
public class ApiAuth {
	private ApiAuthType type = ApiAuthType.NONE;
	private String name;
	private String value;
	private ApiAuthLocation location = ApiAuthLocation.HEADER;

	/**
	 * Returns the type.
	 */
	public ApiAuthType getType() {
		return type;
	}

	/**
	 * Sets the type.
	 */
	public void setType(ApiAuthType type) {
		this.type = type;
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
	 * Returns the location.
	 */
	public ApiAuthLocation getLocation() {
		return location;
	}

	/**
	 * Sets the location.
	 */
	public void setLocation(ApiAuthLocation location) {
		this.location = location;
	}
}
