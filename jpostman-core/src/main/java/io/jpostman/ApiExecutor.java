package io.jpostman;

/**
 * Executes a parsed JPostman {@link Request} using any HTTP/API testing
 * framework.
 *
 * <p>
 * Implementations may use Java HttpClient, REST Assured, OkHttp, Apache
 * HttpClient, Playwright APIRequestContext, or a project-specific client. The
 * core library depends only on this small interface.
 * </p>
 */
@FunctionalInterface
public interface ApiExecutor {

	/**
	 * Executes the request already associated with this executor and returns a
	 * framework-neutral response.
	 *
	 * @return response wrapper
	 */
	ApiResponse response();
}
