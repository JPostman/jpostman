package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.jpostman.Collection;
import io.jpostman.Params;
import io.jpostman.Request;

/**
 * Regression coverage for JPostmanInfo request-value application modes.
 *
 * <p>
 * The tests intentionally distinguish between:
 * </p>
 * <ul>
 * <li>set mode: updates a parameter/property already declared by the
 * request,</li>
 * <li>add mode: explicitly adds a new body/query/header value, and</li>
 * <li>raw JSON fragment mode: resolves one placeholder to zero or more JSON
 * values.</li>
 * </ul>
 */
public class JPostmanAnnotationRequestValueModeRegressionTest {

	@Test
	public void bodyQueryAndHeadersSetExistingValuesAndAddMissingValues() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		// Default mode updates values already declared in the request.
		info.body("title", "Wireless Mouse").query("limit", 25).headers("X-Token", "token-123");

		// Explicit add mode creates values that are not declared by the request.
		info.add().body("dateCreated", "today").add().query("debug", "true").add().headers("X-Debug", "enabled");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("25", updated.getUrl().get("limit"));
		assertEquals("true", updated.getUrl().get("debug"));
		assertEquals("token-123", updated.getHeader().get("X-Token"));
		assertEquals("enabled", updated.getHeader().get("X-Debug"));

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());
		assertEquals("today", body.get("dateCreated").getAsString());
	}

	@Test
	public void bodyContainsResolvableParametersBeforeValuesAreApplied() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();

		// The body property name and placeholder name are both "title". This is
		// important because Body.set("title", value) updates an existing body key.
		assertNotNull(request.getBody());
		assertTrue(request.getBody().params().containsKey("title"));
		assertEquals("", request.getBody().params().get("title"),
				"Body.params() exposes the resolvable key; its unresolved/default value is empty.");
	}

	@Test
	public void bodyResolvesRawJsonFragmentPlaceholderWithoutAddingANewProperty() throws Exception {
		Request request = requestWithRawJsonArrayFragment();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		// The placeholder is intentionally unquoted because jsonList() must be
		// inserted as a JSON array fragment rather than as one quoted String.
		info.body("title", "Wireless Mouse").body("new_item", Params.jsonList("item1", "item2"));

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("[POST  ] Update product                           -> https://example.com/products\n"
				+ "Body: [raw/json] {\n  \"title\": \"Wireless Mouse\",\n  \"items\": [\n"
				+ "    \"item1\",\n    \"item2\"\n  ]\n}", updated.log());

		assertNotNull(updated.getBody());
		assertNotNull(updated.getBody().getParsed(),
				() -> "Resolved body must be valid JSON. Raw body was: " + updated.getBody().getRaw());

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());

		JsonArray items = body.getAsJsonArray("items");
		assertNotNull(items);
		assertEquals(2, items.size());
		assertEquals("item1", items.get(0).getAsString());
		assertEquals("item2", items.get(1).getAsString());
		assertFalse(body.has("new_item"), "new_item is a template variable and must not be added as a body property.");
	}

	@Test
	public void unresolvedRawJsonFragmentProducesAnEmptyArray() throws Exception {
		Request request = requestWithRawJsonArrayFragment();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Update product");

		// No new_item value is supplied. Full request resolution must replace the
		// unresolved token with an empty string, leaving: "items": [].
		info.body("title", "Wireless Mouse");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("[POST  ] Update product                           -> https://example.com/products\n"
				+ "Body: [raw/json] {\n  \"title\": \"Wireless Mouse\",\n  \"items\": []\n}", updated.log());

		assertNotNull(updated.getBody());
		assertNotNull(updated.getBody().getParsed(),
				() -> "Body with an omitted fragment must still be valid JSON. Raw body was: "
						+ updated.getBody().getRaw());

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());

		JsonArray items = body.getAsJsonArray("items");
		assertNotNull(items);
		assertTrue(items.isEmpty());
		assertFalse(body.has("new_item"));
	}

	@Test
	public void jsonListReturnsTypedListForRawJsonValues() {
		// jsonList is a typed value helper. It returns the values without adding
		// quotes or converting them to a Java collection string.
		java.util.List<String> items = Params.jsonList("item1", "item2");

		assertEquals(2, items.size());
		assertEquals("item1", items.get(0));
		assertEquals("item2", items.get(1));
	}

	@Test
	public void jsonListReturnsEmptyListWhenNoValuesAreProvided() {
		java.util.List<String> items = Params.<String>jsonList();

		assertTrue(items.isEmpty());
	}

	@Test
	public void jsonMapReturnsTypedMapForJsonObjectValues() {
		java.util.Map<String, String> values = Params.<String>jsonMap("first", "item1", "second", "item2");

		assertEquals(2, values.size());
		assertEquals("item1", values.get("first"));
		assertEquals("item2", values.get("second"));
	}

	@Test
	public void jsonMapReturnsEmptyMapWhenNoValuesAreProvided() {
		java.util.Map<String, String> values = Params.<String>jsonMap();

		assertTrue(values.isEmpty());
	}

	@Test
	public void secureValuesSetConfiguredParamsAndExplicitlyAddMissingParams() throws Exception {
		Request request = requestWithExistingBodyQueryAndHeader();
		JPostmanInfo info = new JPostmanInfo("request", "", "", "Update product");

		// Secure set operations target existing request values.
		info.sbody("title", "updated-title").sheaders("X-Token", "updated-header").spath("limit", "50");

		// Secure add operations create values that do not exist in the request.
		info.add().sbody("refreshToken", "refresh-secret").add().sheaders("MY_SECRET", "new-header").add().spath("todo",
				"new-url-param");

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
	public void addWorksWithSecureBodyQueryAndHeaderMethods() {
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
				"Masking metadata must retain the secret independently of request serialization.");
	}

	private static Request requestWithExistingBodyQueryAndHeader() throws Exception {
		String json = "{\"item\":[{\"name\":\"Update product\",\"request\":{\"method\":\"POST\","
				+ "\"url\":{\"raw\":\"https://example.com/products?limit={{limit}}\","
				+ "\"host\":[\"example\",\"com\"],\"path\":[\"products\"],"
				+ "\"query\":[{\"key\":\"limit\",\"value\":\"{{limit}}\"}]},"
				+ "\"header\":[{\"key\":\"X-Token\",\"value\":\"{{token}}\"}],"
				+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"title\\\":\\\"{{title}}\\\"}\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject()).getRequest("Update product");
	}

	private static Request requestWithRawJsonArrayFragment() throws Exception {
		String json = "{\"item\":[{\"name\":\"Update product\",\"request\":{\"method\":\"POST\","
				+ "\"url\":\"https://example.com/products\",\"body\":{\"mode\":\"raw\","
				+ "\"raw\":\"{\\\"title\\\":\\\"{{title}}\\\",\\\"items\\\":[/*Runtime values*/\n{{new_item}}]}\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject()).getRequest("Update product");
	}
}