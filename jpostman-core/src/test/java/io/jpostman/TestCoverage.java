package io.jpostman;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/**
 * Validates collection parsing, component parsing, builder behavior,
 * environment resolution, and executor behavior.
 */
public class TestCoverage {

	private static final String TEST_USERNAME = "TEST_USER";
	private static final String ENV_TOKEN_KEY = "ENV_TOKEN";
	private static final String PRODUCT_FOLDER = "Product";
	private static final String IMAGE_FOLDER = "Dynamic Image";

	private static final String LOGIN_GET_TOKEN = "Login user and get tokens";
	private static final String GET_AUTH_USER = "Get current auth user";
	private static final String ALL_PRODUCTS = "Get all products";
	private static final String GENERATE_IMAGE = "Generate image with custom text";

	private static final String COLLECTION_FILE = "DummyJSON.postman_collection.json";
	private static final String ENVIRONMENT_FILE = "DummyJSON.postman_environment.json";

	private static final byte[] COLLECTION_BYTES;
	private static final byte[] ENVIRONMENT_BYTES;

	static {
		COLLECTION_BYTES = readAllBytes(COLLECTION_FILE);
		ENVIRONMENT_BYTES = readAllBytes(ENVIRONMENT_FILE);
	}

	private Collection col;
	private Environment env;
	private Folder folder;

	private static byte[] readAllBytes(String resourceName) {
		try (InputStream is = TestCoverage.class.getClassLoader().getResourceAsStream(resourceName)) {
			return is.readAllBytes();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read resource: " + resourceName, e);
		}
	}

	@BeforeClass
	public void load() throws Exception {
		col = Collection.load(TestCoverage.class.getClassLoader().getResourceAsStream(COLLECTION_FILE));
		col.print();

		col.loadEnvironment(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		env = col.getEnvironment();
		env.print();

		folder = col.getFolder(PRODUCT_FOLDER);
		folder.print();
	}

	// -------------------------------------------------------------------------
	// Test Environment
	// -------------------------------------------------------------------------

	@Test
	public void testLoadFileEnvironment() throws Exception {
		// environment file: loading by path should match classpath-loaded environment
		Environment env = Environment.load("src/test/resources/" + ENVIRONMENT_FILE);
		assertEquals(this.env.toString(), env.toString());

		// collection environment: load environment from file path
		col.loadEnvironment("src/test/resources/" + ENVIRONMENT_FILE);
	}

	@Test
	public void testLoadResourceEnvironment() throws Exception {
		// environment resource: loading by stream should match initialized environment
		Environment env = Environment.load(TestCoverage.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE));
		assertEquals(this.env.toString(), env.toString());
	}

	@Test
	public void testLoadStringEnvironment() throws Exception {
		Environment env;

		// environment: name present → parsed name
		env = Environment.load(JsonParser.parseString("{\"name\":\"TEST_NAME\"}").getAsJsonObject());
		assertEquals(env.getName(), "TEST_NAME");

		// environment: name absent → default name
		env = Environment.load(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(env.getName(), "Unknown Environment");
		env.print();

		// environment values: enabled=true → initially active; disabling removes from
		// active params view
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\", \"value\":\"v\",\"enabled\":true}]}")
						.getAsJsonObject());
		assertEquals(env.get("unknown"), null);
		assertEquals(env.raw("unknown"), null);
		assertEquals(env.entry("apikey").isEnabled(), true);
		assertEquals(env.entry("apikey").getValue(), "v");

		assertEquals(env.getParams().size(), 1);
		assertEquals(env.get("apikey"), "v");
		assertEquals(env.raw("apikey"), "v");
		env.entry("apikey").setEnabled(false);
		assertEquals(env.getParams().size(), 1);
		assertEquals(env.get("apikey"), null);
		assertEquals(env.raw("apikey"), "v");
		assertEquals(env.entry("apikey").toString(), "value=v, enabled=false");
		env.removeKey("apikey");
		assertEquals(env.get("apikey"), null);

