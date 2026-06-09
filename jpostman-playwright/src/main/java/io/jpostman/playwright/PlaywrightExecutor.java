package io.jpostman.playwright;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Authentication;
import io.jpostman.Request;
import io.jpostman.RequestProvider;

/**
 * This executor uses Playwright's API testing client only. It does not launch a
 * browser, page, or browser context.
 *
 * <p>
 * Supported one-shot execution:
 * </p>
 *
 * <pre>
 * ApiResponse response = PlaywrightExecutor.execute(request);
 * </pre>
 *
 * <p>
 * Supported fluent execution:
 * </p>
 *
 * <pre>
 * ApiResponse response = PlaywrightExecutor.apply(request).auth().oauth2(token).response();
 * </pre>
 *
 * <p>
 * Supported shared-session execution:
 * </p>
 *
 * <pre>
 * try (PlaywrightExecutor executor = PlaywrightExecutor.create()) {
 * 	ApiResponse login = executor.setRequest(loginRequest).response();
 * 	ApiResponse user = executor.setRequest(userRequest).response();
 * }
 * </pre>
 *
 * <p>
 * Shared-session execution reuses the same Playwright
 * {@link APIRequestContext}, so cookies/session state from one response can be
 * reused by later requests.
 * </p>
 */
public final class PlaywrightExecutor implements ApiExecutor, AutoCloseable {

	private final Playwright playwright;
	private final APIRequestContext context;
	private final boolean ownsPlaywright;

	private Request request;
	private boolean closed;

	private final Map<String, String> runtimeHeaders = new LinkedHashMap<>();
	private final Authentication.State authState = new Authentication.State();

	/**
	 * Creates a Playwright executor.
	 *
	 * <p>
	 * The executor always owns and disposes the {@link APIRequestContext}. It only
	 * closes the {@link Playwright} instance when {@code ownsPlaywright} is true.
	 * </p>
	 *
	 * @param playwright     Playwright instance used to create an API request
	 *                       context
	 * @param context        existing API request context, or {@code null} to create
	 *                       one
	 * @param ownsPlaywright whether this executor should close the Playwright
	 *                       instance
	 * @throws IllegalArgumentException if {@code playwright} is {@code null}
	 */
	public PlaywrightExecutor(Playwright playwright, APIRequestContext context, boolean ownsPlaywright) {
		if (playwright == null) {
			throw new IllegalArgumentException("playwright must not be null");
		}

		this.playwright = playwright;
		this.context = context == null ? playwright.request().newContext() : context;
		this.ownsPlaywright = ownsPlaywright;
	}

	/**
	 * Creates a reusable Playwright executor with its own Playwright instance and
	 * API request context.
	 *
	 * <p>
	 * Use this when multiple requests should share the same Playwright API session,
	 * including cookies set by earlier responses.
	 * </p>
	 *
	 * @return reusable Playwright executor
	 */
	public static PlaywrightExecutor create() {
		return new PlaywrightExecutor(Playwright.create(), null, true);
	}

