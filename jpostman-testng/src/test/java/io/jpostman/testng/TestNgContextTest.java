package io.jpostman.testng;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import com.google.gson.JsonParser;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.secure.RedactionPolicy;
import io.jpostman.secure.SecureContext;
import io.jpostman.secure.SecureRequest;
import io.jpostman.secure.SecureResponse;
import io.jpostman.secure.SecureValues;

public class TestNgContextTest {

	@Test
	public void hardAssertionsCanCallAllAssertionMethods() {
		ApiResponse response = response(200, "{\"accessToken\":\"abc123\",\"active\":true,\"user\":{\"id\":1}}");

		TestNgContext cxt = TestNgContext.create().secret("accessToken", "abc123").response(response);

		cxt.asserts(true).statusCode(200).statusCode(200, "Status code").exists("accessToken")
				.exists("accessToken", "Token exists").notExists("missing").notExists("missing", "Missing path")
				.pathEquals("active", true).pathEquals("user.id", 1, "User id").pathNotNull("accessToken")
				.pathNotNull("accessToken", "Token not null").isEqual(cxt.asString("accessToken"), "abc123")
				.isEqual(cxt.asString("accessToken"), "abc123", "Token value")
				.isNotEqual(cxt.asString("accessToken"), "bad")
				.isNotEqual(cxt.asString("accessToken"), "bad", "Token should differ").isTrue(true)
				.isTrue(true, "True condition").isFalse(false).isFalse(false, "False condition").isNull(null)
				.isNull(null, "Null value").isNotNull(cxt.response()).isNotNull(cxt.response(), "Response not null");

		cxt.verify();
		cxt.soft().verify();
		cxt.soft().assertAll();
		AssertionError error = expectThrows(AssertionError.class, () -> cxt.asserts(true).exists("refreshToken"));
		assertEquals(error.getMessage(), "Path not found: refreshToken\n");

		TestNgContext.setCurrent(cxt);
		assertEquals(TestNgContext.current(), cxt);
		assertEquals(TestNgContext.current(), cxt.ctx());
		TestNgContext.clearCurrent();
		error = expectThrows(AssertionError.class, () -> TestNgContext.current());
		assertEquals(error.getMessage(), "No current TestNgContext is available.");
	}

	@Test
	public void softAssertionsCanCallAllAssertionMethodsAndAppendSecureLogOnce() {
		ApiResponse response = response(400, "{\"accessToken\":\"abc123\",\"active\":true,\"user\":{\"id\":1}}");

		TestNgContext cxt = TestNgContext.create().secret("accessToken", "abc123").response(response);

		cxt.soft().statusCode(400).exists("active").notExists("missing").pathEquals("active", true)
				.pathEquals("user.id", 1, "User id").pathNotNull("accessToken")
				.isEqual(cxt.asString("accessToken"), "abc123").isNotEqual(cxt.asString("accessToken"), "bad")
				.isTrue(true).isFalse(false).isNull(null).isNotNull(cxt.response());

		cxt.context(c -> cxt.verify());
		cxt.soft().context().verify(400);

		cxt.soft(true).statusCode(400).exists("accessToken", "Access token not found").assertAll();

		try {
			cxt.soft(true).statusCode(400).exists("inactive").pathEquals("inactive", true)
					.isEqual(cxt.asString("accessToken"), "abc1235").verify();

			fail("Expected soft assertions to fail");
		} catch (AssertionError error) {
			assertEquals(error.getSuppressed().length, 1);

			String message = error.getMessage();
			String suppressed = error.getSuppressed()[0].getMessage();

			assertTrue(message.contains("Path not found: inactive"));
			assertTrue(message.contains("expected [abc1235] but found [abc123]"));
			assertFalse(message.contains("**********SecureResponse:"));
			assertTrue(suppressed.contains("**********SecureResponse:"));
			assertFalse(message.contains("\"accessToken\": \"********\""));
			assertTrue(suppressed.contains("\"accessToken\": \"********\""));
		}
	}

