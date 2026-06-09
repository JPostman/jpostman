package io.jpostman.unirest;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Authentication;
import io.jpostman.Request;
import io.jpostman.RequestProvider;

/**
 * Optional Unirest adapter for executing JPostman requests.
 *
 * <p>
 * This class lives in the {@code jpostman-unirest} module, so Unirest remains
 * optional for {@code jpostman-core} users.
 * </p>
 *
 * <p>
 * Supported one-shot execution:
 * </p>
 *
 * <pre>
 * ApiResponse response = UnirestExecutor.execute(request);
 * </pre>
 *
 * <p>
 * Supported fluent execution:
 * </p>
 *
 * <pre>
 * ApiResponse response = UnirestExecutor.apply(request).auth().oauth2(token).response();
 * </pre>
 *
 * <p>
 * Supported shared-session execution:
 * </p>
 *
 * <pre>
 * UnirestExecutor executor = UnirestExecutor.create();
 *
 * ApiResponse login = executor.setRequest(loginRequest).response();
 * ApiResponse user = executor.setRequest(userRequest).response();
 * </pre>
 *
 * <p>
 * Shared-session execution uses an Apache {@link CookieStore}, so cookies set
 * by earlier responses can be reused by later requests.
 * </p>
 *
 * <p>
 * Note: {@code com.mashape.unirest:unirest-java:1.4.9} uses static/global
 * Unirest configuration. {@link #create()} configures Unirest's global HTTP
 * client, so shared-session execution should not be mixed with unrelated
 * parallel Unirest tests that expect a different global client.
 * </p>
 */
public final class UnirestExecutor implements ApiExecutor {

	private Request request;

	private final Map<String, String> runtimeHeaders = new LinkedHashMap<>();
	private final Authentication.State authState = new Authentication.State();

	private final CookieStore cookieStore;

	/**
	 * Creates a Unirest executor for the supplied request.
	 *
	 * @param request request associated with this executor
	 */
	private UnirestExecutor(Request request) {
		this(request, null);
	}

	/**
	 * Creates a Unirest executor with an optional cookie store.
	 *
	 * @param request     request associated with this executor
	 * @param cookieStore cookie store used for shared-session execution, or
	 *                    {@code null}
	 */
	private UnirestExecutor(Request request, CookieStore cookieStore) {
		this.request = request;
		this.cookieStore = cookieStore;
	}

	/**
	 * Creates a reusable Unirest executor with a cookie-enabled Apache HTTP client.
	 *
	 * <p>
	 * Use this when multiple requests should share the same HTTP session, including
	 * cookies set by earlier responses.
	 * </p>
	 *
	 * <p>
	 * Unirest 1.4.9 stores the HTTP client as global/static configuration, so this
	 * method updates the global Unirest HTTP client.
	 * </p>
	 *
	 * <p>
	 * The Apache HTTP client is configured with {@link CookieSpecs#STANDARD} so
	 * modern {@code Set-Cookie} headers are accepted and stored in the shared
	 * cookie store.
	 * </p>
	 *
	 * @return reusable Unirest executor
	 */
	public static UnirestExecutor create() {
		CookieStore cookieStore = new BasicCookieStore();

		RequestConfig requestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();

		CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore)
				.setDefaultRequestConfig(requestConfig).build();

		Unirest.setHttpClient(client);

