package io.jpostman.schema.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one normalized API operation parsed from OpenAPI, Swagger,
 * Postman, or GraphQL.
 */
public class ApiOperation {
	private String folder;
	private String name;
	private String methodName;
	private String description;
	private String method;
	private List<String> allowedMethods = new ArrayList<>();
	private String path;
	private List<ApiParam> queryParams = new ArrayList<>();
	private List<ApiHeader> headers = new ArrayList<>();
	private ApiBody body;
	private ApiAuth auth;
	private ApiExample example;
	private List<ApiResponse> responses = new ArrayList<>();
	private ApiProtocol protocol = ApiProtocol.REST;
	private String graphQlOperationType;
	private boolean urlResolved = true;

	/**
	 * Returns the folder.
	 */
	public String getFolder() {
		return folder;
	}

	/**
	 * Sets the folder.
	 */
	public void setFolder(String folder) {
		this.folder = folder;
	}

	/**
	 * Returns the display name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the display name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the method name.
	 */
	public String getMethodName() {
		return methodName;
	}

	/**
	 * Sets the method name.
	 */
	public void setMethodName(String methodName) {
		this.methodName = methodName;
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
	 * Returns the method.
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * Sets the method.
	 */
	public void setMethod(String method) {
		this.method = method;
	}

	/**
	 * Returns the allowed methods.
	 */
	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	/**
	 * Sets the allowed methods.
	 */
	public void setAllowedMethods(List<String> allowedMethods) {
		this.allowedMethods = allowedMethods == null ? new ArrayList<>() : allowedMethods;
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

	/**
	 * Returns the auth.
	 */
	public ApiAuth getAuth() {
		return auth;
	}

	/**
	 * Sets the auth.
	 */
	public void setAuth(ApiAuth auth) {
		this.auth = auth;
	}

	/**
	 * Returns the example.
	 */
	public ApiExample getExample() {
		return example;
	}

	/**
	 * Sets the example.
	 */
	public void setExample(ApiExample example) {
		this.example = example;
	}

	/**
	 * Returns the responses.
	 */
	public List<ApiResponse> getResponses() {
		return responses;
	}

	/**
	 * Sets the responses.
	 */
	public void setResponses(List<ApiResponse> responses) {
		this.responses = responses == null ? new ArrayList<>() : responses;
	}

	/**
	 * Returns the protocol.
	 */
	public ApiProtocol getProtocol() {
		return protocol;
	}

	/**
	 * Sets the protocol.
	 */
	public void setProtocol(ApiProtocol protocol) {
		this.protocol = protocol;
	}

	/**
	 * Returns the graph ql operation type.
	 */
	public String getGraphQlOperationType() {
		return graphQlOperationType;
	}

	/**
	 * Sets the graph ql operation type.
	 */
	public void setGraphQlOperationType(String graphQlOperationType) {
		this.graphQlOperationType = graphQlOperationType;
	}

	/**
	 * Returns whether url resolved is enabled or true.
	 */
	public boolean isUrlResolved() {
		return urlResolved;
	}

	/**
	 * Sets the url resolved.
	 */
	public void setUrlResolved(boolean urlResolved) {
		this.urlResolved = urlResolved;
	}
}
