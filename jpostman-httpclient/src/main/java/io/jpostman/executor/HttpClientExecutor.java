package io.jpostman.executor;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Authentication;
import io.jpostman.Request;

/**
 * Java 11 {@link HttpClient} adapter for executing JPostman requests.
 *
 * <p>
 * This class lives in the {@code jpostman-httpclient} module and uses only the
 * JDK HTTP client. It is useful as a lightweight executor and as a reference
 * implementation for other adapters.
 * </p>
 *
 * <p>
 * Supported one-shot execution:
 * </p>
 *
 * <pre>
 * ApiResponse response = HttpClientExecutor.execute(request);
 * </pre>
 *
 * <p>
 * Supported fluent execution:
 * </p>
 *
 * <pre>
 * ApiResponse response = HttpClientExecutor
 *         .apply(request)
 *         .auth()
 *         .oauth2(token)
 *         .response();
 * </pre>
 *
 * <p>
 * Supported shared-session execution:
 * </p>
 *
 * <pre>
 * HttpClientExecutor executor = HttpClientExecutor.create();
 *
 * ApiResponse login = executor.setRequest(loginRequest).response();
 * ApiResponse user = executor.setRequest(userRequest).response();
 * </pre>
 *
 * <p>
 * Shared-session execution reuses the same {@link HttpClient} and
 * {@link CookieManager}, so cookies set by earlier responses can be reused by
 * later requests.
 * </p>
 */
public class HttpClientExecutor implements ApiExecutor {

	private Request request;

	private final HttpClient client;
	private final Duration timeout;
	private final CookieManager cookieManager;

	private final Map<String, String> runtimeHeaders = new LinkedHashMap<>();
	private final Authentication.State authState = new Authentication.State();

	/**
	 * Creates an executor with a default Java {@link HttpClient} and a 30-second
	 * request timeout.
	 *
	 * <p>
	 * This constructor is intended for one request. Use {@link #create()} when
	 * multiple requests should share cookies/session state.
	 * </p>
	 *
	 * @param request request associated with this executor
	 */
	public HttpClientExecutor(Request request) {
		this(request, HttpClient.newHttpClient(), Duration.ofSeconds(30), null);
	}

	/**
	 * Creates an executor with a caller-provided HTTP client and timeout.
	 *
	 * <p>
	 * If the provided {@link HttpClient} was built with a cookie handler, that
	 * cookie handler controls whether cookies are shared across requests.
	 * </p>
	 *
	 * @param request request associated with this executor
	 * @param client HTTP client used for execution
	 * @param timeout request timeout; defaults to 30 seconds when {@code null}
	 * @throws IllegalArgumentException if {@code client} is {@code null}
	 */
	public HttpClientExecutor(Request request, HttpClient client, Duration timeout) {
		this(request, client, timeout, null);
	}

	/**
	 * Internal constructor.
	 *
	 * @param request request associated with this executor
	 * @param client HTTP client used for execution
	 * @param timeout request timeout; defaults to 30 seconds when {@code null}
	 * @param cookieManager cookie manager used by {@link #create()}, or {@code null}
	 */
	private HttpClientExecutor(Request request, HttpClient client, Duration timeout, CookieManager cookieManager) {
		if (client == null) {
			throw new IllegalArgumentException("client must not be null");
		}

		this.request = request;
		this.client = client;
		this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
		this.cookieManager = cookieManager;
	}

	/**
	 * Creates a reusable executor with a cookie-enabled Java {@link HttpClient}.
	 *
	 * <p>
	 * Use this when multiple requests should share the same HTTP session, including
	 * cookies set by earlier responses.
	 * </p>
	 *
	 * @return reusable HTTP client executor
	 */
	public static HttpClientExecutor create() {
		CookieManager cookieManager = new CookieManager();
		cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

		HttpClient client = HttpClient.newBuilder()
				.cookieHandler(cookieManager)
				.build();

		return new HttpClientExecutor(null, client, Duration.ofSeconds(30), cookieManager);
	}

	/**
	 * Sets the request to execute.
	 *
	 * <p>
	 * This is useful when reusing the same executor for multiple requests that
	 * should share the same Java {@link HttpClient} and cookie store.
	 * </p>
	 *
	 * @param request request to execute
	 * @return this executor
	 */
	public HttpClientExecutor setRequest(Request request) {
		this.request = request;
		return this;
	}

	/**
	 * Creates a fluent executor for the supplied request.
	 *
	 * @param request request to execute
	 * @return executor associated with the request
	 */
	public static HttpClientExecutor apply(Request request) {
		return new HttpClientExecutor(request);
	}