	@Test
	public void contextCanCallSecureDelegateMethods() throws Exception {
		String rules = "accessToken\n";
		String policy = "[default]\nredact=refreshToken\nheaders=Authorization\n\n[user]\nextends=default\nfilter=id,name";

		Environment env = new Environment("test").builder().add("envToken", "env123").end();

		Request request = request();
		ApiResponse response = response(201,
				"{\"id\":1,\"name\":\"Sam\",\"accessToken\":\"abc123\",\"refreshToken\":\"ref123\",\"phone\":\"+15551234567\"}");

		ApiExecutor executor = () -> response;

		TestNgContext cxt = TestNgContext.create()
				.load(new ByteArrayInputStream(rules.getBytes(StandardCharsets.UTF_8)))
				.loadPolicy(new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)))
				.redactionPolicy(RedactionPolicy.defaults()).redact("accessToken").redactRegex("(?i).*phone.*")
				.redactRegex("(?i).*refresh.*", "[:3]").unredact("unused").headers("Authorization").unheaders("unused")
				.headersFilter("Content-Type").filter("id", "name").plain("plainKey", "plainValue")
				.plain(Map.of("plainMap", 123)).plain(env).secret("secretKey", "secretValue")
				.secret(Map.of("mapSecret", "mapValue")).secret(env).unsecret("plainKey").request(request)
				.response(response);

		SecureValues values = cxt.values();
		SecureRequest secureRequest = cxt.request();
		SecureResponse secureResponse = cxt.response();

		assertNotNull(values);
		assertNotNull(secureRequest);
		assertNotNull(secureResponse);
		assertNotNull(cxt.secure());
		assertNotNull(cxt.redactionPolicy());

		assertEquals(cxt.get("secretKey"), "secretValue");
		assertEquals(cxt.asString("secretKey"), "secretValue");

		assertNotNull(cxt.from(request));
		assertNotNull(cxt.from(response));
		assertNotNull(cxt.from(executor));

		cxt.response(executor);
		assertEquals(cxt.statusCode(), 201);
		assertEquals(cxt.exists("id"), true);
		assertEquals((Integer) cxt.path("id"), 1);

		assertEquals(cxt.paths("id").toString(), "[1]");

		assertNotNull(cxt.log());
		assertNotNull(cxt.log(false));

		cxt.print();
		cxt.print(false);

		TestNgContext copy = cxt.loadRules("user").copy().response(response);
		copy.verify(201);
		copy.asserts().exists("id").statusCode(201);
	}

	@Test
	public void canWrapExistingSecureContextAndUseDefaultSoftStatusCode() {
		ApiResponse response = response(200, "{\"token\":\"abc123\"}");

		SecureContext secure = SecureContext.create().secret("token", "abc123").response(response);

		TestNgContext cxt = TestNgContext.from(secure);

		assertTrue(cxt.hasKey("token"));
		assertEquals(cxt.asString("token"), "abc123");

		cxt.asserts().exists("token").pathEquals("token", "abc123");

		cxt.soft().verify();
		cxt.asserts().verify();
		cxt.verify();
	}

	@Test
	public void filterListCanKeepFullFirstItemAndSelectedFieldsForOtherItems() throws Exception {
		String policy = "[default]\nfilterList=/**/reviews[0],/**/reviews/*/rating,/**/reviews/*/reviewerName\n"
				+ "redact=regex:(?i).*email.*\n";

		ApiResponse response = response(200, "{\"products\":[{\"id\":1,\"title\":\"Mascara\",\"reviews\":["
				+ "{\"rating\":3,\"comment\":\"Would not recommend!\",\"reviewerName\":\"Eleanor\",\"reviewerEmail\":\"e@example.com\"},"
				+ "{\"rating\":4,\"comment\":\"Very satisfied!\",\"reviewerName\":\"Lucas\",\"reviewerEmail\":\"l@example.com\"}"
				+ "]}]}");

		TestNgContext cxt = TestNgContext.create()
				.loadPolicy(new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)));

		String filtered = cxt.from(response).filtered();

		assertTrue(filtered.contains("\"title\": \"Mascara\""), filtered);
		assertTrue(filtered.contains("\"comment\": \"Would not recommend!\""), filtered);
		assertTrue(filtered.contains("\"reviewerEmail\": \"********\""), filtered);
		assertTrue(filtered.contains("\"rating\": 4"), filtered);
		assertTrue(filtered.contains("\"reviewerName\": \"Lucas\""), filtered);
		assertFalse(filtered.contains("Very satisfied!"), filtered);
		assertFalse(filtered.contains("l@example.com"), filtered);

		cxt = TestNgContext.create().redact("regex:(?i).*email.*").filterList("/**/reviews[0]", "/**/reviews/*/rating",
				"/**/reviews/*/reviewerName");

		filtered = cxt.from(response).filtered();

		assertTrue(filtered.contains("\"title\": \"Mascara\""), filtered);
		assertTrue(filtered.contains("\"comment\": \"Would not recommend!\""), filtered);
		assertTrue(filtered.contains("\"reviewerEmail\": \"********\""), filtered);
		assertTrue(filtered.contains("\"rating\": 4"), filtered);
		assertTrue(filtered.contains("\"reviewerName\": \"Lucas\""), filtered);
		assertFalse(filtered.contains("Very satisfied!"), filtered);
		assertFalse(filtered.contains("l@example.com"), filtered);
	}

	@Test
	public void cacheCanUseExplicitKeyAndReuseValue() {
		TestNgContext secure = TestNgContext.create();
		AtomicInteger calls = new AtomicInteger();

		String first = secure.cache(() -> {
			calls.incrementAndGet();
			return "abc123";
		}, "token");

		String second = secure.cache(() -> {
			calls.incrementAndGet();
			return "new-value";
		}, "token");

		String third = secure.cache(() -> {
			calls.incrementAndGet();
			return "new-value";
		});

		assertEquals(first, "abc123");
		assertEquals(second, "abc123");
		assertEquals(third, "new-value");
		assertEquals(calls.get(), 2);

		assertEquals(secure.cache("token"), "abc123");
		assertEquals(secure.cache().get("token"), "abc123");
		assertEquals(secure.cache().get("cacheCanUseExplicitKeyAndReuseValue"), "new-value");

		secure.cacheClean("cacheCanUseExplicitKeyAndReuseValue");

		assertEquals(secure.cache().get("token"), "abc123");
		assertEquals(secure.cache().get("cacheCanUseExplicitKeyAndReuseValue"), null);
		assertEquals(secure.cache().size(), 1);

		secure.cacheClean();

		assertEquals(secure.cache().get("token"), null);
		assertEquals(secure.cache().size(), 0);

		secure.cache("new-key", "new-value");
		assertEquals(secure.cache("new-key"), "new-value");
	}

	@Test
	public void responseCanUseCurrentContextFunction() {
		ApiResponse response = response(200, "{\"accessToken\":\"abc123\"}");
		TestNgContext cxt = TestNgContext.create().request(request()).response(ctx -> response);
		cxt.asserts().exists("accessToken", "Access token not found");
		assertEquals(cxt.response().statusCode(), 200);
	}

	@JPostmanTestNG
	private static final class JPostmanTestNgAnnotatedFixture {
		@SuppressWarnings("unused")
		public void plain() {
		}
	}

	private static ApiResponse response(int statusCode, String body) {
		return new ApiResponse(statusCode, body, new byte[0], Map.of());
	}

	private static Request request() {
		String json = "{\"method\":\"GET\",\"url\":\"https://example.com/users?token={{token}}\","
				+ "\"header\":[{\"key\":\"Authorization\",\"value\":\"Bearer {{token}}\"}]}";

		return Request.from("Demo request", "(root)", JsonParser.parseString(json).getAsJsonObject());
	}
}