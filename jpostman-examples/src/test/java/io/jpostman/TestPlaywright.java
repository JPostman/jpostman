package io.jpostman;

import static io.jpostman.Constants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.jpostman.playwright.PlaywrightExecutor;

/**
 * Demonstrates how to execute Postman-exported collection requests with
 * Playwright's APIRequestContext.
 */
public class TestPlaywright {

	private static final Logger log = LoggerFactory.getLogger(TestPlaywright.class);

	private Collection col;
	private Environment env;
	private Folder folder;

	@BeforeClass
	public void load() throws Exception {
		// Load exported Postman collection from classpath resources.
		col = Collection.load(TestCoverage.class.getClassLoader().getResourceAsStream(COLLECTION_FILE));

		// Load exported Postman environment from classpath resources.
		env = Environment.load(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));

		// Cache Product folder for product-related API tests.
		folder = col.getFolder(PRODUCT_FOLDER);
		folder.print();
	}

	// -------------------------------------------------------------------------
	// Playwright API tests
	// -------------------------------------------------------------------------

	@Test
	public void testGetAccessToken() {
		assertNotNull(env, "Environment not loaded");

		// Get login request template from exported Postman collection.
		Request template = col.getRequest("Login user test request");
		assertNotNull(template, "Request template not found");

		// Build executable request by resolving {{variables}} from environment.
		Request req = template.builder().url() // starts from: {{base_url}}/auth/{{TOKEN}}?q={{TOKEN}}
				.set("q", "find") // updates query parameter: q={{TOKEN}} -> q=find
				.map("TOKEN", "login") // resolves URL path token locally: /auth/{{TOKEN}} -> /auth/login
				.auth(c -> c.set("token", "UNKNOWN")) // overrides bearer token value: token={{TOKEN}} -> token=UNKNOWN
				.body().set("password", env.get("password")) // updates JSON field: "password":"{{password}}" ->
																// "password":"emilyspass"
				.add("age", 21) // adds JSON field: "age":21
				.json("TOKEN", env.get("username")) // JSON-stringifies local token: {{TOKEN}} -> "emilys"
				.build(env); // resolves remaining environment tokens, for example {{base_url}}

		// Show template before resolution and final request after resolution.
		log.debug("REQUEST BEFORE:\n{}", template);
		log.debug("REQUEST AFTER:\n{}", req);
		req.print();

		// Execute login request and validate access token is returned.
		ApiResponse response = PlaywrightExecutor.execute(req);
		assertEquals(response.statusCode(), 200, response.log());

		// Store runtime token back into environment for dependent authenticated calls.
		log.debug("ENV BEFORE:\n{}", env);
		env = env.builder().add(ENV_TOKEN_KEY, response.path("accessToken")).end();
		log.debug("ENV AFTER:\n{}", env);
	}

	@Test
	public void getAccessToken() {
		assertNotNull(env, "Environment not loaded");

		// Get login request template from exported Postman collection.
		Request template = col.getRequest(LOGIN_GET_TOKEN);
		assertNotNull(template, "Request template not found");

		// Build executable request by resolving {{variables}} from environment.
		Request req = template.builder().build(env);

		// Show template before resolution and final request after resolution.
		log.debug("REQUEST BEFORE: {}", template);
		log.debug("REQUEST AFTER:  {}", req);

		// Execute login request and validate access token is returned.
		ApiResponse response = PlaywrightExecutor.execute(req);
		assertEquals(response.statusCode(), 200, response.log());

		// Store runtime token back into environment for dependent authenticated calls.
		log.debug("ENV BEFORE:\n{}", env);
		env = env.builder().add(ENV_TOKEN_KEY, response.path("accessToken")).end();
		log.debug("ENV AFTER:\n{}", env);
	}

	@Test(dependsOnMethods = "getAccessToken")
	public void testGetUser() {
		assertNotNull(env, "Environment not loaded");

		// Get authenticated user request from collection.
		Request template = col.getRequest(GET_AUTH_USER);
		assertNotNull(template, "Request template not found");

		// Resolve URL/auth placeholders from current environment.
		Request req = template.builder().build(env);

		// Execute GET /auth/me using bearer token captured during login.
		ApiResponse response = PlaywrightExecutor.apply(req)
				.auth().oauth2(env.get(ENV_TOKEN_KEY))
				.response();
		assertEquals(response.statusCode(), 200, response.log());
		assertTrue(response.path("id") != null, "Expected response body to contain id");
	}

	@Test
	public void testGetProducts() {
		assertNotNull(env, "Environment not loaded");

		// Get "all products" request from Product folder.
		Request template = folder.getRequest(ALL_PRODUCTS);
		assertNotNull(template, "Request template not found");

		// Resolve environment variables and apply request settings.
		Request req = template.builder().build(env);

		// Execute product list request and verify response contains pagination limit.
		ApiResponse response = PlaywrightExecutor.execute(req);
		assertEquals(response.statusCode(), 200, response.log());
		assertTrue(response.path("limit") != null, "Expected response body to contain limit");
		assertEquals(response.path("products[0].title"), "Essence Mascara Lash Princess");
	}

	@Test
	public void testGetProduct() {
		assertNotNull(env, "Environment not loaded");

		// Get single product request from Product folder.
		Request template = folder.getRequest(SINGLE_PRODUCT);
		assertNotNull(template, "Request template not found");

		// Build executable request from Postman template.
		Request req = template.builder().build(env);

		// Execute single product request and verify product id exists.
		ApiResponse response = PlaywrightExecutor.execute(req);
		assertEquals(response.statusCode(), 200, response.log());
		assertTrue(response.path("id") != null, "Expected response body to contain id");
	}

	@Test
	public void testGetImage() throws IOException {
		assertNotNull(env, "Environment not loaded");

		// Get Dynamic Image folder from collection.
		Folder folder = col.getFolder(IMAGE_FOLDER);
		folder.print();

		// Get image generation request template.
		Request template = folder.getRequest(GENERATE_IMAGE);
		assertNotNull(template, "Request template not found");
		template.print();

		// Build original image request from environment.
		Request req1 = template.builder().build(env);

		// Build modified image request by overriding only one query parameter.
		Request req2 = template.builder().url(q -> q.set("text", "Hello World"))
				// or .queries().set("text", "Hello World").end()
				.build(env);

		// Log both requests to show how one Postman template can produce variations.
		log.debug("REQUEST 1: {}", req1);
		log.debug("REQUEST 2: {}", req2);

		// Execute original image request.
		ApiResponse response1 = PlaywrightExecutor.execute(req1);
		assertEquals(response1.statusCode(), 200, response1.log());

		// Execute modified image request.
		ApiResponse response2 = PlaywrightExecutor.execute(req2);
		assertEquals(response1.statusCode(), 200, response2.log());

		// Different query text should produce different image bytes.
		assertTrue(!Arrays.equals(response1.asByteArray(), response2.asByteArray()), "Expected different bytes");

		// Save generated image so it can be inspected manually after test execution.
		Files.write(Path.of("src/test/resources/hello_world.png"), response2.asByteArray());
	}
}