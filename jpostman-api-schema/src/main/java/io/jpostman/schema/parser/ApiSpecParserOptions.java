package io.jpostman.schema.parser;

/**
 * Contains optional parser settings supplied by the UI or caller.
 */
public class ApiSpecParserOptions {
	private String baseUrl;
	private Boolean overrideUrl;

	/**
	 * Returns the base url.
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * Sets the base url.
	 */
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/**
	 * Returns the override url.
	 */
	public Boolean getOverrideUrl() {
		return overrideUrl;
	}

	/**
	 * Sets the override url.
	 */
	public void setOverrideUrl(Boolean overrideUrl) {
		this.overrideUrl = overrideUrl;
	}

	/**
	 * Creates parser options with default values.
	 */
	public static ApiSpecParserOptions defaults() {
		return new ApiSpecParserOptions();
	}
}
