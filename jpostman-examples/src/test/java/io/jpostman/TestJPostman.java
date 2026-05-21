package io.jpostman;

import static io.jpostman.Constants.COLLECTION;
import static io.jpostman.Constants.ENVIRONMENT;
import static io.jpostman.Constants.GET_AUTH_USER;
import static io.jpostman.Constants.LOGIN_GET_TOKEN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.jpostman.executor.HttpClientExecutor;
import io.jpostman.playwright.PlaywrightExecutor;
import io.jpostman.restassured.RestAssuredExecutor;
import io.jpostman.unirest.UnirestExecutor;

public class TestJPostman {

	private static final Logger log = LoggerFactory.getLogger(TestJPostman.class);

	private JPostman.Context context;

	@BeforeClass
	public void load() throws Exception {
		context = JPostman.load(COLLECTION, ENVIRONMENT);
	}

	@Test
	public void testPlaywrightSharedSession() {
		log.debug("\n************** testPlaywrightSharedSession **************\n");
		try (PlaywrightExecutor executor = PlaywrightExecutor.create()) {
			// Build the login request from the Postman collection and resolve
			// collection/environment variables through JPostman.Context.
			Request loginRequest = context.request(LOGIN_GET_TOKEN);
			log.debug("LOGIN_GET_TOKEN REQUEST: {}", loginRequest);

			// Execute login using a Playwright, stores cookies/session state returned by
			// the server.
			ApiResponse loginResponse = executor.setRequest(loginRequest).response();
			assertEquals(loginResponse.statusCode(), 200, loginResponse.log());
			log.debug("LOGIN_GET_TOKEN RESPONSE: {}", loginResponse.log(true));

			// Build the authenticated user request.
			// No explicit Authorization header is added here. This verifies that the
			// same Playwright APIRequestContext can reuse the login session/cookies.
			Request userRequest = context.request(GET_AUTH_USER);
			log.debug("GET_AUTH_USER REQUEST: {}", userRequest);

			ApiResponse userResponse = executor.setRequest(userRequest).response();
			assertEquals(userResponse.statusCode(), 200, userResponse.log());
			assertNotNull(userResponse.path("id"), "Expected response body to contain id");
		}
	}

	@Test
	public void testRestAssuredSession() {
		log.debug("\n************** testRestAssuredSession **************\n");
		RestAssuredExecutor executor = RestAssuredExecutor.create();

		// Build the login request from the Postman collection and resolve
		// collection/environment variables through JPostman.Context.
		Request loginRequest = context.request(LOGIN_GET_TOKEN);
		log.debug("LOGIN_GET_TOKEN REQUEST: {}", loginRequest);

		// Execute login using an HTTP Client, stores cookies/session state returned by
		// the server.
		ApiResponse loginResponse = executor.setRequest(loginRequest).response();
		assertEquals(loginResponse.statusCode(), 200, loginResponse.log());
		log.debug("LOGIN_GET_TOKEN RESPONSE: {}", loginResponse.log(true));

		// Build the authenticated user request.
		// No explicit Authorization header is added here. This verifies that the
		// same HTTP Client Session Manager can reuse the login session/cookies.
		Request userRequest = context.request(GET_AUTH_USER);
		log.debug("GET_AUTH_USER REQUEST: {}", userRequest);

		ApiResponse userResponse = executor.setRequest(userRequest).response();
		assertEquals(userResponse.statusCode(), 200, userResponse.log());
		assertNotNull(userResponse.path("id"), "Expected response body to contain id");
	}

	@Test
	public void testUnirestSharedSession() {
		log.debug("\n************** testUnirestSharedSession **************\n");
		UnirestExecutor executor = UnirestExecutor.create();

		// Build the login request from the Postman collection and resolve
		// collection/environment variables through JPostman.Context.
		Request loginRequest = context.request(LOGIN_GET_TOKEN);
		log.debug("LOGIN_GET_TOKEN REQUEST: {}", loginRequest);

		// Execute login using an HTTP Client, stores cookies/session state returned by
		// the server.
		ApiResponse loginResponse = executor.setRequest(loginRequest).response();
		assertEquals(loginResponse.statusCode(), 200, loginResponse.log());
		log.debug("LOGIN_GET_TOKEN RESPONSE: {}", loginResponse.log(true));

		// Build the authenticated user request.
		// No explicit Authorization header is added here. This verifies that the
		// same HTTP Client Session Manager can reuse the login session/cookies.
		Request userRequest = context.request(GET_AUTH_USER);
		log.debug("GET_AUTH_USER REQUEST: {}", userRequest);

		ApiResponse userResponse = executor.setRequest(userRequest).response();
		assertEquals(userResponse.statusCode(), 200, userResponse.log());
		assertNotNull(userResponse.path("id"), "Expected response body to contain id");
	}

	@Test
	public void testHttpClientSharedSession() {
		log.debug("\n************** testHttpClientSharedSession **************\n");
		HttpClientExecutor executor = HttpClientExecutor.create();

		// Build the login request from the Postman collection and resolve
		// collection/environment variables through JPostman.Context.
		Request loginRequest = context.request(LOGIN_GET_TOKEN);
		log.debug("LOGIN_GET_TOKEN REQUEST: {}", loginRequest);

		// Execute login using an HTTP Client, stores cookies/session state returned by
		// the server.
		ApiResponse loginResponse = executor.setRequest(loginRequest).response();
		assertEquals(loginResponse.statusCode(), 200, loginResponse.log());
		log.debug("LOGIN_GET_TOKEN RESPONSE: {}", loginResponse.log(true));

		// Build the authenticated user request.
		// No explicit Authorization header is added here. This verifies that the
		// same HTTP Client Session Manager can reuse the login session/cookies.
		Request userRequest = context.request(GET_AUTH_USER);
		log.debug("GET_AUTH_USER REQUEST: {}", userRequest);

		ApiResponse userResponse = executor.setRequest(userRequest).response();
		assertEquals(userResponse.statusCode(), 200, userResponse.log());
		assertNotNull(userResponse.path("id"), "Expected response body to contain id");
	}
}
