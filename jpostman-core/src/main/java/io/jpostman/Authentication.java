package io.jpostman;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * REST-Assured-style authentication helper shared by JPostman executors.
 *
 * <p>
 * This class does not send a request by itself. Instead, it writes runtime
 * authentication headers into a header map owned by an {@link ApiExecutor}. The
 * executor applies those headers when {@link ApiExecutor#response()} is called.
 * </p>
 *
 * <p>
 * Supported common authentication styles:
 * </p>
 *
 * <pre>
 * executor.auth().oauth2(token).response();
 * executor.auth().bearer(token).response();
 * executor.auth().basic(username, password).response();
 * executor.auth().apiKey("X-API-Key", key).response();
 * executor.auth().none().response();
 * </pre>
 *
 * <p>
 * The method names intentionally follow common REST Assured naming so tests can
 * read similarly across different executor modules.
 * </p>
 */
public final class Authentication {

	private final ApiExecutor executor;
	private final Map<String, String> runtimeHeaders;
	private final State state;

	/**
	 * Creates an authentication helper for an executor.
	 *
	 * @param executor       executor returned after authentication configuration
	 * @param runtimeHeaders mutable runtime header map owned by the executor
	 * @param state          shared authentication state owned by the executor
	 * @throws IllegalArgumentException if any argument is {@code null}
	 */
	public Authentication(ApiExecutor executor, Map<String, String> runtimeHeaders, State state) {
		if (executor == null) {
			throw new IllegalArgumentException("executor must not be null");
		}
		if (runtimeHeaders == null) {
			throw new IllegalArgumentException("runtimeHeaders must not be null");
		}
		if (state == null) {
			throw new IllegalArgumentException("state must not be null");
		}

		this.executor = executor;
		this.runtimeHeaders = runtimeHeaders;
		this.state = state;
	}

	/**
	 * Configures OAuth2 bearer-token authentication.
	 *
	 * <p>
	 * This follows REST Assured naming. Internally it adds:
	 * </p>
	 *
	 * <pre>
	 * Authorization: Bearer accessToken
	 * </pre>
	 *
	 * @param accessToken already-created OAuth2 access token
	 * @return outer executor so the caller can continue with {@code response()}
	 */
	public ApiExecutor oauth2(String accessToken) {
		return bearer(accessToken);
	}

	/**
	 * Configures HTTP bearer-token authentication.
	 *
	 * @param token bearer token
	 * @return outer executor so the caller can continue with {@code response()}
	 */
	public ApiExecutor bearer(String token) {
		state.noAuth = false;

		if (token != null && !token.isBlank()) {
			runtimeHeaders.put("Authorization", "Bearer " + token);
		}

		return executor;
	}

	/**
	 * Configures HTTP Basic authentication.
	 *
	 * <p>
	 * The username and password are encoded as UTF-8 and sent as:
	 * </p>
	 *
	 * <pre>
	 * Authorization: Basic base64(username:password)
	 * </pre>
	 *
	 * @param userName user name
	 * @param password password
	 * @return outer executor so the caller can continue with {@code response()}
	 */
	public ApiExecutor basic(String userName, String password) {
		state.noAuth = false;

		String raw = String.valueOf(userName) + ":" + String.valueOf(password);
		String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

		runtimeHeaders.put("Authorization", "Basic " + encoded);

		return executor;
	}

	/**
	 * REST Assured compatibility method.
	 *
	 * <p>
	 * JPostman executors send {@link #basic(String, String)} and
	 * {@link #oauth2(String)} as request headers directly, so this method is a
	 * no-op marker that preserves familiar REST Assured chaining style:
	 * </p>
	 *
	 * <pre>
	 * executor.auth().preemptive().basic(username, password).response();
	 * </pre>
	 *
	 * @return this authentication helper
	 */
	public Authentication preemptive() {
		return this;
	}

	/**
	 * Configures header-based API-key authentication.
	 *
	 * <p>
	 * This helper is intended for APIs that expect keys in headers such as
	 * {@code X-API-Key}. Query-string API keys should be added through the request
	 * URL/query builder instead.
	 * </p>
	 *
	 * @param name  header name, for example {@code X-API-Key}
	 * @param value API-key value
	 * @return outer executor so the caller can continue with {@code response()}
	 */
	public ApiExecutor apiKey(String name, String value) {
		state.noAuth = false;

		if (name != null && !name.isBlank() && value != null) {
			runtimeHeaders.put(name, value);
		}

		return executor;
	}

	/**
	 * Disables Authorization for this execution.
	 *
	 * <p>
	 * Any runtime {@code Authorization} header configured through this helper is
	 * removed. The shared {@link State} is also marked so the executor can ignore an
	 * {@code Authorization} header that came from the Postman request itself.
	 * </p>
	 *
	 * @return outer executor so the caller can continue with {@code response()}
	 */
	public ApiExecutor none() {
		runtimeHeaders.remove("Authorization");
		state.noAuth = true;
		return executor;
	}

	/**
	 * Mutable authentication state shared between {@link Authentication} and the
	 * executor using it.
	 *
	 * <p>
	 * Executors use this state to decide whether an {@code Authorization} header
	 * parsed from the Postman collection should be applied or skipped.
	 * </p>
	 */
	public static final class State {

		private boolean noAuth;

		/**
		 * Returns whether request-level {@code Authorization} should be skipped.
		 *
		 * @return {@code true} after {@link Authentication#none()} is used
		 */
		public boolean isNoAuth() {
			return noAuth;
		}

		/**
		 * Resets authentication state after an execution.
		 */
		public void clear() {
			noAuth = false;
		}

		/**
		 * Determines whether a header parsed from the Postman request should be applied.
		 *
		 * <p>
		 * When {@link Authentication#none()} has been used, only
		 * {@code Authorization} is skipped. Other request headers are still applied.
		 * </p>
		 *
		 * @param name request header name
		 * @return {@code true} if the executor should apply the request header
		 */
		public boolean shouldApplyRequestHeader(String name) {
			return !noAuth || !"Authorization".equalsIgnoreCase(name);
		}
	}
}