	/**
	 * Sets the request to execute.
	 *
	 * <p>
	 * This is useful when reusing the same executor for multiple requests that
	 * should share the same Playwright API request context.
	 * </p>
	 *
	 * @param request request to execute
	 * @return this executor
	 */
	public PlaywrightExecutor setRequest(Request request) {
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
	public static PlaywrightExecutor apply(RequestProvider requestProvider) {
		return apply(requestProvider.build());
	}

	/**
	 * Creates a fluent Playwright executor for one request.
	 *
	 * <p>
	 * The returned executor owns its Playwright resources. Callers should either
	 * use {@link #execute(Request)} for one-shot execution or close the returned
	 * executor manually/with try-with-resources.
	 * </p>
	 *
	 * @param request request to execute
	 * @return configured Playwright executor
	 */
	public static PlaywrightExecutor apply(Request request) {
		return PlaywrightExecutor.create().setRequest(request);
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
	 * Executes a request once using a temporary Playwright executor.
	 *
	 * <p>
	 * This method creates and closes Playwright resources automatically.
	 * </p>
	 *
	 * @param request request to execute
	 * @return framework-neutral response
	 */
	public static ApiResponse execute(Request request) {
		try (PlaywrightExecutor executor = PlaywrightExecutor.create().setRequest(request)) {
			return executor.response();
		}
	}

	/**
	 * Starts REST-Assured-style authentication configuration.
	 *
	 * @return authentication specification
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
	 * @param name  header name
	 * @param value header value
	 * @return this executor
	 */
	public PlaywrightExecutor header(String name, String value) {
		if (name != null && !name.isBlank() && value != null) {
			runtimeHeaders.put(name, value);
		}
		return this;
	}

	/**
	 * Executes the request currently associated with this executor.
	 *
	 * <p>
	 * Runtime headers configured through {@link #header(String, String)} or
	 * {@link #auth()} are applied only to this execution and cleared afterward.
	 * </p>
	 *
	 * <p>
	 * This method does not close the executor. Use {@link #execute(Request)} for
	 * one-shot automatic cleanup, or use try-with-resources when reusing the
	 * executor.
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
			RequestOptions options = toRequestOptions(request);
			com.microsoft.playwright.APIResponse response = context.fetch(request.toUrl(), options);

			byte[] bytes = response.body() == null ? new byte[0] : response.body();
			String body = new String(bytes, StandardCharsets.UTF_8);

			Map<String, List<String>> headers = response.headers().entrySet().stream().collect(Collectors
					.toMap(Map.Entry::getKey, e -> List.of(e.getValue()), (left, right) -> right, LinkedHashMap::new));

			return new ApiResponse(response.status(), body, bytes, headers);
		} finally {
			clearRuntimeState();
		}
	}

	/**
	 * Converts a JPostman request into Playwright request options.
	 *
	 * @param request JPostman request
	 * @return Playwright request options
	 */
	private RequestOptions toRequestOptions(Request request) {
		String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
		RequestOptions options = RequestOptions.create().setMethod(method);

		applyRequestHeaders(request, options);
		applyRuntimeHeaders(options);
		applyContentTypeIfNeeded(request, options);
		applyBodyIfNeeded(request, options);

		return options;
	}

	/**
	 * Applies headers parsed from the Postman request.
	 *
	 * <p>
	 * If {@code auth().none()} was used, an existing Authorization header from the
	 * original request is intentionally skipped.
	 * </p>
	 */
	private void applyRequestHeaders(Request request, RequestOptions options) {
		if (request.getHeader() == null || request.getHeader().getParams() == null) {
			return;
		}

		request.getHeader().getParams().forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null && authState.shouldApplyRequestHeader(name)
					&& !runtimeHeaders.containsKey(name)) {
				options.setHeader(name, value);
			}
		});
	}

	/**
	 * Applies runtime headers configured through this executor.
	 *
	 * <p>
	 * Runtime headers are applied after request headers, so they override headers
	 * parsed from the Postman collection.
	 * </p>
	 */
	private void applyRuntimeHeaders(RequestOptions options) {
		runtimeHeaders.forEach((name, value) -> {
			if (name != null && !name.isBlank() && value != null) {
				options.setHeader(name, value);
			}
		});
	}

	/**
	 * Adds JSON Content-Type automatically when the request body is JSON and the
	 * Postman request did not already define Content-Type.
	 */
	private void applyContentTypeIfNeeded(Request request, RequestOptions options) {
		if (request.getBody() != null && !request.getBody().isEmpty()
				&& "json".equalsIgnoreCase(request.getBody().getLanguage())
				&& !request.getHeader().getParams().containsKey("Content-Type")) {
			options.setHeader("Content-Type", "application/json");
		}
	}

	/**
	 * Applies request body data when present.
	 */
	private void applyBodyIfNeeded(Request request, RequestOptions options) {
		if (request.getBody() != null && !request.getBody().isEmpty()) {
			options.setData(request.getBody().getRaw());
		}
	}

	/**
	 * Clears runtime-only headers and authentication state after each execution.
	 */
	private void clearRuntimeState() {
		runtimeHeaders.clear();
		authState.clear();
	}

	/**
	 * Closes Playwright resources owned by this executor.
	 *
	 * <p>
	 * The API request context is always disposed by this executor. The Playwright
	 * instance is closed only when this executor created it.
	 * </p>
	 */
	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;

		if (context != null) {
			context.dispose();
		}

		if (ownsPlaywright && playwright != null) {
			playwright.close();
		}
	}
}