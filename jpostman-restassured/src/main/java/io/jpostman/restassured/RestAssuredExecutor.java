package io.jpostman.restassured;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Authentication;
import io.jpostman.Request;
import io.jpostman.RequestProvider;
import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Optional REST Assured adapter for executing JPostman requests.
 *
 * <p>
 * This class lives in the {@code jpostman-restassured} module, so REST Assured
 * remains optional for core users.
 * </p>
 *
 * <p>
 * Two usage styles are supported.
 * </p>
 *
 * <h2>Native REST Assured style</h2>
 *
 * <pre>
 * Response response = RestAssuredExecutor.execute(request, given()).then().statusCode(200).extract().response();
 * </pre>
 *
 * <pre>
 * RestAssuredExecutor.apply(request, given()).auth().oauth2(token).get(request.toUrl());
 * </pre>
 *
 * <h2>JPostman executor style</h2>
 *
 * <pre>
 * ApiResponse response = RestAssuredExecutor.execute(request);
 * </pre>
 *
 * <pre>
 * ApiResponse response = RestAssuredExecutor.apply(request).auth().oauth2(token).response();
 * </pre>
 */
public final class RestAssuredExecutor implements ApiExecutor {

	private Request request;
	private final CookieFilter cookieFilter;

	private final Map<String, String> runtimeHeaders = new LinkedHashMap<>();
	private final Authentication.State authState = new Authentication.State();

	/**
	 * Creates a reusable REST Assured executor with a shared cookie filter.
	 *
	 * <p>
	 * Use this when multiple requests should share the same HTTP session, including
	 * cookies set by earlier responses.
	 * </p>
	 *
	 * @return reusable REST Assured executor
	 */
	public static RestAssuredExecutor create() {
		return new RestAssuredExecutor(null, new CookieFilter());
	}

	/**
	 * Creates a REST Assured executor with a default request specification.
	 *
	 * @param request request associated with this executor
	 */
	private RestAssuredExecutor(Request request) {
		this(request, null);
	}

	/**
	 * Creates a REST Assured executor with a caller-provided request specification.
	 *
	 * @param request       request associated with this executor
	 * @param specification REST Assured request specification
	 */
	private RestAssuredExecutor(Request request, CookieFilter cookieFilter) {
		this.request = request;
		this.cookieFilter = cookieFilter;
	}

	/**
	 * Sets the request to execute.
	 *
	 * <p>
	 * This is useful when reusing the same executor for multiple requests that
	 * should share the same REST Assured cookie filter.
	 * </p>
	 *
	 * @param request request to execute
	 * @return this executor
	 */
	public RestAssuredExecutor setRequest(Request request) {
		this.request = request;
		return this;
	}

	/**
	 * Creates a fluent JPostman-style REST Assured executor for the supplied
	 * request.
	 *
	 * @param request request to execute
	 * @return executor associated with the request
	 */
	public static RestAssuredExecutor apply(Request request) {
		return new RestAssuredExecutor(request);
	}

