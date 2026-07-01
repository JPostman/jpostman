package io.jpostman.schema.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents example request values that can be used to populate environment
 * variables.
 */
public class ApiExample {
	private String name;
	private String path;
	private List<ApiParam> queryParams = new ArrayList<>();
	private List<ApiHeader> headers = new ArrayList<>();
	private ApiBody body;

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
	 * Returns the path.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the path.
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * Returns the query params.
	 */
	public List<ApiParam> getQueryParams() {
		return queryParams;
	}

	/**
	 * Sets the query params.
	 */
	public void setQueryParams(List<ApiParam> queryParams) {
		this.queryParams = queryParams == null ? new ArrayList<>() : queryParams;
	}

	/**
	 * Returns the headers.
	 */
	public List<ApiHeader> getHeaders() {
		return headers;
	}

	/**
	 * Sets the headers.
	 */
	public void setHeaders(List<ApiHeader> headers) {
		this.headers = headers == null ? new ArrayList<>() : headers;
	}

	/**
	 * Returns the body.
	 */
	public ApiBody getBody() {
		return body;
	}

	/**
	 * Sets the body.
	 */
	public void setBody(ApiBody body) {
		this.body = body;
	}
}
