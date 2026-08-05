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
import io.jpostman.secure.SecureValue;

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

		// Missing component fields are added directly; no separate add mode is
		// required.
		info.body("dateCreated", "today").query("debug", "true").headers("X-Debug", "enabled");

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
	public void missingPlainValuesAreAddedWithoutAddMode() throws Exception {
		JPostmanInfo info;

		// Default mode is set/resolve. Missing component fields must not queue a
		// delayed set failure during RequestBuilder.build().
		info = new JPostmanInfo("response", "", "", "Update product").params("wrongBody", "myBody")
				.params("wrongHeader", "myToken").params("wrongQuery", "myLimit");
		Request unchanged = JPostmanFramework.applyRequestValues(requestWithExistingBodyQueryAndHeader(), info);
		assertEquals("{\"title\":\"{{title}}\"}", unchanged.getBody().getRaw());
		assertEquals("{{token}}", unchanged.getHeader().get("X-Token"));
		assertEquals("{{limit}}", unchanged.getUrl().get("limit"));

		info = new JPostmanInfo("response", "", "", "Update product").params("title", "myBody")
				.params("token", "myToken").params("limit", "myLimit");
		unchanged = JPostmanFramework.applyRequestValues(requestWithExistingBodyQueryAndHeader(), info);
		assertEquals("{\"title\":\"myBody\"}", unchanged.getBody().getRaw());
		assertEquals("myToken", unchanged.getHeader().get("X-Token"));
		assertEquals("myLimit", unchanged.getUrl().get("limit"));

		info = new JPostmanInfo("response", "", "", "Update product").body("{{title}}", "myBody")
				.headers("{{token}}", "myToken").query("{{limit}}", "myLimit");
		unchanged = JPostmanFramework.applyRequestValues(requestWithExistingBodyQueryAndHeader(), info);
		assertEquals("{\"title\":\"myBody\"}", unchanged.getBody().getRaw());
		assertEquals("myToken", unchanged.getHeader().get("X-Token"));
		assertEquals("myLimit", unchanged.getUrl().get("limit"));

		info = new JPostmanInfo("response", "", "", "Update product").body("newTitle", "myBody")
				.headers("newToken", "myToken").query("newLimit", "myLimit");
		unchanged = JPostmanFramework.applyRequestValues(requestWithExistingBodyQueryAndHeader(), info);
		assertEquals("{\"title\":\"{{title}}\",\"newTitle\":\"myBody\"}", unchanged.getBody().getRaw());
		assertEquals("myToken", unchanged.getHeader().get("newToken"));
		assertEquals("myLimit", unchanged.getUrl().get("newLimit"));
		assertEquals("{{token}}", unchanged.getHeader().get("X-Token"));
		assertEquals("{{limit}}", unchanged.getUrl().get("limit"));
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
		info.body("title", "Wireless Mouse", "{{new_item}}", Params.<String>jsonList("item1", "item2"));

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
		java.util.List<String> items = Params.asList("item1", "item2");

		assertEquals(2, items.size());
		assertEquals("item1", items.get(0));
		assertEquals("item2", items.get(1));
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
		info.sbody("refreshToken", "refresh-secret").sheaders("MY_SECRET", "new-header").spath("todo", "new-url-param");

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

		info.sbody("secretBody", "hidden-body").squery("secretQuery", "hidden-query").sheaders("X-Secret",
				"hidden-header");

		assertTrue(info.body.containsKey("secretBody"));
		assertTrue(info.query.containsKey("secretQuery"));
		assertTrue(info.headers.containsKey("X-Secret"));
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
	public void sauthOAuth2CreatesAuthorizationHeaderWhenRequestHasNoHeaderSection() throws Exception {
		Request request = requestWithoutHeaders();
		JPostmanInfo info = new JPostmanInfo("response", "", "Auth", "Get current authenticated user");

		info.sauth("oauth2", "access-token");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		assertEquals("Bearer access-token", updated.getHeader().get("Authorization"));
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

	@Test
	public void dependencyParamsResolvePathQueryHeadersBodyAndAuthBeforeUnresolvedCleanup() throws Exception {
		Request request = requestWithUnresolvedValuesInEveryRequestSection();
		JPostmanInfo dependencyInfo = new JPostmanInfo("request", "testReq", "", "My Request");

		dependencyInfo.params("hierarchy", "parent", "interval", "daily", "limit", 20, "token", "header-token",
				"oauth2", "access-token");

		Request updated = JPostmanFramework.applyRequestValues(request, dependencyInfo);

		assertTrue(updated.log().contains("/consumption/parent?limit=20"), updated::log);
		assertEquals("20", updated.getUrl().get("limit"));
		assertEquals("header-token", updated.getHeader().get("X-Token"));
		assertEquals("Bearer access-token", updated.getHeader().get("Authorization"));

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("daily", body.get("interval").getAsString());
	}

	@Test
	public void originalTemplateStillExposesAllParamsAfterOneResolvedCopyIsBuilt() throws Exception {
		Request original = requestWithUnresolvedValuesInEveryRequestSection();
		JPostmanInfo firstDependency = new JPostmanInfo("request", "firstDependency", "", "My Request");
		firstDependency.params("hierarchy", "parent");

		Request firstResolvedCopy = JPostmanFramework.applyRequestValues(original, firstDependency);
		assertTrue(firstResolvedCopy.log().contains("/consumption/parent"), firstResolvedCopy::log);

		JPostmanInfo completeDependencyChain = new JPostmanInfo("response", "testRes", "", "My Request");
		completeDependencyChain.params("hierarchy", "parent", "interval", "daily", "limit", 20, "token", "header-token",
				"oauth2", "access-token");

		Request finalRequest = JPostmanFramework.applyRequestValues(original, completeDependencyChain);

		assertTrue(finalRequest.log().contains("/consumption/parent?limit=20"), finalRequest::log);
		assertEquals("header-token", finalRequest.getHeader().get("X-Token"));
		assertEquals("Bearer access-token", finalRequest.getHeader().get("Authorization"));
		assertEquals("daily", finalRequest.getBody().getParsed().getAsJsonObject().get("interval").getAsString());
	}

	@Test
	public void runtimePrintFalseKeepsEveryUnresolvedPowerDailyPlaceholder() throws Exception {
		Request unresolved = requestWithPowerDailyUnresolvedBody();
		JPostmanInfo info = new JPostmanInfo("executor", "defaultExecutor", "My Folder", "My Request");
		info.params("hierarchy", "parent", "interval", "parent");
		info.sourceRequest(unresolved);

		Request executable = JPostmanFramework.applyRequestValues(unresolved, info);
		RuntimeRequestContext active = new RuntimeRequestContext(executable);
		JPostmanRuntime<RuntimeRequestContext> runtime = new JPostmanRuntime<>(null, "", ignored -> active,
				() -> active, () -> info);

		String raw = runtime.log(false);

		assertTrue(raw.contains("/v1/{{hierarchy}}"), raw);
		assertTrue(raw.contains("\"fromDate\": \"{{fromDate}}\""), raw);
		assertTrue(raw.contains("\"toDate\": \"{{toDate}}\""), raw);

		// Rendering the unresolved form must not replace the executable request held by
		// the active context.
		assertEquals(executable, active.request().request());
	}

	@Test
	public void runtimePrintTrueMasksSecureValueAcrossPathQueryAuthHeadersAndBody() throws Exception {
		String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
		Request unresolved = requestWithSameSecretInEveryRequestSection();
		JPostmanInfo info = new JPostmanInfo("executor", "defaultExecutor", "", "Secure request");

		// params resolves the token throughout the request. Marking the same value
		// secure
		// through sheaders must mask every occurrence in the final resolved log.
		info.params("pathToken", token, "queryToken", token, "AccessToken", token, "bodyToken", token);
		info.sheaders("AccessToken", token);

		Request executable = JPostmanFramework.applyRequestValues(unresolved, info);
		RuntimeRequestContext active = new RuntimeRequestContext(executable);
		JPostmanRuntime<RuntimeRequestContext> runtime = new JPostmanRuntime<>(null, "", ignored -> active,
				() -> active, () -> info);

		String resolvedLog = runtime.log(true);

		assertFalse(resolvedLog.contains(token), resolvedLog);
		assertTrue(resolvedLog.contains("Bearer " + SecureValue.DEFAULT_MASK), resolvedLog);
		assertTrue(resolvedLog.contains("AccessToken"), resolvedLog);
		assertTrue(resolvedLog.contains(SecureValue.DEFAULT_MASK), resolvedLog);

		// Masking is presentation-only. The executable request still contains the real
		// secret required by the HTTP executor.
		assertEquals(token, executable.getHeader().get("AccessToken"));
		assertEquals("Bearer " + token, executable.getHeader().get("Authorization"));
	}

	@Test
	public void infoLogIncludesParamsAndMasksSensitiveParamValues() {
		JPostmanInfo info = new JPostmanInfo("request", "testReq", "", "My Request");
		info.params("hierarchy", "parent", "interval", "daily", "token", "secret-token");

		String log = info.log(false);

		assertTrue(log.contains("params={hierarchy=parent, interval=daily, token=" + SecureValue.DEFAULT_MASK + "}"),
				log);
		assertFalse(log.contains("secret-token"), log);
	}

	@Test
	public void componentFieldNamesOverrideDifferentPlaceholderNames() throws Exception {
		Request request = requestWithDifferentFieldAndPlaceholderNames();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Create product");

		info.body("title", "Wireless Mouse", "price", 25).headers("X-Token", "header-token").query("limit", 50)
				.path("id", 101);

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());
		assertEquals(25, body.get("price").getAsInt());
		assertEquals("header-token", updated.getHeader().get("X-Token"));
		assertEquals("50", updated.getUrl().get("limit"));
		assertTrue(updated.log().contains("/products/101?limit=50"), updated.log());
	}

	@Test
	public void secureComponentFieldNamesOverrideDifferentPlaceholderNames() throws Exception {
		Request request = requestWithDifferentFieldAndPlaceholderNames();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Create product");

		info.sbody("title", "Wireless Mouse", "price", 25).sheaders("X-Token", "header-token").squery("limit", 50)
				.spath("id", 101).sauth("oauth2", "secret-token");

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());
		assertEquals(25, body.get("price").getAsInt());
		assertEquals("header-token", updated.getHeader().get("X-Token"));
		assertEquals("Bearer secret-token", updated.getHeader().get("Authorization"));
		assertEquals("50", updated.getUrl().get("limit"));
		assertTrue(updated.log().contains("/products/101?limit=50"), updated.log());
	}

	@Test
	public void wrappedKeysStillResolvePlaceholderNamesWithoutAddingFields() throws Exception {
		Request request = requestWithDifferentFieldAndPlaceholderNames();
		JPostmanInfo info = new JPostmanInfo("response", "", "", "Create product");

		info.body("{{productTitle}}", "Wireless Mouse", "{{productPrice}}", 25)
				.headers("{{headerToken}}", "header-token").query("{{pageLimit}}", 50).path("{{id}}", 101);

		Request updated = JPostmanFramework.applyRequestValues(request, info);

		JsonObject body = updated.getBody().getParsed().getAsJsonObject();
		assertEquals("Wireless Mouse", body.get("title").getAsString());
		assertEquals(25, body.get("price").getAsInt());
		assertFalse(body.has("productTitle"));
		assertFalse(body.has("productPrice"));
		assertEquals("header-token", updated.getHeader().get("X-Token"));
		assertEquals("50", updated.getUrl().get("limit"));
		assertTrue(updated.log().contains("/products/101?limit=50"), updated.log());
	}

	private static Request requestWithDifferentFieldAndPlaceholderNames() throws Exception {
		String json = "{\"item\":[{\"name\":\"Create product\",\"request\":{\"method\":\"POST\","
				+ "\"url\":{\"raw\":\"https://example.com/products/{{id}}?limit={{pageLimit}}\","
				+ "\"host\":[\"example\",\"com\"],\"path\":[\"products\",\"{{id}}\"],"
				+ "\"variable\":[{\"key\":\"productId\",\"value\":\"{{id}}\"}],"
				+ "\"query\":[{\"key\":\"limit\",\"value\":\"{{pageLimit}}\"}]},"
				+ "\"header\":[{\"key\":\"X-Token\",\"value\":\"{{headerToken}}\"}],"
				+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"title\\\":\\\"{{productTitle}}\\\",\\\"price\\\":{{productPrice}}}\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject()).getRequest("Create product");
	}

	private static Request requestWithSameSecretInEveryRequestSection() throws Exception {
		String json = "{\"item\":[{\"name\":\"Secure request\",\"request\":{\"method\":\"POST\","
				+ "\"url\":{\"raw\":\"https://example.com/{{pathToken}}?access={{queryToken}}\","
				+ "\"host\":[\"example\",\"com\"],\"path\":[\"{{pathToken}}\"],"
				+ "\"query\":[{\"key\":\"access\",\"value\":\"{{queryToken}}\"}]},"
				+ "\"header\":[{\"key\":\"Authorization\",\"value\":\"Bearer {{AccessToken}}\"}],"
				+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"token\\\":\\\"{{bodyToken}}\\\"}\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject()).getRequest("Secure request");
	}

	private static Request requestWithPowerDailyUnresolvedBody() throws Exception {
		String body = "{\n  \"fromDate\": \"{{fromDate}}\",\n  \"toDate\": \"{{toDate}}\"\n}";
		JsonObject request = new JsonObject();
		request.addProperty("method", "POST");
		request.addProperty("url", "{{base_url}}/v1/{{hierarchy}}");
		JsonArray headers = new JsonArray();
		headers.add(header("Authorization", "Bearer {{AccessToken}}"));
		headers.add(header("X-AUTH-APIKEY", "{{apikey}}"));
		headers.add(header("X-Client-ID", "{{clientId}}"));
		request.add("header", headers);
		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("mode", "raw");
		requestBody.addProperty("raw", body);
		JsonObject raw = new JsonObject();
		raw.addProperty("language", "json");
		JsonObject options = new JsonObject();
		options.add("raw", raw);
		requestBody.add("options", options);
		request.add("body", requestBody);
		JsonObject item = new JsonObject();
		item.addProperty("name", "My Request");
		item.add("request", request);
		JsonArray items = new JsonArray();
		items.add(item);
		JsonObject collection = new JsonObject();
		collection.add("item", items);
		return Collection.load(collection).getRequest("My Request");
	}

	private static JsonObject header(String key, String value) {
		JsonObject header = new JsonObject();
		header.addProperty("key", key);
		header.addProperty("value", value);
		return header;
	}

	public static final class RuntimeRequestContext {
		private Request request;

		RuntimeRequestContext(Request request) {
			this.request = request;
		}

		public RuntimeSecureRequest request() {
			return new RuntimeSecureRequest(request);
		}

		public void request(Request request) {
			this.request = request;
		}
	}

	public static final class RuntimeSecureRequest {
		private final Request request;

		RuntimeSecureRequest(Request request) {
			this.request = request;
		}

		public Request request() {
			return request;
		}

		public String log(boolean resolve) {
			return request.log();
		}
	}

	private static Request requestWithoutHeaders() throws Exception {
		String json = "{\"item\":[{\"name\":\"Get current authenticated user\",\"request\":{"
				+ "\"method\":\"GET\",\"url\":\"https://example.com/auth/me\"}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject())
				.getRequest("Get current authenticated user");
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

	private static Request requestWithUnresolvedValuesInEveryRequestSection() throws Exception {
		String json = "{\"item\":[{\"name\":\"My Request\",\"request\":{\"method\":\"POST\","
				+ "\"url\":{\"raw\":\"https://example.com/consumption/{{hierarchy}}?limit={{limit}}\","
				+ "\"host\":[\"example\",\"com\"],\"path\":[\"consumption\",\"{{hierarchy}}\"],"
				+ "\"query\":[{\"key\":\"limit\",\"value\":\"{{limit}}\"}]},"
				+ "\"header\":[{\"key\":\"X-Token\",\"value\":\"{{token}}\"}],"
				+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"interval\\\":\\\"{{interval}}\\\"}\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}";
		return Collection.load(JsonParser.parseString(json).getAsJsonObject()).getRequest("My Request");
	}
}