	/**
	 * Applies JPostman request headers and body settings to an existing REST
	 * Assured specification.
	 *
	 * <p>
	 * This method intentionally returns {@link RequestSpecification} so callers can
	 * continue using native REST Assured features such as
	 * {@code .auth().oauth2(token).get(url)}.
	 * </p>
	 *
	 * @param request       JPostman request
	 * @param specification REST Assured request specification
	 * @return updated REST Assured request specification
	 */
	public static RequestSpecification apply(Request request, RequestSpecification specification) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}

		RequestSpecification spec = specification == null ? RestAssured.given() : specification;

		request.getHeader().getParams().forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null) {
				spec.header(name, value);
			}
		});

		if (request.getBody() != null && !request.getBody().isEmpty()
				&& "json".equalsIgnoreCase(request.getBody().getLanguage())
				&& !hasHeaderIgnoreCase(request.getHeader().getParams(), "Content-Type")) {
			spec.contentType(ContentType.JSON);
		}

		if (request.getBody() != null && !request.getBody().isEmpty()) {
			spec.body(request.getBody().getRaw());
		}

		return spec;
	}

	/**
	 * Executes a request provided by a {@link RequestProvider}.
	 *
	 * @param requestProvider request provider
	 * @return API response
	 */
	public static ApiResponse execute(RequestProvider requestProvider) {
		return execute(requestProvider.build());
	}

	/**
	 * Executes a request once using a default REST Assured specification and
	 * returns a framework-neutral response.
	 *
	 * @param request request to execute
	 * @return framework-neutral response
	 */
	public static ApiResponse execute(Request request) {
		return new RestAssuredExecutor(request).response();
	}

	/**
	 * Executes a JPostman request using REST Assured and returns the native REST
	 * Assured {@link Response}.
	 *
	 * @param request       JPostman request
	 * @param specification REST Assured request specification
	 * @return native REST Assured response
	 */
	public static Response execute(Request request, RequestSpecification specification) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}

		RequestSpecification spec = apply(request, specification);
		return executeByMethod(request, spec);
	}

	/**
	 * Starts fluent authentication/header configuration.
	 *
	 * @return shared authentication builder
	 */
	public Authentication auth() {
		return new Authentication(this, runtimeHeaders, authState);
	}

	/**
	 * Returns the REST Assured cookie filter used by shared-session executors.
	 *
	 * <p>
	 * This is mainly useful for tests or advanced users who need to inspect, reuse,
	 * or clear cookies captured during execution. It returns {@code null} when this
	 * executor was not created by {@link #create()}.
	 * </p>
	 *
	 * @return cookie filter, or {@code null}
	 */
	public CookieFilter getCookieFilter() {
		return cookieFilter;
	}

	/**
	 * Adds or overrides a header for the next executed request.
	 *
	 * <p>
	 * Runtime headers are applied after headers parsed from the Postman collection,
	 * so they can override collection headers.
	 * </p>
	 *
	 * @param name  header name
	 * @param value header value
	 * @return this executor
	 */
	public RestAssuredExecutor header(String name, String value) {
		if (name != null && !name.isBlank() && value != null) {
			runtimeHeaders.put(name, value);
		}
		return this;
	}

	/**
	 * Executes the request associated with this executor and returns a
	 * framework-neutral response.
	 *
	 * <p>
	 * Runtime headers and auth state are cleared after each execution, even when
	 * execution fails.
	 * </p>
	 *
	 * @return framework-neutral response
	 */
	@Override
	public ApiResponse response() {
		if (request == null) {
			clearRuntimeState();
			throw new IllegalArgumentException("request must not be null");
		}

		try {
			RequestSpecification spec = applyForResponse(request);
			Response response = executeByMethod(request, spec);

			byte[] bytes = response.asByteArray() == null ? new byte[0] : response.asByteArray();
			String body = response.asString() == null ? "" : response.asString();

			Map<String, List<String>> headers = new LinkedHashMap<>();
			response.getHeaders().forEach(header -> headers.put(header.getName(), List.of(header.getValue())));

			return new ApiResponse(response.statusCode(), body, bytes, headers);
		} finally {
			clearRuntimeState();
		}
	}

	/**
	 * Applies request headers, runtime headers, content type, and body to a REST
	 * Assured specification for framework-neutral execution.
	 */
	private RequestSpecification applyForResponse(Request request) {
		// Build a fresh mutable specification for every execution so headers, body,
		// and content type from one request do not leak into the next request. The
		// shared CookieFilter is reapplied when this executor was created for session
		// reuse.
		RequestSpecification spec = cookieFilter == null ? RestAssured.given()
				: RestAssured.given().filter(cookieFilter);

		request.getHeader().getParams().forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null && authState.shouldApplyRequestHeader(name)
					&& !runtimeHeaders.containsKey(name)) {
				spec.header(name, value);
			}
		});

		runtimeHeaders.forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null) {
				spec.header(name, value);
			}
		});

		if (request.getBody() != null && !request.getBody().isEmpty()
				&& "json".equalsIgnoreCase(request.getBody().getLanguage())
				&& !hasHeaderIgnoreCase(request.getHeader().getParams(), "Content-Type")
				&& !hasHeaderIgnoreCase(runtimeHeaders, "Content-Type")) {
			spec.contentType(ContentType.JSON);
		}

		if (request.getBody() != null && !request.getBody().isEmpty()) {
			spec.body(request.getBody().getRaw());
		}

		return spec;
	}

	private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String headerName) {
		return headers != null && headers.keySet().stream().anyMatch(headerName::equalsIgnoreCase);
	}

	/**
	 * Executes the request using the HTTP method from the JPostman request.
	 */
	private static Response executeByMethod(Request request, RequestSpecification specification) {
		String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
		String url = request.toUrl();

		switch (method) {
		case "GET":
			return specification.get(url);
		case "POST":
			return specification.post(url);
		case "PUT":
			return specification.put(url);
		case "PATCH":
			return specification.patch(url);
		case "DELETE":
			return specification.delete(url);
		case "HEAD":
			return specification.head(url);
		case "OPTIONS":
			return specification.options(url);
		default:
			throw new IllegalArgumentException("Unsupported HTTP method: " + method);
		}
	}

	/**
	 * Clears runtime headers and authentication state after execution.
	 */
	private void clearRuntimeState() {
		runtimeHeaders.clear();
		authState.clear();
	}
}