	/**
	 * Executes a request once with a default Java {@link HttpClient}.
	 *
	 * <p>
	 * This method is convenient for independent requests. It does not share cookies
	 * with other calls. Use {@link #create()} and {@link #setRequest(Request)} when
	 * cookies/session state should be reused.
	 * </p>
	 *
	 * @param request request to execute
	 * @return framework-neutral response
	 */
	public static ApiResponse execute(Request request) {
		return new HttpClientExecutor(request).response();
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
	 * Adds or overrides a header for the next executed request.
	 *
	 * <p>
	 * Runtime headers are applied after headers parsed from the Postman collection,
	 * so they can override collection headers.
	 * </p>
	 *
	 * @param name header name
	 * @param value header value
	 * @return this executor
	 */
	public HttpClientExecutor header(String name, String value) {
		if (name != null && !name.isBlank() && value != null) {
			runtimeHeaders.put(name, value);
		}
		return this;
	}

	/**
	 * Executes the request currently associated with this executor.
	 *
	 * <p>
	 * Runtime headers and auth state are cleared after each execution, even when
	 * execution fails. The underlying {@link HttpClient} remains reusable.
	 * </p>
	 *
	 * @return framework-neutral response
	 * @throws IllegalArgumentException if no request has been set
	 */
	@Override
	public ApiResponse response() {
		if (request == null) {
			clearRuntimeState();
			throw new IllegalArgumentException("request must not be null");
		}

		try {
			HttpRequest httpRequest = toHttpRequest(request);
			HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

			byte[] bytes = response.body() == null ? new byte[0] : response.body();
			String body = new String(bytes, StandardCharsets.UTF_8);

			return new ApiResponse(response.statusCode(), body, bytes, response.headers().map());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to execute request: " + request, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Request execution interrupted: " + request, e);
		} finally {
			clearRuntimeState();
		}
	}

	/**
	 * Converts a JPostman request into a Java {@link HttpRequest}.
	 *
	 * @param request JPostman request
	 * @return Java HTTP request
	 */
	private HttpRequest toHttpRequest(Request request) {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(toSafeUri(request.toUrl()))
				.timeout(timeout);

		applyRequestHeaders(request, builder);
		applyRuntimeHeaders(builder);
		applyContentTypeIfNeeded(request, builder);
		applyMethodAndBody(request, builder);

		return builder.build();
	}

	/**
	 * Applies headers parsed from the Postman request.
	 *
	 * <p>
	 * When {@code auth().none()} is used, Authorization from the original request
	 * is skipped.
	 * </p>
	 */
	private void applyRequestHeaders(Request request, HttpRequest.Builder builder) {
		request.getHeader().getParams().forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null && 
					authState.shouldApplyRequestHeader(name) && !runtimeHeaders.containsKey(name)) {
				builder.header(name, value);
			}
		});
	}

	/**
	 * Applies runtime headers configured through {@link #header(String, String)} or
	 * {@link #auth()}.
	 *
	 * <p>
	 * {@link HttpRequest.Builder#setHeader(String, String)} is used so runtime
	 * values replace duplicate request headers, especially {@code Authorization}.
	 * </p>
	 */
	private void applyRuntimeHeaders(HttpRequest.Builder builder) {
		runtimeHeaders.forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null) {
				builder.setHeader(name, value);
			}
		});
	}

	/**
	 * Adds JSON Content-Type automatically when the body is JSON and neither the
	 * Postman request nor runtime configuration already supplied one.
	 */
	private void applyContentTypeIfNeeded(Request request, HttpRequest.Builder builder) {
		if (request.getBody() != null
				&& !request.getBody().isEmpty()
				&& "json".equalsIgnoreCase(request.getBody().getLanguage())
				&& !hasHeaderIgnoreCase(request.getHeader().getParams(), "Content-Type")
				&& !hasHeaderIgnoreCase(runtimeHeaders, "Content-Type")) {
			builder.header("Content-Type", "application/json");
		}
	}

	private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String headerName) {
		return headers != null && headers.keySet().stream().anyMatch(headerName::equalsIgnoreCase);
	}

	/**
	 * Applies the HTTP method and optional body publisher.
	 */
	private void applyMethodAndBody(Request request, HttpRequest.Builder builder) {
		String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
		String rawBody = request.getBody() == null ? "" : request.getBody().getRaw();

		boolean hasBody = rawBody != null && !rawBody.isEmpty();

		HttpRequest.BodyPublisher bodyPublisher = hasBody
				? HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8)
				: HttpRequest.BodyPublishers.noBody();

		switch (method) {
		case "GET":
			builder.GET();
			break;
		case "DELETE":
			if (hasBody) {
				builder.method(method, bodyPublisher);
			} else {
				builder.DELETE();
			}
			break;
		case "POST":
		case "PUT":
		case "PATCH":
		case "HEAD":
		case "OPTIONS":
			builder.method(method, bodyPublisher);
			break;
		default:
			throw new IllegalArgumentException("Unsupported HTTP method: " + method);
		}
	}

	/**
	 * Converts the request URL string into a {@link URI}.
	 *
	 * <p>
	 * The current implementation encodes spaces as {@code %20}. Full query
	 * component encoding should eventually live in the URL/query builder so every
	 * executor receives an already-safe URL.
	 * </p>
	 *
	 * @param rawUrl request URL
	 * @return URI usable by Java HttpClient
	 */
	private URI toSafeUri(String rawUrl) {
		if (rawUrl == null || rawUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("URL must not be null or empty");
		}

		return URI.create(rawUrl.replace(" ", "%20"));
	}

	/**
	 * Returns the cookie manager used by shared-session executors.
	 *
	 * <p>
	 * This is mainly useful for tests or advanced users who need to inspect or
	 * clear cookies. It returns {@code null} when this executor was not created by
	 * {@link #create()}.
	 * </p>
	 *
	 * @return cookie manager, or {@code null}
	 */
	public CookieManager getCookieManager() {
		return cookieManager;
	}

	/**
	 * Clears runtime headers and authentication state after execution.
	 */
	private void clearRuntimeState() {
		runtimeHeaders.clear();
		authState.clear();
	}
}