		// environment values: enabled=false → initially inactive; enabling restores to
		// active params view
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\", \"value\":\"v\",\"enabled\":false}]}")
						.getAsJsonObject());
		assertEquals(env.getParams().size(), 1);
		assertEquals(env.entry("apikey").isEnabled(), false);
		env.entry("apikey").setEnabled(true);
		assertEquals(env.getParams().size(), 1);

		// environment values: key absent and enabled=false → skipped
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"value\":\"v\",\"enabled\":false}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: key absent and enabled=true → skipped
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"value\":\"v\",\"enabled\":true}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: value absent → stored as ""
		env = Environment.load(JsonParser.parseString("{\"values\":[{\"key\":\"apikey\"}]}").getAsJsonObject());
		assertEquals(env.get("apikey"), "");
		env.print();

		// environment values: values exists but is not array → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":\"not-array\"}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: non-object value entry → skipped
		env = Environment.load(JsonParser.parseString("{\"values\":[\"not-object\"]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: key is null → skipped
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"key\":null,\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(env.getParams().size(), 0);

		// environment values: enabled missing → treated as enabled
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"key\":\"k\",\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(env.get("k"), "v");

		// environment values: value is null → stored as ""
		env = Environment
				.load(JsonParser.parseString("{\"values\":[{\"key\":\"k\",\"value\":null}]}").getAsJsonObject());
		assertEquals(env.get("k"), "");

		// environment: name is null → default name
		env = Environment.load(JsonParser.parseString("{\"name\":null}").getAsJsonObject());
		assertEquals(env.getName(), "Unknown Environment");
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Environment key not found: 'NEW_KEY'")
	public void testEnvSetThrowWhenKeyNotFound() {
		Environment source = new Environment("Test Env");

		// environment builder: add creates new key
		Environment target1 = source.builder().add("NEW_KEY", TEST_USERNAME).end();

		// environment builder: set existing key updates value
		Environment target2 = target1.builder().set("NEW_KEY", ENV_TOKEN_KEY).end();

		// environment builder: resolve adds values from map
		Environment target3 = target2.builder().resolve(Map.of("OLD_KEY", TEST_USERNAME)).end();

		assertEquals(target1.toString(), "  NEW_KEY                             = " + TEST_USERNAME + "\n");
		assertEquals(target2.toString(), "  NEW_KEY                             = " + ENV_TOKEN_KEY + "\n");
		assertEquals(target3.toString(), "  NEW_KEY                             = " + ENV_TOKEN_KEY + "\n"
				+ "  OLD_KEY                             = " + TEST_USERNAME + "\n");
		assertEquals(source.resolve(null).toString(), "{}");

		// environment builder: source remains unchanged
		assertEquals(source.getParams().size(), 0);

		// environment builder: set missing key → throws
		source.builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Collection
	// -------------------------------------------------------------------------

	@Test
	public void testCollection() throws Exception {
		Collection col = Collection.load("src/test/resources/" + COLLECTION_FILE);

		// collection file: same source loaded by path → same parsed output
		assertEquals(this.col.toString(), col.toString());
		assertEquals(this.col.getRoot(), col.getRoot());
		assertEquals(this.col.getName(), col.getName());
		assertEquals(this.col.getFolders().toString(), col.getFolders().toString());
		assertEquals(this.col.getRequests().toString(), col.getRequests().toString());

		// collection lookup: unknown folder/request → throws exception
		assertThrows(IllegalArgumentException.class, () -> col.getFolder("UNKNOWN"));
		assertThrows(IllegalArgumentException.class, () -> col.getRequest("UNKNOWN"));

		// request summary: parsed request should match formatted output
		assertEquals(col.getRequest(GET_AUTH_USER).toString(),
				"[GET   ] Get current auth user                    -> {{base_url}}/auth/me");
	}

	@Test
	public void testCollectionFromString() throws Exception {
		Collection col;

		// collection: empty object → default unnamed collection
		col = Collection.load(JsonParser.parseString("{}").getAsJsonObject());
		col.print();

		// collection: info present but name absent → default name
		col = Collection.load(JsonParser.parseString("{\"info\": {}}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");

		// collection: item contains unnamed request/folder object without request →
		// skipped as folder/request
		col = Collection.load(JsonParser.parseString("{\"item\": [{}]}").getAsJsonObject());
		assertEquals(col.getFolders().size(), 0);

		// collection: folder with empty item array → folder exists with no requests
		col = Collection.load(JsonParser
				.parseString("{\"item\": [{\"name\": \"" + PRODUCT_FOLDER + "\", \"item\": []}]}").getAsJsonObject());
		assertEquals(col.getFolder(PRODUCT_FOLDER).toString(), "");
		col.print();

		// collection: root request without name → request name defaults to Unnamed
		col = Collection.load(JsonParser.parseString("{\"item\": [{\"request\":{}}]}").getAsJsonObject());
		assertEquals(col.getRequests().size(), 1);
		col.print();

		// collection: info exists but is null → default name
		col = Collection.load(JsonParser.parseString("{\"info\":null}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");

		// collection: info.name is null → default name
		col = Collection.load(JsonParser.parseString("{\"info\":{\"name\":null}}").getAsJsonObject());
		assertEquals(col.getName(), "Unnamed Collection");

		// collection: item exists but is not array → no folders/requests
		col = Collection.load(JsonParser.parseString("{\"item\":\"invalid\"}").getAsJsonObject());
		assertEquals(col.getFolders().size(), 0);
		assertEquals(col.getRequests().size(), 0);
	}

	@Test
	public void testParamsPropsOverridesWithNonEmptySystemProperties() {
		System.setProperty("PARAMS_EMPTY", "");
		System.setProperty("PARAMS_TOKEN", "system-token");
		try {
			Map<String, String> source = Map.of("PARAMS_EMPTY", "default-empty", "PARAMS_NAME", "default-name",
					"PARAMS_TOKEN", "default-token");

			Map<String, String> result = Params.props(source, null);

			assertEquals(result.get("PARAMS_EMPTY"), "default-empty");
			assertEquals(result.get("PARAMS_NAME"), "default-name");
			assertEquals(result.get("PARAMS_TOKEN"), "system-token");
			assertEquals(source.get("PARAMS_TOKEN"), "default-token");

			result = Params.props(source, Set.of("PARAMS_EMPTY", "PARAMS_TOKEN"));

			assertEquals(result.get("PARAMS_EMPTY"), "default-empty");
			assertEquals(result.get("PARAMS_NAME"), null);
			assertEquals(result.get("PARAMS_TOKEN"), "system-token");
			assertEquals(source.get("PARAMS_TOKEN"), "default-token");

			result = Params.props(source, Set.of("PARAMS_TOKEN"));

			assertEquals(result.get("PARAMS_EMPTY"), null);
			assertEquals(result.get("PARAMS_NAME"), null);
			assertEquals(result.get("PARAMS_TOKEN"), "system-token");
			assertEquals(source.get("PARAMS_TOKEN"), "default-token");

			Environment env = new Environment("Test Env");
			assertEquals(Params.props(env, Set.of()).size(), 0);

			assertEquals(Params.props(null).size(), 0);
		} finally {
			System.clearProperty("PARAMS_TOKEN");
			System.clearProperty("PARAMS_EMPTY");
		}
	}

	@Test
	public void testParamsMapJsonPath() {
		// asMap: creates an ordered map and keeps values as-is, including lists.
		Map<String, ?> result1 = Params.asMap("key1", "value", "key2", Params.asList(1, 2, 3));
		assertEquals(result1.toString(), "{key1=value, key2=[1, 2, 3]}");
		assertEquals(Params.asMap((Object[]) null).toString(), "{}");
		assertEquals(Params.asMap().toString(), "{}");

		// asJson: JSON-stringifies String values, but keeps nested maps and primitive
		// values as-is.
		Map<String, ?> result2 = Params.asJson("key3", "value", "key4",
				Params.asMap("key5", true, "key6", 12.34, "key7", null));
		assertEquals(result2.toString(), "{key3=\"value\", key4={key5=true, key6=12.34, key7=null}}");

		// copy: merges maps into a new ordered map; later maps are appended and
		// override duplicate keys.
		assertEquals(Params.copy(result1, result2).toString(),
				"{key1=value, key2=[1, 2, 3], key3=\"value\", key4={key5=true, key6=12.34, key7=null}}");

		// copy: ignores null maps.
		assertEquals(Params.copy(result1, null).toString(), "{key1=value, key2=[1, 2, 3]}");

		// substituteVars: null vars → original value returned
		assertEquals(Params.substituteVars("{{username}}", null), "{{username}}");

		// param helper: null input should not throw
		Params.substituteVars(null, null);

		assertEquals(Params.path(this.col.getRoot(), "info.name"), "DummyJSON");

		// Covers: element == null
		assertThrows(IllegalArgumentException.class, () -> Params.path(JsonParser.parseString("null"), "info.name"));

		// Covers: element.isJsonNull()
		assertThrows(IllegalArgumentException.class,
				() -> Params.path(JsonParser.parseString("{\"info\":{\"name\":null}}"), "info.name"));

		JsonElement root = JsonParser.parseString(
				"{\"text\":\"hello\"," + "\"flag\":true," + "\"intValue\":123," + "\"longValue\":9999999999999,"
						+ "\"doubleValue\":12.5," + "\"array\":[1,2,3]," + "\"object\":{\"name\":\"JPostman\"}}");

		// String primitive branch
		assertEquals(Params.path(root, "text"), "hello");

		// Boolean primitive branch
		assertEquals(Params.path(root, "flag"), Boolean.TRUE);

		// Number branch: Integer
		assertEquals(Params.path(root, "intValue"), Integer.valueOf(123));

		// Number branch: Integer parse fails, then Long branch
		assertEquals(Params.path(root, "longValue"), Long.valueOf(9999999999999L));

		// Number branch: decimal becomes Double
		assertEquals(Params.path(root, "doubleValue"), Double.valueOf(12.5));

		// JsonArray branch: returns JsonElement itself
		JsonElement array = Params.path(root, "array");
		assertTrue(array.isJsonArray());
		assertEquals(array.getAsJsonArray().size(), 3);

		// JsonObject branch: returns JsonElement itself
		JsonElement object = Params.path(root, "object");
		assertTrue(object.isJsonObject());
		assertEquals(object.getAsJsonObject().get("name").getAsString(), "JPostman");

		root = JsonParser.parseString(
				"{\"intValue\": 123, \"doubleValue\": 12.5, \"lowerExponent\": 1e3, \"upperExponent\": 1E3}");
		assertEquals(Params.path(root, "intValue"), Integer.valueOf(123));
		assertEquals(Params.path(root, "doubleValue"), Double.valueOf(12.5));
		assertEquals(Params.path(root, "lowerExponent"), Double.valueOf(1000.0));
		assertEquals(Params.path(root, "upperExponent"), Double.valueOf(1000.0));

		root = JsonParser.parseString("{\"info\":{\"name\":\"DummyJSON\",\"empty\":null},"
				+ "\"products\":[{\"id\":1,\"title\":\"Phone\"}]}");

		// Covers: after simple object access, final current.isJsonNull() ? null :
		// current
		assertEquals(Params.pathElement(root, "info.empty"), null);

		// Covers: fieldName lookup returns null inside bracket path
		assertEquals(Params.pathElement(root, "missing[0]"), null);

		// Covers: fieldName lookup returns JsonNull inside bracket path
		assertEquals(Params.pathElement(root, "info.empty[0]"), null);

		// Covers: current becomes null after first segment, next loop checks current ==
		// null
		assertEquals(Params.pathElement(root, "info.missing.name"), null);

		// Covers: current becomes JsonNull after first segment, next loop checks
		// current.isJsonNull()
		assertEquals(Params.pathElement(root, "info.empty.name"), null);

		// Covers: simple field access where current is not object
		assertEquals(Params.pathElement(root, "products.id"), null);

		// Covers: bracket access where current is not array
		assertEquals(Params.pathElement(root, "info[0]"), null);
	}

	@Test
	public void testParamsPathElement() {
		JsonElement root = JsonParser.parseString("{\"info\":{\"name\":\"DummyJSON\",\"nullValue\":null},"
				+ "\"products\":[{\"id\":1,\"title\":\"Phone\"},{\"id\":2,\"title\":\"Laptop\"}],"
				+ "\"matrix\":[[10,20],[30,40]]}");

		// root == null
		assertEquals(Params.pathElement(null, "info.name"), null);

		// root.isJsonNull()
		assertEquals(Params.pathElement(JsonParser.parseString("null"), "info.name"), null);

		// path == null
		assertEquals(Params.pathElement(root, null), root);

		// path.trim().isEmpty()
		assertEquals(Params.pathElement(root, "   "), root);

		// normal object field access
		assertEquals(Params.pathElement(root, "info.name").getAsString(), "DummyJSON");

		// simple object field access on non-object returns null
		assertEquals(Params.pathElement(root, "products.id"), null);

		// object field exists but value is JsonNull
		assertEquals(Params.pathElement(root, "info.nullValue"), null);

		// bracket field access: products[0].id
		assertEquals(Params.pathElement(root, "products[0].id").getAsInt(), 1);

		// bracket access directly on array: [1][0]
		assertEquals(Params.pathElement(root.getAsJsonObject().get("matrix"), "[1][0]").getAsInt(), 30);

		// field before bracket but current is not object
		assertEquals(Params.pathElement(root.getAsJsonObject().get("products"), "products[0]"), null);

		// missing closing bracket
		assertThrows(IllegalArgumentException.class, () -> Params.pathElement(root, "products[0"));

		// invalid array index text
		assertThrows(NumberFormatException.class, () -> Params.pathElement(root, "products[x]"));

		// bracket segment points to non-array
		assertEquals(Params.pathElement(root, "info[0]"), null);

		// negative index
		assertEquals(Params.pathElement(root, "products[-1]"), null);

		// index out of range
		assertEquals(Params.pathElement(root, "products[99]"), null);
	}

	// -------------------------------------------------------------------------
	// Test Folder
	// -------------------------------------------------------------------------

	@Test
	public void testFolder() throws Exception {
		Collection col;
		Folder folder;
		Request req;

		// folder: empty item array → zero requests
		col = Collection.load(JsonParser
				.parseString("{\"item\": [{\"name\": \"" + PRODUCT_FOLDER + "\", \"item\": []}]}").getAsJsonObject());
		folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.getRequests().size(), 0);
		folder.print();

		// folder: request without name → request name defaults to Unnamed
		col = Collection.load(
				JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[{\"request\":{}}]}]}")
						.getAsJsonObject());

		Folder productFolder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(productFolder.getRequests().size(), 1);
		assertThrows(IllegalArgumentException.class, () -> productFolder.getRequest("UNKNOWN"));

		// folder request: unnamed empty request → default GET and empty URL
		req = productFolder.getRequest("Unnamed");
		assertEquals(req.toString(), "[GET   ] Unnamed                                  -> ");
		assertEquals(req, req.build());
		folder.print();

		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER
				+ "\",\"item\":[{\"name\":\"Users\",\"request\":{\"method\":\"GET\",\"url\":\"https://example.com/users\"}}]}]}")
				.getAsJsonObject());
		folder = col.getFolder(PRODUCT_FOLDER);
		assertEquals(folder.toDebugString(), "=== Folder: Product (1 request) ===\n"
				+ "    [GET   ] Users                                    -> https://example.com/users\n");
	}

	// -------------------------------------------------------------------------
	// Test Auth
	// -------------------------------------------------------------------------

	@Test
	public void testAuthSet() throws Exception {
		Request req;
		Request template = col.getRequest(GET_AUTH_USER);

		// auth builder: set existing token value
		Auth auth = template.getAuth().builder().set("token", ENV_TOKEN_KEY).end();
		assertEquals(auth.toString(), "[bearer] {token=" + ENV_TOKEN_KEY + "}");

		// request builder: auth add with env resolution
		Environment newEnv = env.builder().add("accessToken", ENV_TOKEN_KEY).end();
		req = template.builder().auth().add("TEST_USERNAME", TEST_USERNAME).end().build(newEnv);
		assertEquals(req.getAuth().toString(),
				"[bearer] {token=" + ENV_TOKEN_KEY + ", TEST_USERNAME=" + TEST_USERNAME + "}");

		// request builder: lambda auth add with env resolution
		req = template.builder().auth(c -> c.add("TEST_USERNAME", TEST_USERNAME)).build(newEnv);
		assertEquals(req.getAuth().toString(),
				"[bearer] {token=" + ENV_TOKEN_KEY + ", TEST_USERNAME=" + TEST_USERNAME + "}");

		// auth builder: original template remains unchanged
		assertEquals(template.getAuth().toString(), "[bearer] {token={{accessToken}}}");
	}

	@Test
	public void testAuthSetFromString() throws Exception {
		Auth auth;

		// auth absent → noauth
		auth = Auth.from(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth is JSON null → noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":null}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth present but type absent → noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":{}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth type present but matching array absent → no params
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"noauth\"}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		assertEquals(auth.toString(), "[noauth]");
		assertEquals(auth.isNoAuth(), true);
		assertEquals(auth.getParams().size(), 0);
		auth.print();

		// v2.1 array: non-object element → skipped
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[\"not-an-object\"]}}")
				.getAsJsonObject());
		assertEquals(auth.toString(), "[bearer] {}");

		// v2.1 array: key absent → skipped
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"value\":\"tok\"}]}}")
				.getAsJsonObject());
		assertEquals(auth.toString(), "[bearer] {}");

		// v2.1 array: key absent → skipped
		auth = Auth
				.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"value\":\"{{TOKEN}}\"}]}}")
						.getAsJsonObject());
		assertEquals(auth.getRaw().toString(), "[{\"value\":\"{{TOKEN}}\"}]");
		assertEquals(auth.params().toString(), "{TOKEN=}");
		assertEquals(Params.resolve(auth.params(), Map.of("TOKEN", "abc")).toString(), "{TOKEN=abc}");
		assertEquals(Params.resolve(auth.params(), null).toString(), "{}");
		assertEquals(Params.resolve(null, Map.of("TOKEN", "abc")).toString(), "{}");
		assertEquals(Params.resolve(auth.params(), Map.of("UNKNOWN", "abc")).toString(), "{TOKEN=}");
		assertEquals(Params.addTokens(Map.of("TOKEN", "abc"), (String) null).toString(), "{}");
		assertEquals(Params.addTokens(Map.of("TOKEN", "abc"), (Map<String, String>) null).toString(), "{}");
		assertEquals(Params.addTokens(null, (String) null).toString(), "{}");
		assertEquals(Params.addTokens(null, (Map<String, String>) null).toString(), "{}");

		// assertEquals(Params.addTokens(auth.params(), Map.of("UNKNOWN",
		// "abc")).toString(), "{TOKEN=}");

		// v2.1 array: value absent → stored as ""
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\"}]}}")
				.getAsJsonObject());
		assertEquals(auth.get("token"), "");
		assertEquals(auth.getParams().entrySet().stream().anyMatch(h -> "token".equals(h.getKey())), true);
		auth.print();

		// v2.0 object: primitive value → stored directly; non-primitive value → stored
		// as ""
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":\"basic\",\"basic\":{\"TEST_USERNAME\":\""
				+ TEST_USERNAME + "\",\"nested\":{\"a\":1}}}}").getAsJsonObject());
		assertEquals(auth.get("TEST_USERNAME"), TEST_USERNAME);
		assertEquals(auth.get("nested"), "");

		// auth exists but is not object → noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":\"invalid\"}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");
		assertEquals(auth.getParams().size(), 0);

		// auth type is null → defaults to noauth
		auth = Auth.from(JsonParser.parseString("{\"auth\":{\"type\":null}}").getAsJsonObject());
		assertEquals(auth.getType(), "noauth");

		// auth array entry: key is null and value is null → skipped
		auth = Auth.from(
				JsonParser.parseString("{\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":null,\"value\":null}]}}")
						.getAsJsonObject());
		assertEquals(auth.getParams().size(), 0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Auth key not found: 'NEW_KEY'")
	public void testAuthSetThrowWhenKeyNotFound() {
		Request template = col.getRequest(GET_AUTH_USER);

		// auth builder: add creates new key on cloned auth
		Auth auth = template.getAuth().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(auth.toString(), "[bearer] {token={{accessToken}}, NEW_KEY=" + TEST_USERNAME + "}");

		// auth builder: set missing key on original auth → throws
		template.getAuth().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Header
	// -------------------------------------------------------------------------

	@Test
	public void testHeaderSet() {
		Request req;
		Request template = col.getRequest(GET_AUTH_USER);

		// header builder: set existing Content-Type value
		Header header = template.getHeader().builder().set("Content-Type", ENV_TOKEN_KEY).end();
		assertEquals(header.toString(), "  Content-Type                        = " + ENV_TOKEN_KEY + "\n");

		// request builder: headers add with env resolution
		req = template.builder().headers().add("token", ENV_TOKEN_KEY).end().build(env);
		assertEquals(req.getHeader().toString(), "  Content-Type                        = application/json\n"
				+ "  token                               = " + ENV_TOKEN_KEY + "\n");

		// request builder: lambda headers add with env resolution
		req = template.builder().headers(c -> c.add("token", ENV_TOKEN_KEY)).build(env);
		assertEquals(req.getHeader().toString(), "  Content-Type                        = application/json\n"
				+ "  token                               = " + ENV_TOKEN_KEY + "\n");

		// header builder: original template remains unchanged
		assertEquals(template.getHeader().toString(), "  Content-Type                        = application/json\n");
		assertEquals(template.getHeader().getParams().size(), 1);
	}

	@Test
	public void testHeaderSetFromString() throws Exception {
		Header header;

		// header field absent → empty
		header = Header.from(JsonParser.parseString("{}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);
		header.print();

		// header field is not JsonArray → empty
		header = Header.from(JsonParser.parseString("{\"header\":\"not-an-array\"}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: non-object element → skipped
		header = Header.from(JsonParser.parseString("{\"header\":[\"not-an-object\"]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: key absent → skipped
		header = Header.from(JsonParser.parseString("{\"header\":[{\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: value absent → stored as ""
		header = Header.from(JsonParser.parseString("{\"header\":[{\"key\":\"X-NoVal\"}]}").getAsJsonObject());
		assertEquals(header.get("X-NoVal"), "");
		header.print();

		// v2.1 array: key + value + disabled=false → stored
		header = Header.from(JsonParser
				.parseString("{\"header\":[{\"key\":\"Accept\",\"value\":\"application/json\",\"disabled\":false}]}")
				.getAsJsonObject());
		assertEquals(header.get("Accept"), "application/json");

		// v2.1 array: key is null → skipped
		header = Header.from(JsonParser.parseString("{\"header\":[{\"key\":null,\"value\":\"v\"}]}").getAsJsonObject());
		assertEquals(header.isEmpty(), true);

		// v2.1 array: value is null → stored as empty string
		header = Header.from(
				JsonParser.parseString("{\"header\":[{\"key\":\"X-NullValue\",\"value\":null}]}").getAsJsonObject());
		assertEquals(header.get("X-NullValue"), "");

		// header object: disabled header is stored but skipped during request
		// preparation
		Collection col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + GET_AUTH_USER + "\","
				+ "\"request\":{\"url\":{\"raw\":\"{{base_url}}\"},"
				+ "\"header\":[{\"key\":\"Accept\",\"value\":\"application/json\",\"disabled\":true},{\"key\":\"appkey\",\"value\":\"{{API_KEY}}\"}]}}]}")
				.getAsJsonObject());
		Request template = col.getRequest(GET_AUTH_USER);
		Request req = template.builder().build(env);
		assertEquals(req.getHeader().get("unknonwn"), null);
		assertEquals(req.getHeader().get("Accept"), null);
		assertEquals(req.getHeader().toString(), "  appkey                              = \n");
		req.getHeader().getParam("Accept").setEnabled(true); // enabled
		assertEquals(req.getHeader().get("Accept"), "application/json");
		assertEquals(req.getHeader().toString(), "  Accept                              = application/json\n"
				+ "  appkey                              = \n");
		assertEquals(template.getHeader().get("appkey"), "{{API_KEY}}");
		assertEquals(template.getHeader().params().toString(), "{API_KEY=}");
		assertEquals(req.getHeader().get("appkey"), "");
		assertEquals(req.getHeader().params().toString(), "{}");
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Header key not found: 'NEW_KEY'")
	public void testHeaderSetThrowWhenKeyNotFound() {
		Request template = col.getRequest(GET_AUTH_USER);

		// header builder: add creates new key on cloned header
		Header header = template.getHeader().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(header.toString(), "  Content-Type                        = application/json\n"
				+ "  NEW_KEY                             = " + TEST_USERNAME + "\n");

		// header builder: set missing key on original header → throws
		template.getHeader().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Url
	// -------------------------------------------------------------------------

	@Test
	public void testUrlSet() throws Exception {
		Collection col;
		Request template;
		Request req;

		this.col.print();

		// url object: raw URL and query array with text variable → parsed URL
		template = this.col.getFolder(IMAGE_FOLDER).getRequest(GENERATE_IMAGE);
		assertEquals(template.getUrl().isEmpty(), false);

		Url url = template.getUrl().builder().set("text", "Hello World").end();
		assertEquals(url.getOriginal(), "{{base_url}}/image/400x200/008080/ffffff?text=JPostman");
		assertEquals(url.toString(), "{{base_url}}/image/400x200/008080/ffffff?text=Hello World");
		assertEquals(url.toDebugString(),
				"=== Original URL: {{base_url}}/image/400x200/008080/ffffff?text=JPostman ===\n"
						+ "{{base_url}}/image/400x200/008080/ffffff?text=Hello World");
		req = template.builder().url().set("text", "Hello World").end().build(env);
		req.getUrl().print();

		assertEquals(url.params().toString(), "{base_url=}");

		url = new Url("http://example.com/products?id=25&&type=book&");
		assertEquals(url.getRaw(), "http://example.com/products");
		assertEquals(url.get("id"), "25");
		assertEquals(url.get("type"), "book");
		assertEquals(url.getParams().size(), 2);
		assertEquals(url.toString(), "http://example.com/products?id=25&type=book");

		url = new Url("http://example.com/products?=invalid&id=25");
		assertEquals(url.getRaw(), "http://example.com/products");
		assertEquals(url.get("id"), "25");
		assertEquals(url.getParams().size(), 1);
		assertEquals(url.toString(), "http://example.com/products?id=25");

		url = new Url("http://example.com/products?id");
		assertEquals(url.getRaw(), "http://example.com/products");
		assertEquals(url.get("id"), "");
		assertEquals(url.getParams().size(), 1);
		assertEquals(url.toString(), "http://example.com/products?id=");

		Environment env = new Environment("Test Env").builder().add("base_url", "https://dummyjson.com")
				.add("image_text", "Original").end();

		// url builder: set existing query parameter value
		req = template.builder().url(q -> q.set("text", "JPostman")).build(env);

		// url object: raw URL has url and fragment → url update preserves fragment
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder().url(q -> q.set("text", "JPostman")).build(env);

		assertEquals(template.getUrl().get("text"), "{{image_text}}");
		assertEquals(req.getUrl().get("text"), "JPostman");
		assertEquals(req.toUrl(), env.get("base_url") + "/image?text=JPostman#preview");

		// url object: disabled query parameter is skipped during URL rebuild while
		// original URL and fragment are preserved
		col = Collection.load(JsonParser
				.parseString("{\"item\":[{\"name\":\"" + GET_AUTH_USER + "\","
						+ "\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}#preview\","
						+ "\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\",\"disabled\":true}]}}}]}")
				.getAsJsonObject());
		template = col.getRequest(GET_AUTH_USER);
		req = template.builder().build(env);
		assertEquals(req.getUrl().get("unknonwn"), null);
		assertEquals(req.getUrl().get("text"), null);
		assertEquals(req.getUrl().toString(), "https://dummyjson.com/image#preview");
		assertEquals(req.getUrl().getOriginal(), "{{base_url}}/image?text={{image_text}}#preview");
		assertEquals(req.toUrl(), "https://dummyjson.com/image#preview");
		req.getUrl().getParam("text").setEnabled(true); // enabled
		assertEquals(req.getUrl().get("text"), "{{image_text}}");
		assertEquals(req.getUrl().toString(), "https://dummyjson.com/image?text={{image_text}}#preview");
		assertEquals(req.getUrl().getOriginal(), "{{base_url}}/image?text={{image_text}}#preview");
		assertEquals(req.toUrl(), "https://dummyjson.com/image?text={{image_text}}#preview");

		// raw URL has no query string but url.query[] exists
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image#preview\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		req = template.builder().url(q -> q.set("text", "JPostman")).build(env);

		assertEquals(template.getUrl().get("text"), "{{image_text}}");
		assertEquals(req.getUrl().get("text"), "JPostman");
		assertEquals(req.toUrl(), env.get("base_url") + "/image?text=JPostman#preview");

		url = new Url(null);
		assertEquals(url.getRaw(), "");
		assertEquals(url.getOriginal(), "");
	}

	@Test
	public void testUrlSetFromString() throws Exception {
		Collection col;
		Request template;

		// url object: raw URL without query parameters → non-empty URL with empty
		// params
		col = Collection.load(JsonParser
				.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[" + "{\"name\":\""
						+ GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}" + "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// url object: query array key/value → parsed
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{image_text}}\"}]}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// url object: query field is null → raw query string still parsed
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":null}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: non-object element skipped; raw query string still
		// parsed
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[" + "{\"name\":\"" + GET_AUTH_USER
						+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[1]}}}"
						+ "]}]}")
				.getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: missing key skipped; raw query string still parsed
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true}]}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: null key/value skipped; raw query string still parsed
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": false, \"key\": null, \"value\": null}]}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);

		// v2.1 url query array: disabled key/value skipped; raw query string still
		// parsed
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"name\":\"" + GET_AUTH_USER
				+ "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image?text={{image_text}}\",\"query\":[{\"disabled\": true, \"key\": \"user\", \"value\": null}]}}}"
				+ "]}]}").getAsJsonObject());
		template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);
		assertEquals(template.getUrl().isEmpty(), false);
	}

	@Test
	public void testUrlParsingFromRawString() {
		// raw URL: base URL, query parameters, and fragment are separated and rebuilt
		Url url = new Url("http://{{base_url}}/users?id={{id}}&type=book#preview");
		assertEquals(url.getRaw(), "http://{{base_url}}/users#preview");
		assertEquals(url.get("id"), "{{id}}");
		assertEquals(url.get("type"), "book");
		assertEquals(url.isEmpty(), false);
		assertEquals(url.toString(), "http://{{base_url}}/users?id={{id}}&type=book#preview");
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "URL query parameter not found: 'NEW_KEY'")
	public void testUrlSetThrowWhenKeyNotFound() throws Exception {
		Collection col = Collection.load(JsonParser
				.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[" + "{\"name\":\""
						+ GET_AUTH_USER + "\",\"request\":{\"url\":{\"raw\":\"{{base_url}}/image\"}}}" + "]}]}")
				.getAsJsonObject());
		Request template = col.getFolder(PRODUCT_FOLDER).getRequest(GET_AUTH_USER);

		// url builder: add creates new key on cloned url
		Url url = template.getUrl().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(url.toString(), "{{base_url}}/image?NEW_KEY=" + TEST_USERNAME);

		// url builder: set missing key on original url → throws
		template.getUrl().builder().set("NEW_KEY", TEST_USERNAME);
	}

	// -------------------------------------------------------------------------
	// Test Body
	// -------------------------------------------------------------------------

	@Test
	public void testBodySet() throws Exception {
		Request req;
		Request template = col.getRequest(LOGIN_GET_TOKEN);
		template.print();

		// body builder: set existing username value
		Body body = template.getBody().builder().set("username", TEST_USERNAME).end();
		assertEquals(body.toString(),
				"[raw/json] {\n" + "  \"username\": \"TEST_USER\",\n" + "  \"password\": \"{{password}}\"\n" + "}");

		// request builder: body set with env resolution
		req = template.builder().body().set("username", TEST_USERNAME).end().build(env);
		assertEquals(req.getBody().toString(),
				"[raw/json] {\n" + "  \"username\": \"TEST_USER\",\n" + "  \"password\": \"emilyspass\"\n" + "}");

		// request builder: lambda body set with env resolution
		req = template.builder().body(c -> c.set("username", TEST_USERNAME)).build(env);
		assertEquals(req.getBody().toString(),
				"[raw/json] {\n" + "  \"username\": \"TEST_USER\",\n" + "  \"password\": \"emilyspass\"\n" + "}");

		// The resolved body only contains tokens that are still unresolved.
		assertEquals(body.params().toString(), "{password=}");

		// The original template still contains all template tokens.
		assertEquals(template.getBody().params().toString(), "{username=, password=}");
	}

	@Test
	public void testBodyNewSet() throws Exception {
		Request template = col.getRequest(GET_AUTH_USER);

		// body builder: add supports number, boolean, and list values
		Body body = template.getBody().builder().add("count", 25).add("active", true).add("tags", List.of(1, true, "$"))
				.end();

		assertEquals(body.toString(), "[none] {\"count\":25,\"active\":true,\"tags\":[1,true,\"$\"]}");
	}

	@Test
	public void testBodySetFromString() throws Exception {
		Body body;
		Body resolved;

		// body is JSON null → mode "none"
		body = Body.from(JsonParser.parseString("{\"body\":null}").getAsJsonObject());
		assertEquals(body.toString(), "[none]");

		// body mode is null → mode "none"
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\": null}}").getAsJsonObject());
		assertEquals(body.toString(), "[none]");
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);
		body.print();

		// raw mode: raw key absent → raw=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\"}}").getAsJsonObject());
		assertEquals(body.toString(), "[raw] ");
		assertEquals(body.getMode(), "raw");
		assertEquals(body.getRaw(), "");
		assertEquals(body.getParsed(), null);
		assertEquals(body.isEmpty(), true);

		// raw mode: non-empty raw body → not empty
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "raw");
		assertEquals(body.getRaw(), "hello");
		assertEquals(body.isEmpty(), false);

		// getString: array value → default returned
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":[1,2,3]}}").getAsJsonObject());
		assertEquals(body.getRaw(), "");

		// raw mode: options is null
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":null}}")
				.getAsJsonObject());
		assertEquals(body.getLanguage(), "");

		// raw mode: options absent/empty → language=""
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{}}}")
				.getAsJsonObject());
		assertEquals(body.getLanguage(), "");
		assertEquals(body.toString(), "[raw] hello");

		// raw mode: options.raw exists but is not object → language defaults to ""
		body = Body.from(
				JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{\"raw\":\"json\"}}}")
						.getAsJsonObject());
		assertEquals(body.getLanguage(), "");
		assertEquals(body.toString(), "[raw] hello");

		// raw mode: options.raw exists but language absent → language=""
		body = Body
				.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"hello\",\"options\":{\"raw\":{}}}}")
						.getAsJsonObject());
		assertEquals(body.getLanguage(), "");

		// graphql mode: graphql field absent → raw graphql payload is empty; builder
		// creates an empty object
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\"}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");

		// graphql mode: graphql field is null → raw graphql payload is empty; builder
		// creates an empty object
		body = Body
				.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\",\"graphql\":null}}").getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{}");
		assertEquals(body.toString(), "[graphql]");

		// graphql mode: graphql query object is stored as raw text; builder starts from
		// an empty object
		body = Body
				.from(JsonParser.parseString("{\"body\":{\"mode\":\"graphql\",\"graphql\":{\"query\":\"{ hero }\"}}}")
						.getAsJsonObject());
		assertEquals(body.builder().end().getRaw(), "{\"query\":\"{ hero }\"}");
		assertEquals(body.toString(), "[graphql] {\"query\":\"{ hero }\"}");

		// raw/json: valid JSON array → parsed as JsonArray and builder preserves array
		// body
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[\\\"{{base_url}}\\\",\\\"{{username}}\\\",\\\"{{password}}\\\"]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertNotNull(body.getParsed());
		assertEquals(body.getParsed().isJsonArray(), true);
		assertEquals(body.builder().end().getRaw(), "[\"{{base_url}}\",\"{{username}}\",\"{{password}}\"]");
		assertEquals(Params.substituteVars(body.builder().end().getRaw(), env.getParams()),
				"[\"https://dummyjson.com\",\"emilys\",\"emilyspass\"]");

		// raw/json: invalid JSON raw string → parse failure ignored, mode remains raw
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[{null}]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "raw");

		// formdata mode: explicit null payload → mode preserved, but body is empty
		body = Body.from(
				JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\": null}}").getAsJsonObject());
		assertEquals(body.getMode(), "formdata");

		// formdata mode: invalid JSON-looking string → preserved as a string primitive
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{null}]\"}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");

		// formdata mode: JSON array encoded as a string → parsed and serialized as
		// array
		body = Body.from(JsonParser
				.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"[{\\\"key\\\":\\\"file\\\"}]\"}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
		assertEquals(body.getRaw(), "[{\"key\":\"file\"}]");
		assertEquals(body.toString(), "[formdata] [{\"key\":\"file\"}]");

		// urlencoded mode: missing payload → mode preserved with empty body
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"urlencoded\"}}").getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");

		// urlencoded mode: explicit null payload → mode preserved with empty body
		body = Body.from(
				JsonParser.parseString("{\"body\":{\"mode\":\"urlencoded\", \"urlencoded\": null}}").getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");

		// urlencoded mode: Postman-style array payload → serialized as JSON array
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"urlencoded\",\"urlencoded\":[{\"key\":\"username\",\"value\":\"{{username}}\"}]}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "urlencoded");
		assertEquals(body.getRaw(), "[{\"key\":\"username\",\"value\":\"{{username}}\"}]");
		assertEquals(Params.substituteVars(body.getRaw(), env.getParams()),
				"[{\"key\":\"username\",\"value\":\"emilys\"}]");

		// formdata mode: object payload → serialized as JSON object
		body = Body.from(JsonParser
				.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":{\"username\":\"{{username}}\"}}}")
				.getAsJsonObject());
		assertEquals(body.getMode(), "formdata");
		assertEquals(body.getRaw(), "{\"username\":\"{{username}}\"}");
		assertEquals(Params.substituteVars(body.getRaw(), env.getParams()), "{\"username\":\"emilys\"}");

		// formdata mode: primitive boolean payload → preserved as primitive fallback
		body = Body
				.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":true}}").getAsJsonObject());
		assertEquals(body.getParsed().isJsonPrimitive(), true);
		assertEquals(body.getRaw(), "true");

		// formdata mode: blank string payload → treated as empty body so GET requests
		// do not send ""
		body = Body
				.from(JsonParser.parseString("{\"body\":{\"mode\":\"formdata\",\"formdata\":\"\"}}").getAsJsonObject());
		assertEquals(body.getRaw(), "");
		assertEquals(body.isEmpty(), true);

		// raw/json resolve: number value → not substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"count\\\":42}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("count", "99")).end();
		assertEquals(resolved.getRaw(), "{\"count\":42}");

		// raw/json resolve: non-string nested value → not substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"meta\\\":{\\\"v\\\":1}}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("v", "replaced")).end();
		assertEquals(resolved.getRaw(), "{\"meta\":{\"v\":1}}");

		// raw/json resolve: array string values → recursively substituted
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[\\\"{{base_url}}\\\",\\\"{{username}}\\\"]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());

		resolved = body.builder().resolve(env.getParams()).end();
		assertEquals(resolved.getRaw(), "[\"https://dummyjson.com\",\"emilys\"]");

		// body field is primitive instead of object → defaults to NONE body
		body = Body.from(JsonParser.parseString("{\"body\":\"invalid\"}").getAsJsonObject());
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);

		// raw/json resolve: null array item → preserved
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[null,\\\"{{username}}\\\"]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(env.getParams()).end();
		assertEquals(resolved.getRaw(), "[null,\"emilys\"]");

		// null request object → NONE body
		body = Body.from(null);
		assertEquals(body.getMode(), "none");
		assertEquals(body.isEmpty(), true);
	}

	@Test
	public void testBodyHandlebarsTemplatesAndSimpleMutations() {
		// raw/json body can contain a template fragment that is not valid JSON by
		// itself.
		// Handlebars resolution must preserve the original raw body instead of
		// converting it to {}.
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"<id>{{USER_ID}}</id>\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		Body resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
		assertEquals(resolved.getRaw(), "<id>42</id>");

		// Missing variables use normal Handlebars behavior: unresolved values become
		// empty strings.
		assertEquals(Params.substituteVars("<id>{{UNKNOWN_ID}}</id>", Map.of("USER_ID", "42")), "<id></id>");
		assertEquals(Params.substituteVars("<id>{{ USER_ID }}</id><missing>{{UNKNOWN_ID}}</missing>",
				Map.of("USER_ID", "42")), "<id>42</id><missing></missing>");
		assertEquals(Params.substituteVars("<id>{{UNKNOWN_ID}</id>", Map.of("USER_ID", "42")),
				"<id>{{UNKNOWN_ID}</id>");

		// format: quoted JSON string body is returned without JSON quotes.
		body = Body.from(JsonParser.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"\\\"<id>42</id>\\\"\","
				+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}").getAsJsonObject());
		assertEquals(body.pretty(), "<id>42</id>");
		assertEquals(Body.pretty(body.getParsed()), "<id>42</id>");
		assertEquals(Body.pretty(null), "");

		// format: non-string JSON primitive is formatted by Gson.
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"true\"," + "\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertEquals(body.pretty(), "true");

		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"id\\\":\\\"{{UNKNOWN_ID}}\\\"}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		assertEquals(body.builder().resolve(Map.of("USER_ID", "42")).end().getRaw(), "{\"id\":\"\"}");

		// Body mutation is intentionally simple: top-level JSON object keys only.
		// Template replacement is delegated to Handlebars through Params
		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"id\\\":\\\"{{USER_ID}}\\\",\\\"role\\\":\\\"{{ROLE}}\\\"}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().set("id", "007").add("traceId", "abc-123").resolve(Map.of("ROLE", "admin")).end();
		assertEquals(resolved.getRaw(), "{\"id\":\"007\",\"role\":\"admin\",\"traceId\":\"abc-123\"}");

		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\"}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Key must be String. Found: 123")
	public void testPartLevelMapAliasRejectsNonStringRestKey() {
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\"}\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());

		body.builder().map("username", "emmy", 123, "bad");
	}

	@Test
	public void testPartLevelEndParamsResolveBeforeBuildEnv() throws Exception {
		Collection col = Collection.load(
				JsonParser.parseString("{\"item\":[{\"name\":\"Partial Resolve\",\"request\":{\"method\":\"POST\","
						+ "\"url\":{\"raw\":\"{{base_url}}/search?text={{text}}\",\"query\":[{\"key\":\"text\",\"value\":\"{{text}}\"}]},"
						+ "\"header\":[{\"key\":\"X-Trace\",\"value\":\"{{trace_id}}\"}],"
						+ "\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\",\"value\":\"{{token}}\"}]},"
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\",\\\"password\\\":\\\"{{password}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}").getAsJsonObject());

		Environment env = new Environment("Env").builder().add("base_url", "https://api.example.com")
				.add("text", "env-text").add("trace_id", "env-trace").add("token", "env-token")
				.add("username", "env-user").add("password", "env-pass").end(null);

		Request template = col.getRequest("Partial Resolve");
		Request req = template.builder().url().set("text", "{{TEXT}}").end(Map.of("TEXT", "local-text")).auth()
				.set("token", "{{token}}").end(Map.of("token", "local-token")).headers().set("X-Trace", "{{trace_id}}")
				.end(Map.of("trace_id", "local-trace")).body().set("username", "{{username}}")
				.end(Map.of("username", "local-user")).build(env);

		req.print();

		assertEquals(req.toUrl(), "https://api.example.com/search?text=local-text");
		assertEquals(req.getHeader().get("X-Trace"), "local-trace");
		assertEquals(req.getAuth().get("token"), "local-token");
		assertEquals(req.getBody().getRaw(), "{\"username\":\"local-user\",\"password\":\"env-pass\"}");
	}

	@Test
	public void testParamsEndWithParamsAndNullResolve() {
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\",\\\"password\\\":\\\"{{password}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());

		Body locallyResolved = body.builder().set("username", "{{username}}").end(Map.of("username", "local-user"));

		assertEquals(locallyResolved.getRaw(), "{\"username\":\"local-user\",\"password\":\"{{password}}\"}");

		Body unchanged = body.builder().resolve(null).end();
		assertEquals(unchanged.getRaw(), "{\"username\":\"{{username}}\",\"password\":\"{{password}}\"}");
	}

	@Test
	public void testPartLevelMapAliasSupportsPrimitiveBodyValues() throws Exception {
		Collection col = Collection
				.load(JsonParser.parseString("{\"item\":[{\"name\":\"Primitive Body\",\"request\":{\"method\":\"POST\","
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\",\\\"single\\\":\\\"{{single}}\\\","
						+ "\\\"age\\\": \\\"{{AGE_1}}\\\",\\\"note\\\":\\\"age={{AGE_2}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}").getAsJsonObject());

		Request req = col.getRequest("Primitive Body").builder().body().set("username", "{{username}}")
				.set("age", "{{age}}").set("single", "{{single}}")
				.map("username", "emmy", "single", true, "age", 25, "AGE_2", 10).build();

		assertEquals(req.getBody().getRaw(),
				"{\"username\":\"emmy\",\"single\":\"true\",\"age\":\"25\",\"note\":\"age=10\"}");

		assertEquals(req.builder().build(null).log(),
				"[POST  ] Primitive Body                           -> \n" + "Body: [raw/json] {\n"
						+ "  \"username\": \"emmy\",\n" + "  \"single\": \"true\",\n" + "  \"age\": \"25\",\n"
						+ "  \"note\": \"age=10\"\n" + "}");
	}

	@Test
	public void testPartLevelJsonAliasSupportsRawJsonTemplateValues() throws Exception {
		Collection col = Collection.load(
				JsonParser.parseString("{\"item\":[{\"name\":\"Raw Json Template\",\"request\":{\"method\":\"POST\","
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":{{username}},\\\"age\\\":{{age}},\\\"single\\\":{{single}}}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}").getAsJsonObject());

		Request req = col.getRequest("Raw Json Template").builder().body()
				.json("username", "emmy", "age", 25, "single", true).build();

		assertEquals(req.getBody().getRaw(), "{\"username\":\"emmy\",\"age\":25,\"single\":true}");
		assertNotNull(req.getBody().getParsed());
		assertEquals(req.getBody().getParsed().isJsonObject(), true);
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Values must be provided as key/value pairs.")
	public void testPartLevelMapAliasRejectsOddKeyValuePairs() {
		Body body = Body.from(JsonParser
				.parseString("{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		body.builder().map("username", "emmy", "age");
	}

	@Test
	public void testBodyTryParseJson() {
		Body body = Body.from(JsonParser
				.parseString(
						"{\"body\":{\"mode\":\"raw\",\"raw\":\"   \",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());

		Body resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
		assertEquals(resolved.getRaw(), "   ");
		assertEquals(resolved.getParsed(), null);

		body = new Body("raw", null, "json");
		resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
		assertEquals(resolved.getRaw(), null);
		assertEquals(resolved.getParsed(), null);

		body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"id\\\":\\\"{{USER_ID}}\\\"}\",\"options\":{\"raw\":{\"language\":\"text\"}}}}")
				.getAsJsonObject());
		resolved = body.builder().resolve(Map.of("USER_ID", "42")).end();
		assertEquals(resolved.getRaw(), "{\"id\":\"42\"}");
		assertNotNull(resolved.getParsed());
		assertEquals(resolved.getParsed().isJsonObject(), true);
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Body key not found: 'NEW_KEY'")
	public void testBodySetThrowWhenKeyNotFound() {
		Request template = col.getRequest(LOGIN_GET_TOKEN);

		// body builder: add creates new key on cloned JSON object body
		Body body = template.getBody().builder().add("NEW_KEY", TEST_USERNAME).end();
		assertEquals(body.toString(), "[raw/json] {\n" + "  \"username\": \"{{username}}\",\n"
				+ "  \"password\": \"{{password}}\",\n" + "  \"NEW_KEY\": \"TEST_USER\"\n" + "}");

		// body builder: set missing key on original body → throws
		template.getBody().builder().set("NEW_KEY", TEST_USERNAME).end();
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Body builder add/set requires a JSON object body: \\[1,2,3\\]")
	public void testBodyAddRequiresObjectBody() {
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"[1,2,3]\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		body.builder().add("x", 1).end();
	}

	@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Body builder add/set requires a JSON object body: <id>42</id>")
	public void testBodyAddRequiresObjectBodyWhenRawTemplateIsNotJson() {
		Body body = Body.from(JsonParser.parseString(
				"{\"body\":{\"mode\":\"raw\",\"raw\":\"<id>{{USER_ID}}</id>\",\"options\":{\"raw\":{\"language\":\"json\"}}}}")
				.getAsJsonObject());
		body.builder().add("age", 21).resolve(Map.of("USER_ID", "42")).end();
	}

	// -------------------------------------------------------------------------
	// Test Request
	// -------------------------------------------------------------------------

	@Test
	public void testRequest() throws Exception {
		Request request = folder.getRequest(ALL_PRODUCTS);

		// request: real collection request should preserve name
		assertEquals(request.getName(), ALL_PRODUCTS);
	}

	@Test
	public void testRequestFromString() throws Exception {
		Collection col;
		Request req;

		// request: url string → parsed directly
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"url\": \"http://github.com\"}" + "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.toUrl(), "http://github.com");
		assertEquals(req.getMethod(), "GET");
		assertEquals(req.getFolderName(), PRODUCT_FOLDER);
		assertEquals(req.getUrl().isEmpty(), false);
		req.print();

		// request: description string → parsed directly
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": \"TODO\"}" + "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "TODO");
		req.print();

		// request: description null → empty string
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": null}" + "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "");

		// request: description object without content → empty string
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"description\": {}}" + "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "");

		// request: description object with content → content string
		col = Collection
				.load(JsonParser
						.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
								+ "{\"request\":{\"description\": {\"content\": \"TODO\"}}" + "}]}]}")
						.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getDescription(), "TODO");

		// request: url null → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[" + "{\"request\":{\"url\": null}" + "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request: url object without raw → empty string
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[" + "{\"request\":{\"url\": {}}" + "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request auth: v2.1 basic array value absent → stored as ""
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
				+ "{\"request\":{\"auth\":{\"type\":\"basic\",\"basic\":[{\"key\":\"" + TEST_USERNAME + "\"}]}}"
				+ "}]}]}").getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getAuth().toString(), "[basic] {" + TEST_USERNAME + "=}");
		req.print();

		// request header: value absent → stored as ""
		col = Collection.load(JsonParser
				.parseString("{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":["
						+ "{\"request\":{\"header\":[{\"key\":\"" + TEST_USERNAME + "\"}]}" + "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getHeader().toString(), "  " + TEST_USERNAME + "                           = \n");
		req.print();

		// request body: empty body object → none body
		col = Collection.load(JsonParser.parseString(
				"{\"item\":[{\"name\":\"" + PRODUCT_FOLDER + "\",\"item\":[" + "{\"request\":{\"body\":{}}" + "}]}]}")
				.getAsJsonObject());
		req = col.getFolder(PRODUCT_FOLDER).getRequest("Unnamed");
		assertEquals(req.getBody().toString(), "[none]");
		req.print();

		// request url: url field is array → unsupported URL shape returns ""
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"url\":[\"http://github.com\"]}}]}")
				.getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request url object: raw is null → empty URL
		col = Collection
				.load(JsonParser.parseString("{\"item\":[{\"request\":{\"url\":{\"raw\":null}}}]}").getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request url object: raw is object → empty URL
		col = Collection.load(JsonParser
				.parseString("{\"item\":[{\"request\":{\"url\":{\"raw\":{\"value\":\"http://github.com\"}}}}]}")
				.getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.toUrl(), "");

		// request: description array → empty string
		col = Collection.load(
				JsonParser.parseString("{\"item\":[{\"request\":{\"description\":[\"TODO\"]}}]}").getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.getDescription(), "");

		// request: description object with content null → empty string
		col = Collection.load(JsonParser.parseString("{\"item\":[{\"request\":{\"description\":{\"content\":null}}}]}")
				.getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.getDescription(), "");

		// request: description object with content object → empty string
		col = Collection.load(
				JsonParser.parseString("{\"item\":[{\"request\":{\"description\":{\"content\":{\"text\":\"TODO\"}}}}]}")
						.getAsJsonObject());
		req = col.getRequest("Unnamed");
		assertEquals(req.getDescription(), "");
	}

	@Test
	public void testRequestParamsReturnsHandlebarsTokensWithEmptyValues() throws Exception {
		Collection col = Collection
				.load(JsonParser.parseString("{\"item\":[{\"name\":\"Tokens\",\"request\":{\"method\":\"POST\","
						+ "\"url\":{\"raw\":\"{{base_url}}/users/{{user_id}}?q={{query}}\","
						+ "\"query\":[{\"key\":\"q\",\"value\":\"{{query}}\"}]},"
						+ "\"header\":[{\"key\":\"X-Trace\",\"value\":\"{{trace_id}}\"}],"
						+ "\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\",\"value\":\"{{token}}\"}]},"
						+ "\"body\":{\"mode\":\"raw\",\"raw\":\"{\\\"username\\\":\\\"{{username}}\\\"}\","
						+ "\"options\":{\"raw\":{\"language\":\"json\"}}}}}]}").getAsJsonObject());

		Map<String, String> params = col.getRequest("Tokens").params();

		assertEquals(params,
				Map.of("base_url", "", "user_id", "", "query", "", "trace_id", "", "token", "", "username", ""));
	}

	@Test
	public void testRequestResolveFillsMatchingEnvironmentValuesOnly() throws Exception {
		Collection col = Collection
				.load(JsonParser
						.parseString("{\"item\":[{\"name\":\"Resolve Tokens\",\"request\":{"
								+ "\"url\":{\"raw\":\"{{base_url}}/users/{{missing_id}}\"},"
								+ "\"header\":[{\"key\":\"X-Trace\",\"value\":\"{{trace_id}}\"}]}}]}")
						.getAsJsonObject());
		Environment env = new Environment("Env").builder().add("base_url", "https://api.example.com")
				.add("trace_id", "abc-123").end(null);

		Request req = col.getRequest("Resolve Tokens");

		Map<String, String> resolved = req.resolve(env);
		assertEquals(resolved.get("base_url"), "https://api.example.com");
		assertEquals(resolved.get("trace_id"), "abc-123");
		assertEquals(resolved.get("missing_id"), "");

		assertEquals(req.resolve(null).toString(), "{base_url=, missing_id=, trace_id=}");
	}

	@Test
	public void testDefaultExecuteWithJavaHttpClientExecutor() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);

		server.createContext("/users", exchange -> {
			String responseBody = "{\"users\":[{\"id\":1,\"firstName\":\"Benjamin\"}]}";

			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);

			try (OutputStream os = exchange.getResponseBody()) {
				os.write(responseBody.getBytes(StandardCharsets.UTF_8));
			}
		});

		server.start();

		try {
			int port = server.getAddress().getPort();

			String collectionJson = "{\n" + "  \"info\": {\n" + "    \"name\": \"Coverage Collection\",\n"
					+ "    \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n"
					+ "  },\n" + "  \"item\": [\n" + "    {\n" + "      \"name\": \"Get Users\",\n"
					+ "      \"request\": {\n" + "        \"method\": \"GET\",\n" + "        \"header\": [],\n"
					+ "        \"url\": {\n" + "          \"raw\": \"http://localhost:" + port + "/users\",\n"
					+ "          \"protocol\": \"http\",\n" + "          \"host\": [\"localhost\"],\n"
					+ "          \"port\": \"" + port + "\",\n" + "          \"path\": [\"users\"]\n" + "        }\n"
					+ "      }\n" + "    }\n" + "  ]\n" + "}";

			String environmentJson = "{\n" + "  \"name\": \"Coverage Environment\",\n" + "  \"values\": []\n" + "}";

			Path collectionFile = Files.createTempFile("coverage-collection", ".json");
			Path environmentFile = Files.createTempFile("coverage-environment", ".json");

			Files.write(collectionFile, collectionJson.getBytes(StandardCharsets.UTF_8));
			Files.write(environmentFile, environmentJson.getBytes(StandardCharsets.UTF_8));
		} finally {
			server.stop(0);
		}
	}

	// -------------------------------------------------------------------------
	// Test Authentication
	// -------------------------------------------------------------------------

	@Test
	public void testAuthenticationBranches() {
		ApiExecutor executor = () -> new ApiResponse(200, "{}", new byte[0], Map.of());
		Map<String, String> runtimeHeaders = new java.util.LinkedHashMap<>();
		Authentication.State state = new Authentication.State();
		Authentication auth = new Authentication(executor, runtimeHeaders, state);

		// oauth2 delegates to bearer and sets Authorization.
		ApiExecutor returned = auth.oauth2("token-123");
		assertEquals(returned, executor);
		assertEquals(runtimeHeaders.get("Authorization"), "Bearer token-123");
		assertEquals(state.isNoAuth(), false);
		assertEquals(state.shouldApplyRequestHeader("Authorization"), true);

		// bearer replaces the Authorization header and keeps auth enabled.
		returned = auth.bearer("token-456");
		assertEquals(returned, executor);
		assertEquals(runtimeHeaders.get("Authorization"), "Bearer token-456");
		assertEquals(state.isNoAuth(), false);

		// bearer ignores null and blank tokens but still returns the executor.
		runtimeHeaders.clear();
		assertEquals(auth.bearer(null), executor);
		assertEquals(runtimeHeaders.containsKey("Authorization"), false);

		assertEquals(auth.bearer("   "), executor);
		assertEquals(runtimeHeaders.containsKey("Authorization"), false);

		// basic stores the Base64-encoded username:password value.
		returned = auth.basic("user", "pass");
		String expectedBasic = java.util.Base64.getEncoder()
				.encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
		assertEquals(returned, executor);
		assertEquals(runtimeHeaders.get("Authorization"), "Basic " + expectedBasic);
		assertEquals(state.isNoAuth(), false);

		// basic uses String.valueOf, so null values are represented as "null".
		auth.basic(null, null);
		String expectedNullBasic = java.util.Base64.getEncoder()
				.encodeToString("null:null".getBytes(StandardCharsets.UTF_8));
		assertEquals(runtimeHeaders.get("Authorization"), "Basic " + expectedNullBasic);

		// preemptive is a no-op marker that returns the same Authentication object.
		assertEquals(auth.preemptive(), auth);

		// apiKey stores valid header name/value pairs.
		runtimeHeaders.clear();
		returned = auth.apiKey("X-API-Key", "api-key-123");
		assertEquals(returned, executor);
		assertEquals(runtimeHeaders.get("X-API-Key"), "api-key-123");
		assertEquals(state.isNoAuth(), false);

		// apiKey ignores null/blank names and null values.
		runtimeHeaders.clear();
		assertEquals(auth.apiKey(null, "api-key-123"), executor);
		assertEquals(runtimeHeaders.isEmpty(), true);

		assertEquals(auth.apiKey("   ", "api-key-123"), executor);
		assertEquals(runtimeHeaders.isEmpty(), true);

		assertEquals(auth.apiKey("X-API-Key", null), executor);
		assertEquals(runtimeHeaders.isEmpty(), true);

		// none removes runtime Authorization and tells executors to skip request
		// Authorization.
		runtimeHeaders.put("Authorization", "Bearer old-token");
		returned = auth.none();
		assertEquals(returned, executor);
		assertEquals(runtimeHeaders.containsKey("Authorization"), false);
		assertEquals(state.isNoAuth(), true);
		assertEquals(state.shouldApplyRequestHeader("Authorization"), false);
		assertEquals(state.shouldApplyRequestHeader("authorization"), false);
		assertEquals(state.shouldApplyRequestHeader("Content-Type"), true);
		assertEquals(state.shouldApplyRequestHeader(null), true);

		// clear resets only the auth state. Executors clear runtime headers separately.
		state.clear();
		assertEquals(state.isNoAuth(), false);
		assertEquals(state.shouldApplyRequestHeader("Authorization"), true);
	}

	@Test
	public void testAuthenticationConstructorValidation() {
		ApiExecutor executor = () -> new ApiResponse(200, "{}", new byte[0], Map.of());
		Map<String, String> runtimeHeaders = new java.util.LinkedHashMap<>();
		Authentication.State state = new Authentication.State();

		assertThrows(IllegalArgumentException.class, () -> new Authentication(null, runtimeHeaders, state));

		assertThrows(IllegalArgumentException.class, () -> new Authentication(executor, null, state));

		assertThrows(IllegalArgumentException.class, () -> new Authentication(executor, runtimeHeaders, null));
	}

	// -------------------------------------------------------------------------
	// Test ApiResponse
	// -------------------------------------------------------------------------

	@Test
	public void testApiResponseBranches() {
		Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));

		byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

		ApiResponse response = new ApiResponse(201, "{\"ok\":true}", bytes, headers);

		assertEquals(response.statusCode(), 201);
		assertEquals(response.getBody(), "{\"ok\":true}");
		assertEquals(response.parse().getAsJsonObject().get("ok").getAsBoolean(), true);
		assertEquals(response.parse("{\"name\":\"JPostman\"}").getAsJsonObject().get("name").getAsString(), "JPostman");
		assertEquals(new String(response.asByteArray(), StandardCharsets.UTF_8), "abc");
		assertEquals(response.getHeaders().get("Content-Type"), List.of("application/json"));
		assertEquals(response.toString(), "ApiResponse{statusCode=201, bodyLength=11}");
		assertEquals(response.pretty(), "{\n  \"ok\": true\n}");
		assertEquals(response.log(), "Status Code: 201\nBody: {\n  \"ok\": true\n}\n");
		assertEquals(response.log(true),
				"Status Code: 201\n  Content-Type                        = [application/json]\n"
						+ "Body: {\n  \"ok\": true\n}\n");
		response.print();

		// Constructor should clone byte array.
		bytes[0] = 'z';
		assertEquals(new String(response.asByteArray(), StandardCharsets.UTF_8), "abc");

		// asByteArray() should also return a clone.
		byte[] returnedBytes = response.asByteArray();
		returnedBytes[0] = 'x';
		assertEquals(new String(response.asByteArray(), StandardCharsets.UTF_8), "abc");

		// Headers should be unmodifiable.
		assertThrows(UnsupportedOperationException.class, () -> response.getHeaders().put("X-Test", List.of("value")));

		// Null body, null bytes, and null headers branches.
		ApiResponse emptyResponse = new ApiResponse(204, null, null, null);

		assertEquals(emptyResponse.statusCode(), 204);
		assertEquals(emptyResponse.getBody(), "");
		assertEquals(emptyResponse.asByteArray().length, 0);
		assertEquals(emptyResponse.getHeaders().size(), 0);
		assertEquals(emptyResponse.toString(), "ApiResponse{statusCode=204, bodyLength=0}");
		assertEquals(emptyResponse.pretty(), "");

		// Invalid JSON parse branch should return null.
		assertEquals(emptyResponse.parse("not-json").getAsString(), "not-json");

		String raw = col.getRoot().toString();
		ApiResponse colResponse = new ApiResponse(200, raw, null, null);
		assertEquals(raw, colResponse.parse(raw).toString());
		assertEquals(colResponse.exists("info.unknown"), false);
		assertEquals(colResponse.exists("info.name"), true);
		assertEquals(colResponse.path("info.name"), "DummyJSON");
		assertEquals(colResponse.path("item[0].item[0].name"), ALL_PRODUCTS);
	}

	// -------------------------------------------------------------------------
	// Test JPostman
	// -------------------------------------------------------------------------

	@Test
	public void testJPostmanLoadAndSessionBranches() throws Exception {
		JPostman.Context context = JPostman.load(new ByteArrayInputStream(COLLECTION_BYTES),
				new ByteArrayInputStream(ENVIRONMENT_BYTES));

		assertNotNull(context.getCollection());
		assertNotNull(context.getEnvironment());

		Request request = context.request(GET_AUTH_USER);
		assertNotNull(request);
		assertEquals(request.getName(), GET_AUTH_USER);
		assertEquals(request.toUrl(), "https://dummyjson.com/auth/me");

		assertEquals(context.folder(PRODUCT_FOLDER).getName(), PRODUCT_FOLDER);

		JPostman.Context sessionWithoutEnv = JPostman.load(new ByteArrayInputStream(COLLECTION_BYTES));

		assertNotNull(sessionWithoutEnv.getCollection());
		assertEquals(sessionWithoutEnv.getEnvironment(), null);

		Request unresolvedRequest = sessionWithoutEnv.request(GET_AUTH_USER);
		assertNotNull(unresolvedRequest);
		assertEquals(unresolvedRequest.getName(), GET_AUTH_USER);

		JPostman.Context sessionWithBlankEnv = JPostman.load(new ByteArrayInputStream(COLLECTION_BYTES));

		assertNotNull(sessionWithBlankEnv.getCollection());
		assertEquals(sessionWithBlankEnv.getEnvironment(), null);

		assertThrows(NullPointerException.class, () -> JPostman.load(null));
		assertThrows(NullPointerException.class, () -> context.request(null));

		assertThrows(IllegalArgumentException.class, () -> context.request("UNKNOWN_REQUEST"));
	}
}