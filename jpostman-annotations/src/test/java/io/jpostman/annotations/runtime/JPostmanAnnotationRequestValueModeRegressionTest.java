package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	public void bodyQueryAndHeadersSetExistingValuesAndAddOnlyWhenRequested() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.body("title", "Wireless Mouse").query("limit", 25, "missingQuery", "not-added")
				.headers("X-Token", "token-123").add().body("dateCreated", "today").add().query("debug", "true").add()
				.headers("X-Debug", "enabled").headers("X-Not-Added", "missing");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("25", updated.getUrl().get("limit"));
		assertEquals("true", updated.getUrl().get("debug"));
		assertFalse(updated.getUrl().getParams().containsKey("missingQuery"));

		assertEquals("token-123", updated.getHeader().get("X-Token"));
		assertEquals("enabled", updated.getHeader().get("X-Debug"));
		assertFalse(updated.getHeader().getParams().containsKey("X-Not-Added"),
				"add() should apply only to the next value method call.");

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());
		assertEquals("today", body.get("dateCreated").getAsString());
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
	public void sauthOAuth2AddsBearerAuthorizationHeaderForExecutorCompatibility() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		info.sauth("oauth2", "secret-token");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("Bearer secret-token", updated.getHeader().get("Authorization"));
		assertEquals("secret-token", info.secretValues().get("oauth2"));
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