		return new UnirestExecutor(null, cookieStore);
	}

	/**
	 * Sets the request to execute.
	 *
	 * <p>
	 * This is useful when reusing the same executor for multiple requests that
	 * should share the same Unirest cookie store.
	 * </p>
	 *
	 * @param request request to execute
	 * @return this executor
	 */
	public UnirestExecutor setRequest(Request request) {
		this.request = request;
		return this;
	}

	/**
	 * Builds a request from the supplied {@link RequestProvider} and delegates to
	 * the request-based method.
	 *
	 * @param requestProvider provider used to build the request
	 * @return executor associated with the request
	 */
	public static UnirestExecutor apply(RequestProvider requestProvider) {
		return apply(requestProvider.build());
	}

	/**
	 * Creates a fluent Unirest executor for the supplied request.
	 *
	 * @param request request to execute
	 * @return executor associated with the request
	 */
	public static UnirestExecutor apply(Request request) {
		return new UnirestExecutor(request);
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
	 * Executes a request once using Unirest.
	 *
	 * <p>
	 * This method is convenient for independent requests. Use {@link #create()} and
	 * {@link #setRequest(Request)} when cookies/session state should be reused.
	 * </p>
	 *
	 * @param request request to execute
	 * @return framework-neutral response
	 */
	public static ApiResponse execute(Request request) {
		return new UnirestExecutor(request).response();
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
	 * so they can override collection headers such as {@code Authorization}.
	 * </p>
	 *
	 * @param name  header name
	 * @param value header value
	 * @return this executor
	 */
	public UnirestExecutor header(String name, String value) {
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
	 * execution fails. The underlying Unirest global HTTP client remains reusable.
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
			HttpResponse<String> response = executeWithUnirest(request);

			String body = response.getBody() == null ? "" : response.getBody();
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

			Map<String, List<String>> headers = new LinkedHashMap<>();
			response.getHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));

			return new ApiResponse(response.getStatus(), body, bytes, headers);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to execute request with Unirest: " + request, e);
		} finally {
			clearRuntimeState();
		}
	}

	/**
	 * Converts and executes a JPostman request with Unirest.
	 */
	private HttpResponse<String> executeWithUnirest(Request request) throws Exception {
		String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
		String url = toSafeUrl(request.toUrl());

		switch (method) {
		case "GET":
			return applyHeaders(Unirest.get(url), request).asString();
		case "DELETE":
			if (hasBody(request)) {
				return applyBody(applyHeaders(Unirest.delete(url), request), request).asString();
			}
			return applyHeaders(Unirest.delete(url), request).asString();
		case "POST":
			return applyBody(applyHeaders(Unirest.post(url), request), request).asString();
		case "PUT":
			return applyBody(applyHeaders(Unirest.put(url), request), request).asString();
		case "PATCH":
			return applyBody(applyHeaders(Unirest.patch(url), request), request).asString();
		case "HEAD":
			return applyHeaders(Unirest.head(url), request).asString();
		case "OPTIONS":
			return applyHeaders(Unirest.options(url), request).asString();
		default:
			throw new IllegalArgumentException("Unsupported HTTP method: " + method);
		}
	}

	/**
	 * Applies headers from the Postman request and runtime executor configuration.
	 */
	private <T extends HttpRequest> T applyHeaders(T httpRequest, Request request) {
		request.getHeader().getParams().forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null && authState.shouldApplyRequestHeader(name)
					&& !runtimeHeaders.containsKey(name)) {
				httpRequest.header(name, value);
			}
		});

		runtimeHeaders.forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null) {
				httpRequest.header(name, value);
			}
		});

		if (request.getBody() != null && !request.getBody().isEmpty()
				&& "json".equalsIgnoreCase(request.getBody().getLanguage())
				&& !request.getHeader().getParams().containsKey("Content-Type")) {
			httpRequest.header("Content-Type", "application/json");
		}

		return httpRequest;
	}

	/**
	 * Applies request body data when present.
	 */
	private HttpRequestWithBody applyBody(HttpRequestWithBody httpRequest, Request request) {
		if (hasBody(request)) {
			httpRequest.body(request.getBody().getRaw());
		}
		return httpRequest;
	}

	/**
	 * Returns whether the request has a non-empty body.
	 */
	private boolean hasBody(Request request) {
		return request.getBody() != null && request.getBody().getRaw() != null && !request.getBody().getRaw().isEmpty();
	}

	/**
	 * Converts a request URL to a value accepted by Unirest.
	 *
	 * <p>
	 * The current implementation encodes spaces as {@code %20}. Full query
	 * component encoding should eventually live in the URL/query builder so every
	 * executor receives an already-safe URL.
	 * </p>
	 */
	private String toSafeUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("URL must not be null or empty");
		}
		return rawUrl.replace(" ", "%20");
	}

	/**
	 * Returns the cookie store used by shared-session executors.
	 *
	 * <p>
	 * This is mainly useful for tests or advanced users who need to inspect or
	 * clear cookies. It returns {@code null} when this executor was not created by
	 * {@link #create()}.
	 * </p>
	 *
	 * @return cookie store, or {@code null}
	 */
	public CookieStore getCookieStore() {
		return cookieStore;
	}

	/**
	 * Clears runtime headers and authentication state after execution.
	 */
	private void clearRuntimeState() {
		runtimeHeaders.clear();
		authState.clear();
	}
}
