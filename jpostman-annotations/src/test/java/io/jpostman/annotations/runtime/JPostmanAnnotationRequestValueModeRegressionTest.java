package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.jpostman.Collection;
import io.jpostman.Request;

/**
 * Regression coverage for JPostmanInfo request-value application modes.
 */
public class JPostmanAnnotationRequestValueModeRegressionTest {

	@Test
	public void bodyQueryAndHeadersSetExistingValuesAndAddMissingValues() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.body("title", "Wireless Mouse").query("limit", 25, "missingQuery", "not-added")
				.headers("X-Token", "token-123").add().body("dateCreated", "today").add().query("debug", "true").add()
				.headers("X-Debug", "enabled").headers("X-Not-Added", "missing");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("25", updated.getUrl().get("limit"));
		assertEquals("true", updated.getUrl().get("debug"));
		assertEquals("not-added", updated.getUrl().get("missingQuery"));

		assertEquals("token-123", updated.getHeader().get("X-Token"));
		assertEquals("enabled", updated.getHeader().get("X-Debug"));
		assertEquals("missing", updated.getHeader().get("X-Not-Added"));

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());
		assertEquals("today", body.get("dateCreated").getAsString());
	}

	@Test
	public void secureValuesSetConfiguredParamsAndAddMissingParams() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("request", "", "", "Update product");

		info.sbody("title", "updated-title").sbody("refreshToken", "refresh-secret")
				.sheaders("X-Token", "updated-header").sheaders("MY_SECRET", "new-header").spath("limit", "50")
				.spath("todo", "new-url-param");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("updated-title", body.get("title").getAsString());
		assertEquals("refresh-secret", body.get("refreshToken").getAsString());
		assertEquals("updated-header", updated.getHeader().get("X-Token"));
		assertEquals("new-header", updated.getHeader().get("MY_SECRET"));
		assertEquals("50", updated.getUrl().get("limit"));
		assertEquals("new-url-param", updated.getUrl().get("todo"));
	}

	@Test
	public void addWorksWithSecureBodyQueryAndHeaderMethods() throws Exception {
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.add().sbody("secretBody", "hidden-body").add().squery("secretQuery", "hidden-query").add()
				.sheaders("X-Secret", "hidden-header");

		assertTrue(info.bodyAdd.containsKey("secretBody"));
		assertTrue(info.queryAdd.containsKey("secretQuery"));
		assertTrue(info.headersAdd.containsKey("X-Secret"));
		assertEquals("hidden-body", info.secretValues().get("secretBody"));
		assertEquals("hidden-query", info.secretValues().get("secretQuery"));
		assertEquals("hidden-header", info.secretValues().get("X-Secret"));
		assertTrue(Arrays.asList(info.secretHeaders()).contains("X-Secret"));
	}

	@Test
	public void toJsonStringifiesLastRequestValueGroup() {
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.body("username", "emilys", "password", "emilyspass").toJson();

		assertEquals("\"emilys\"", info.body.get("username"));
		assertEquals("\"emilyspass\"", info.body.get("password"));

		info.query("limit", 25).toJson();

		assertEquals("25", info.query.get("limit"));
		assertEquals("\"emilys\"", info.body.get("username"),
				"toJson() should affect only the most recent request-value group.");
	}

	@Test
	public void toJsonStringifiesCollectionItemsAndSecretValues() {
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.body("products", Arrays.asList("1", "2", "3")).toJson();

		assertEquals(Arrays.asList("\"1\"", "\"2\"", "\"3\""), info.body.get("products"));

		JPostmanInfo secret = new JPostmanInfo("response", "", "", "Update product");

		secret.sheaders("Authorization", "Bearer token").toJson();

		assertEquals("\"Bearer token\"", secret.secretValues().get("Authorization"));
		assertTrue(Arrays.asList(secret.secretHeaders()).contains("Authorization"));
	}

	@Test
	public void sauthOAuth2AddsBearerAuthorizationHeaderForExecutorCompatibility() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.sauth("oauth2", "secret-token");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("Bearer secret-token", updated.getHeader().get("Authorization"));
		assertEquals("secret-token", info.secretValues().get("oauth2"));
	}

	@Test
	public void secureValueMethodsNormalizeCachedSecretWrappersBeforeWrappingAgain() {
		JPostmanInfo source = new JPostmanInfo("response", "", "", "Login");
		source.sbody("refreshToken", "refresh-secret");
		Object cachedSecret = source.body.get("refreshToken");

		JPostmanInfo target = new JPostmanInfo("request", "", "", "Refresh");
		target.sbody("refreshToken", cachedSecret).squery("refreshToken", cachedSecret)
				.sheaders("X-Refresh-Token", cachedSecret).spath("refreshToken", cachedSecret)
				.sauth("oauth2", cachedSecret);

		assertEquals("refresh-secret", target.secretValues().get("refreshToken"));
		assertEquals("refresh-secret", target.secretValues().get("X-Refresh-Token"));
		assertEquals("refresh-secret", target.secretValues().get("oauth2"));
	}

	@Test
	public void secureValuesAreRevealedOnlyWhenAppliedToExecutableRequest() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("request", "", "", "Update product");

		info.sbody("title", "refresh-secret").squery("limit", 40).sheaders("X-Token", "header-secret").sauth("oauth2",
				"access-secret");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertTrue(body.get("title").isJsonPrimitive(),
				"Secure body values must be applied as scalars, not serialized wrapper objects.");
		assertEquals("refresh-secret", body.get("title").getAsString());
		assertEquals("40", updated.getUrl().get("limit"));
		assertEquals("header-secret", updated.getHeader().get("X-Token"));
		assertEquals("Bearer access-secret", updated.getHeader().get("Authorization"));

		assertEquals("refresh-secret", info.secretValues().get("title"),
				"Masking metadata must still retain the secret value independently of request serialization.");
	}

	private static Request requestWithExistingBodyQueryAndHeader() throws Exception {
		String json = "{\"item\":[{\"name\":\"Update product\",\"request\":{\"method\":\"POST\","
				+ "\"url\":{\"raw\":\"https://example.com/products?limit={{limit}}\","
				+ "\"host\":[\"example\",\"com\"],\"path\":[\"products\"],"
				+ "\"query\":[{\"key\":\"limit\",\"value\":\"{{limit}}\"}]},"
				+ "\"header\":[{\"key\":\"X-Token\",\"value\":\"{{token}}\"}],"
				+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"title\\\":\\\"Old Mouse\\\"}\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject()).getRequest("Update product");
	}
}
