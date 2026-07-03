package io.jpostman.secure;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import com.google.gson.JsonParser;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Environment;
import io.jpostman.Params;
import io.jpostman.Request;

public class SecureTest {

	@Test
	public void secureValueMasksToStringButCanReveal() {
		SecureValue value = SecureValue.secret("real-token");
		assertEquals(value.mask(), SecureValue.DEFAULT_MASK);
		assertEquals(value.toString(), SecureValue.DEFAULT_MASK);
		assertEquals(value.reveal(), "real-token");
		assertTrue(value.isProtected());
	}

	@Test
	public void secureValueCustomMasksToStringButCanReveal() {
		String customMask = "#####";
		SecureValue value = SecureValue.secret("real-token", customMask);
		assertEquals(value.mask(), customMask);
		assertEquals(value.toString(), customMask);
		assertEquals(value.reveal(), "real-token");
		assertTrue(value.isProtected());
	}

	@Test
	public void secureValuesCanRevealForBuildAndDisplayForLogs() {
		SecureValues values = SecureValues.builder().secret("accessToken", "abc123")
				.plain("baseUrl", "https://example.com").build();
		assertEquals(values.asMap().get("accessToken"), SecureValue.DEFAULT_MASK);
		assertEquals(values.asMap().get("baseUrl"), "https://example.com");
		assertEquals(values.toString(), "accessToken=********\nbaseUrl=https://example.com");
	}

	@Test
	public void redactionPolicyCanUseCustomSliceExpressionFactory() {
		ApiResponse response = response(200, "{\"creditCard\":\"1234-4567-7890-0987\"}");
		RedactionPolicy policy = RedactionPolicy.builder().sliceExpressionFactory(new DefaultSliceExpressionFactory() {
			@Override
			public String mask(SliceExpressionFactory parsed, String source, String mask) {
				return parsed.mask(source, "#####");
			}
		}).protectRule("creditCard[-4:]").build();
		String log = SecureResponse.from(response).redactionPolicy(policy).log();
		assertTrue(log.contains("\"creditCard\": \"#####0987\""));
	}

	@Test
	public void redactionPolicyProtectsCustomKeys() {
		RedactionPolicy policy = RedactionPolicy.builder().sliceExpressionFactory(new DefaultSliceExpressionFactory())
				.protectKey("API-KEY").protectKey("accessToken").mask("*!!!!*").build();
		String text = "API-KEY = secret-key\naccessToken: token-value\nusername = testuser";
		String redacted = SecureText.redact(text, SecureValues.empty(), policy);
		assertTrue(redacted.contains("API-KEY = *!!!!*"), redacted);
		assertTrue(redacted.contains("accessToken: *!!!!*"), redacted);
		assertTrue(redacted.contains("username = testuser"), redacted);
	}

	@Test
	public void secureRequestKeepsUnresolvedPlaceholdersButMasksConcreteProtectedHeaders() {
		SecureRequest unresolved = SecureRequest.from(loginRequest())
				.secret(Params.asMap("accessToken", "real-token", "API-KEY", "real-api-key", "SSN", "999-99-9999"));

		String unresolvedLog = unresolved.log(false);
		assertTrue(unresolvedLog.contains("Authorization                       = Bearer {{accessToken}}"),
				unresolvedLog);
		assertTrue(unresolvedLog.contains("API-KEY                             = {{API-KEY}}"), unresolvedLog);
		assertTrue(unresolvedLog.contains("\"ssn\": \"{{SSN}}\""), unresolvedLog);

		String json = "{\"method\":\"GET\",\"url\":{\"raw\":\"/secure\",\"path\":[\"secure\"]},\"header\":["
				+ "{\"key\":\"Authorization\",\"value\":\"Bearer real-token\"},"
				+ "{\"key\":\"X-Trace\",\"value\":\"trace-123\"}]}";
		Request concreteRequest = Request.from("Concrete", "secure", JsonParser.parseString(json).getAsJsonObject());
		String concreteLog = SecureRequest.from(concreteRequest).headers("Authorization").log(false);
		assertTrue(concreteLog.contains("Authorization                       = ********"), concreteLog);
		assertTrue(concreteLog.contains("X-Trace                             = trace-123"), concreteLog);
	}

	@Test
	public void secureResponseMasksConfiguredHeadersWithoutChangingRequestPlaceholderBehavior() {
		ApiResponse response = response(200, "{\"token\":\"abc123\",\"trace\":\"trace-secret\"}",
				Map.of("Set-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));

		SecureResponse secureResponse = SecureResponse.from(response)
				.values(SecureValues.builder().secret("trace", "trace-secret").build())
				.redactionPolicy(RedactionPolicy.defaults().headers("X-Trace-Id"));

		String log = secureResponse.log(true);
		assertTrue(log.contains("Set-Cookie                          = ********"), log);
		assertTrue(log.contains("X-Trace-Id                          = ********"), log);
		assertFalse(log.contains("jwt-value"), log);
		assertFalse(log.contains("trace-secret"), log);
	}

	@Test
	public void secureContextHeaderAddsProtectedResponseHeader() {
		ApiResponse response = response(200, "{\"status\":\"ok\"}",
				Map.of("X-Session-Id", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));
		SecureContext secure = SecureContext.create().headers("X-Session-Id");
		SecureResponse secureResponse = secure.from(response);
		String log = secureResponse.log(true);
		assertTrue(log.contains("X-Session-Id                        = ********"), log);
		assertTrue(log.contains("X-Trace-Id                          = [trace-secret]"), log);

		secureResponse = SecureResponse.from(response).headers("X-Session-Id");
		log = secureResponse.log(true);
		assertTrue(log.contains("X-Session-Id                        = ********"), log);
		assertTrue(log.contains("X-Trace-Id                          = [trace-secret]"), log);
	}

	@Test
	public void secureContextHeaderFilterShowsOnlySelectedResponseHeaders() {
		ApiResponse response = response(200, "{\"status\":\"ok\"}",
				Map.of("X-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));

		SecureContext secure = SecureContext.create().headers("X-Cookie").headersFilter("X-Cookie");

		SecureResponse secureResponse = secure.from(response);
		String log = secureResponse.log(true);
		assertTrue(log.contains("X-Cookie                            = ********"), log);
		assertFalse(log.contains("X-Trace-Id"), log);
		assertFalse(log.contains("trace-secret"), log);

		secureResponse = secure.from(response).headers("X-Cookie").headersFilter("X-Cookie");
		log = secureResponse.log(true);
		assertTrue(log.contains("X-Cookie                            = ********"), log);
		assertFalse(log.contains("X-Trace-Id"), log);
		assertFalse(log.contains("trace-secret"), log);
	}

	@Test
	public void secureRequestMasksBeforeBuildAndResolvesOnBuild() {
		Map<String, ?> plainEnv = Params.asMap("baseUrl", "https://api.example.com");
		Map<String, ?> vaultEnv = Params.asMap("accessToken", "real-token", "API-KEY", "real-api-key", "SSN",
				"999-99-9999");
		Request loginRequest = loginRequest();
		SecureRequest secure = SecureRequest.from(loginRequest).redactionPolicy(RedactionPolicy.defaults())
				.plain(plainEnv).secret(vaultEnv);

		secure.print();
		String unresolved = secure.log(false);
		String resolved = secure.log();
		assertEquals(secure.toString(), "[POST  ] Login                                    -> {{baseUrl}}/login");
		assertEquals(resolved,
				"[POST  ] Login                                    -> https://api.example.com/login\n"
						+ "Headers:\n  Authorization                       = ********\n"
						+ "  API-KEY                             = ********\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"********\"\n}");
		assertEquals(unresolved,
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"{{SSN}}\"\n}");
		// Also mask username and Content-Type.
		assertEquals(secure.redactionPolicy().isProtectedKey("username"), false);
		assertEquals(secure.redactionPolicy().isProtectedKey("password"), true);
		secure = secure.redact("username", "Content-Type[0:11]").unredact("password");
		assertEquals(secure.redactionPolicy().isProtectedKey("username"), true);
		assertEquals(secure.redactionPolicy().isProtectedKey("password"), false);

		assertEquals(secure.log(false),
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = ********application\n\nBody: [raw] {\n"
						+ "  \"username\": \"********\",\n  \"password\": \"secret\",\n  \"ssn\": \"{{SSN}}\"\n}");
		Request request = secure.build();
		assertEquals(request.getHeader().get("Authorization"), "Bearer real-token");
		assertEquals(request.getHeader().get("API-KEY"), "real-api-key");
		assertEquals(request.toUrl(), "https://api.example.com/login");

		Request modified = secure.builder().headers().add("X-Token", "{{accessToken}}").end().build();
		assertEquals(modified.getHeader().get("X-Token"), "real-token");
		assertEquals(modified.toUrl(), "https://api.example.com/login");

		assertEquals(secure.request(), loginRequest);
	}

	@Test
	public void secureContextCanApplySameValuesAndRulesToMultipleRequests() {
		SecureContext secure = SecureContext.create().redactionPolicy(RedactionPolicy.defaults())
				.unredact("Authorization") // Authorization = ********
											// Authorization = Bearer ********
				.redact("username", "Content-Type[-4:]").plain(new Environment("postman-secure"))
				.plain(Params.asMap("baseUrl", "https://api.example.com"))
				.secret(Params.asMap("accessToken", "real-token", "API-KEY", "real-api-key", "SSN", "999-99-9999"));

		SecureRequest login = secure.from(loginRequest());
		SecureRequest user = secure.from(userRequest());
		String resolved = login.log();
		String loginDebug = login.log(false);
		String userDebug = user.log(false);
		Request resolvedLogin = login.build();
		Request resolvedUser = user.build();

		assertTrue(resolved.contains("Authorization                       = ********"));

		assertEquals(loginDebug,
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = ********json\n\nBody: [raw] {\n"
						+ "  \"username\": \"********\",\n  \"password\": \"********\",\n  \"ssn\": \"{{SSN}}\"\n}");
		assertEquals(userDebug,
				"[GET   ] Get User                                 -> {{baseUrl}}/users/123\nHeaders:\n"
						+ "  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = ********json\n\nBody: [none]");
		assertEquals(resolvedLogin.getHeader().get("Authorization"), "Bearer real-token");
		assertEquals(resolvedLogin.getHeader().get("API-KEY"), "real-api-key");
		assertEquals(resolvedLogin.toUrl(), "https://api.example.com/login");
		assertEquals(resolvedUser.getHeader().get("Authorization"), "Bearer real-token");
		assertEquals(resolvedUser.getHeader().get("API-KEY"), "real-api-key");
		assertEquals(resolvedUser.toUrl(), "https://api.example.com/users/123");
		assertEquals(secure.redactionPolicy().isProtectedKey("username"), true);
		assertEquals(secure.redactionPolicy().protectedKeys().contains("username"), true);
		loginDebug = SecureContext.create().secret(new Environment("postman-secure")).from(loginRequest()).log(false);
		assertEquals(loginDebug,
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"{{SSN}}\"\n}");

		assertEquals(secure.values().get("accessToken").isProtected(), true);
		secure.unsecret("accessToken").unheaders("Authorization");
		login = secure.from(loginRequest());
		secure.print();
		String afterUnsecret = login.log();
		assertTrue(afterUnsecret.contains("Authorization                       = Bearer real-token"), afterUnsecret);
		assertEquals(secure.values().get("accessToken").isProtected(), false);
	}

	@Test
	public void secureApiResponseRedactsKnownValuesAndProtectedKeys() {
		ApiResponse response = response(200,
				"{\"accessToken\":\"abc123\",\"username\":\"testuser\",\"apiKey\":\"key-1\","
						+ "\"token\":\"ABCD\",\"ssn\":\"999-999-9999\",\"creditCard\":\"1234-4567-7890-0987\"}",
				Map.of("Authorization", Params.asList("Bearer abc123")));

		SecureResponse secureResponse = SecureContext.create().from(response)
				.redactionPolicy(RedactionPolicy.defaults()).redact("token", "ssn", "creditCard[-4:]");
		String log = secureResponse.log(true);
		assertTrue(log.contains(SecureValue.DEFAULT_MASK));
		assertTrue(log.contains("testuser"));
		assertTrue(log.contains("\"accessToken\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"apiKey\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"token\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"ssn\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"creditCard\": \"********0987\""));

		assertEquals(secureResponse.statusCode(), 200);
		assertEquals(secureResponse.getBody(),
				"{\"accessToken\":\"abc123\",\"username\":\"testuser\",\"apiKey\":\"key-1\","
						+ "\"token\":\"ABCD\",\"ssn\":\"999-999-9999\",\"creditCard\":\"1234-4567-7890-0987\"}");
		assertEquals(secureResponse.parse().toString(),
				"{\"accessToken\":\"abc123\",\"username\":\"testuser\",\"apiKey\":\"key-1\","
						+ "\"token\":\"ABCD\",\"ssn\":\"999-999-9999\",\"creditCard\":\"1234-4567-7890-0987\"}");
		assertEquals(secureResponse.parse("{\"hello\": \"world\"}").toString(), "{\"hello\":\"world\"}");
		assertEquals(secureResponse.asByteArray().length, 0);
		assertEquals(secureResponse.getHeaders().get("Authorization").get(0), "Bearer abc123");
		assertEquals(secureResponse.path("creditCard"), "1234-4567-7890-0987");
		assertEquals(secureResponse.pretty(),
				"{\n  \"accessToken\": \"********\",\n  \"username\": \"testuser\",\n"
						+ "  \"apiKey\": \"********\",\n  \"token\": \"********\",\n"
						+ "  \"ssn\": \"********\",\n  \"creditCard\": \"********0987\"\n}");
		assertEquals(secureResponse.response(), response);
		secureResponse.print();
	}

	@Test
	public void secureApiResponseJsonPathProtectedKeys() {
		ApiResponse response = response(200,
				"{\"key1\": {\"subkey\": \"value\"},\n\"key2\": {\"subkey\": \"value\"}\n}");
		SecureResponse secureResponse = SecureResponse.from(response).redact("/key2/subkey");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"key1\": {\n    \"subkey\": \"value\"\n  }"));
		assertTrue(log.contains("\"key2\": {\n    \"subkey\": \"********\"\n  }"));
	}

	@Test
	public void secureApiResponseRedactsJsonParentPath() {
		ApiResponse response = response(200,
				"{\"key1\": {\"subkey\": \"value\"},\n\"key2\": {\"subkey\": \"value\"}\n}");
		SecureResponse secureResponse = SecureResponse.from(response).redact("/key2");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"key1\": {\n    \"subkey\": \"value\"\n  }"));
		assertTrue(log.contains("\"key2\": \"" + SecureValue.DEFAULT_MASK + "\""));
	}

	@Test
	public void secureApiResponseRedactsJsonArrayParentPath() {
		ApiResponse response = response(200, "{\"users\":[{\"name\":\"sam\"},{\"name\":\"bob\"}],\"status\":\"ok\"}");
		SecureResponse secureResponse = SecureResponse.from(response).redact("/users");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"users\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"status\": \"ok\""));
	}

	@Test
	public void secureApiResponseRedactsJsonArrayChildPath() {
		ApiResponse response = response(200, "{\"users\":[{\"name\":\"sam\"},{\"name\":\"bob\"}],\"status\":\"ok\"}");
		SecureResponse secureResponse = SecureResponse.from(response).redact("/users/0/name");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"users\": [\n    {\n      \"name\": \"********\"\n    },\n"
				+ "    {\n      \"name\": \"bob\"\n    }\n  ]"));
		assertTrue(log.contains("\"status\": \"ok\""));
	}

	@Test
	public void secureApiResponseSupportsSliceRedactionRules() {
		ApiResponse response = response(200,
				"{\"index0\":\"ABCDE\",\"range13\":\"ABCDE\",\"range0Minus1\":\"ABCDE\","
						+ "\"keepLastByEnd\":\"1234-4567-7890-0987\",\"keepLastByStart\":\"1234-4567-7890-0987\","
						+ "\"singleNegativeIndex\":\"1234-4567-7890-0987\"}",
				Map.of());
		String log = SecureResponse.from(response).redact("index0[0]", "range13[1:3]", "range0Minus1[0:-1]",
				"keepLastByEnd[:-4]", "keepLastByStart[-4:]", "singleNegativeIndex[-4]").log();
		assertTrue(log.contains("\"index0\": \"********A\""));
		assertTrue(log.contains("\"range13\": \"********BC\""));
		assertTrue(log.contains("\"range0Minus1\": \"********ABCD\""));
		assertTrue(log.contains("\"keepLastByEnd\": \"********1234-4567-7890-\""));
		assertTrue(log.contains("\"keepLastByStart\": \"********0987\""));
		assertTrue(log.contains("\"singleNegativeIndex\": \"********0\""));
	}

	@Test
	public void secureContextCanLoadIniFromFile() throws Exception {
		SecureContext secure = SecureContext.create()
				.load(SecureTest.class.getClassLoader().getResourceAsStream("secure-rules.ini"));
		SecureRequest login = secure.from(loginRequest());
		assertEquals(login.log(false),
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = ********json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"{{SSN}}\"\n}");
	}

	@Test
	public void secureContextCanLoadIniStyleSettings() throws Exception {
		String config = "username\nContent-Type[-4:]\n/key2/subkey\n";

		SecureContext secure = SecureContext.create()
				.load(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
		SecureRequest login = secure.from(loginRequest());
		assertEquals(login.log(false),
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = ********json\n\nBody: [raw] {\n"
						+ "  \"username\": \"********\",\n  \"password\": \"********\",\n  \"ssn\": \"{{SSN}}\"\n}");
	}

	@Test
	public void secureApiResponseRedactsWildcardJsonPathUnderProductsOnly() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				Map.of());

		SecureResponse secureResponse = SecureResponse.from(response).redactionPolicy(RedactionPolicy.defaults())
				.redact("/products/*/reviews");

		String log = secureResponse.log(true);

		assertTrue(log.contains("\"products\": ["));
		assertTrue(log.contains("\"id\": 1"));
		assertTrue(log.contains("\"id\": 2"));
		assertTrue(log.contains("\"reviews\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"orders\": ["));
		assertTrue(log.contains("order@test.com"));
		assertTrue(log.contains("\"comment\": \"keep\""));
	}

	@Test
	public void secureApiResponseRedactsAllReviewsWithDoubleStarPath() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				Map.of());

		SecureResponse secureResponse = SecureResponse.from(response).redactionPolicy(RedactionPolicy.defaults())
				.redact("/**/reviews");

		String log = secureResponse.log(true);
		assertTrue(log.contains("\"reviews\": \"" + SecureValue.DEFAULT_MASK + "\""));
	}

	@Test
	public void secureApiResponseRedactsWildcardJsonPathWithSlice() {
		ApiResponse response = response(200, "{\"products\":[{\"id\":1,\"cardNumber\":\"1234-4567-7890-0987\"},"
				+ "{\"id\":2,\"cardNumber\":\"1111-2222-3333-4444\"}]}");

		SecureResponse secureResponse = SecureResponse.from(response).redactionPolicy(RedactionPolicy.defaults())
				.redact("/products/*/cardNumber[-4:]");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"cardNumber\": \"********0987\""));
		assertTrue(log.contains("\"cardNumber\": \"********4444\""));
	}

	@Test
	public void secureApiResponseRedactsCaseInsensitiveRegexKeyRules() {
		ApiResponse response = response(200,
				"{\"accessToken\":\"abc123\",\"id_token\":\"id123\",\"username\":\"sam\"}");

		SecureResponse secureResponse = SecureResponse.from(response)
				.redactionPolicy(RedactionPolicy.builder().protectRule("regex:(?i).*token.*").build());

		String log = secureResponse.log(true);
		assertTrue(log.contains("\"accessToken\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"id_token\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"username\": \"sam\""));

		secureResponse = SecureResponse.from(response)
				.redactionPolicy(secureResponse.redactionPolicy().removeRules("regex:(?i).*token.*"));
		log = secureResponse.log(true);
		assertTrue(log.contains("\"accessToken\": \"abc123\""));
		assertTrue(log.contains("\"id_token\": \"id123\""));
		assertTrue(log.contains("\"username\": \"sam\""));
	}

	@Test
	public void secureApiResponseRedactsRegexJsonPathRules() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				Map.of());

		SecureResponse secureResponse = SecureResponse.from(response)
				.redactionPolicy(RedactionPolicy.builder().protectRule("regex:/products/\\d+/reviews").build());

		String log = secureResponse.log(true);

		assertTrue(log.contains("\"products\": ["));
		assertTrue(log.contains("\"reviews\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"orders\": ["));
		assertTrue(log.contains("order@test.com"));
		assertTrue(log.contains("\"comment\": \"keep\""));
	}

	@Test
	public void secureApiResponseRedactsRegexJsonPathForSingleDigitProductIndexes() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				Map.of());

		SecureResponse secureResponse = SecureResponse.from(response)
				.redactionPolicy(RedactionPolicy.builder().protectRule("regex:/products/[0-9]/reviews").build());

		String log = secureResponse.log(true);

		assertTrue(log.contains("\"products\": ["));
		assertTrue(log.contains("\"id\": 1"));
		assertTrue(log.contains("\"id\": 2"));
		assertTrue(log.contains("\"reviews\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"orders\": ["));
		assertTrue(log.contains("order@test.com"));
		assertTrue(log.contains("\"comment\": \"keep\""));
	}

	@Test
	public void redactionPolicyCanRemoveRegexJsonPathRule() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]}]}",
				Map.of());
		RedactionPolicy policy = RedactionPolicy.builder().protectRule("regex:/products/[0-9]/reviews").build()
				.removeRules("regex:/products/[0-9]/reviews");
		String log = SecureResponse.from(response).redactionPolicy(policy).log(true);
		assertTrue(log.contains("a@test.com"));
		assertTrue(log.contains("\"comment\": \"bad\""));
	}

	@Test
	public void redactionPolicySliceWrapperCanParseAnotherSliceExpression() {
		RedactionPolicy policy = RedactionPolicy.builder().protectRule("cardNumber[-4:]").build();
		SliceExpressionFactory slice = policy.sliceExpressionFor("cardNumber");
		SliceExpressionFactory parsed = slice.parse("[0:4]");
		assertEquals(parsed.mask("1234-4567-7890-0987", "********"), "********1234");
	}

	@Test
	public void secureApiResponseExistsSupportsSimpleJsonPath() {
		ApiResponse response = response(200,
				"{\"accessToken\":\"abc123\",\"products\":[{\"id\":1,\"title\":\"Phone\"}]}");
		SecureResponse secureResponse = SecureResponse.from(response);
		assertTrue(secureResponse.exists("accessToken"));
		assertTrue(secureResponse.exists("products[0].id"));
		assertTrue(secureResponse.exists("products[0].title"));
	}

	@Test
	public void secureApiResponseExistsSupportsWildcardJsonPathRules() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\"}]}],\"orders\":[{\"id\":10,\"comments\":[]}]}",
				Map.of());
		SecureResponse secureResponse = SecureResponse.from(response);
		assertTrue(secureResponse.exists("/products/*/reviews"));
		assertTrue(secureResponse.exists("/**/reviews"));
	}

	@Test
	public void secureApiResponseExistsSupportsRegexRules() {
		ApiResponse response = response(200,
				"{\"token\":\"abc123\",\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\"}]}],"
						+ "\"orders\":[{\"id\":10,\"reviews\":[{\"comment\":\"keep\"}]}]}",
				Map.of());
		SecureResponse secureResponse = SecureResponse.from(response);
		assertTrue(secureResponse.exists("regex:.*token.*"));
		assertTrue(secureResponse.exists("regex:/products/\\d+/reviews"));
	}

	@Test
	public void secureContextCopyKeepsConfigurationButNotLatestState() {
		SecureContext secure = SecureContext.create().plain(Params.asMap("baseUrl", "https://api.example.com"))
				.secret(Params.asMap("accessToken", "real-token", "API-KEY", "real-api-key")).redact("username")
				.filter("id");

		SecureContext first = secure.copy();
		SecureContext second = secure.copy();

		SecureRequest login = first.from(loginRequest());
		SecureRequest user = second.from(userRequest());

		String firstLog = first.log(false);
		String secondLog = second.log(false);
		String originalLog = secure.log(false);

		assertTrue(firstLog.contains("Login"));
		assertFalse(firstLog.contains("Get User"));
		assertTrue(secondLog.contains("Get User"));
		assertFalse(secondLog.contains("Login"));
		assertEquals(originalLog, "");

		assertEquals(login.build().toUrl(), "https://api.example.com/login");
		assertEquals(user.build().toUrl(), "https://api.example.com/users/123");
		assertEquals(login.build().getHeader().get("Authorization"), "Bearer real-token");
		assertTrue(login.log(false).contains("\"username\": \"********\""));

		ApiResponse response = response(200, "{\"id\":1,\"token\":\"abc\"}");
		assertEquals(first.from(response).filtered(), "{\n  \"id\": 1\n}");
		assertEquals(first.statusCode(), 200);
		assertTrue(first.exists("id"));
		assertEquals(first.path("id"), 1);
		assertEquals(first.paths("id").toString(), "[1]");
		assertEquals(SecureContext.callerMethodName(1), "secureContextCopyKeepsConfigurationButNotLatestState");
	}

	@Test
	public void cacheCanUseExplicitKeyAndReuseValue() {
		SecureContext secure = SecureContext.create();
		AtomicInteger calls = new AtomicInteger();

		String first = secure.cache("token", () -> {
			calls.incrementAndGet();
			return "abc123";
		});

		String second = secure.cache("token", () -> {
			calls.incrementAndGet();
			return "new-value";
		});

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
	}

	@Test
	public void cacheStoresFailureAndLaterCallsThrowCachedFailureException() {
		SecureContext secure = SecureContext.create();
		AtomicInteger calls = new AtomicInteger();

		try {
			secure.cache("token", () -> {
				calls.incrementAndGet();
				throw new RuntimeException("Token failed");
			});
			fail("Expected first cache call to fail");
		} catch (RuntimeException e) {
			assertEquals(e.getMessage(), "Token failed");
		}

		try {
			secure.cache("token", () -> {
				calls.incrementAndGet();
				return "abc123";
			});
			fail("Expected cached failure");
		} catch (SecureContext.CachedFailureException e) {
			assertTrue(e.getMessage().contains("token"));
			assertEquals(e.getCause().getMessage(), "Token failed");
		}

		assertEquals(calls.get(), 1);
	}

	@Test
	public void jPostmanAssertionErrorKeepsSecureLogAndCause() {
		AssertionError original = new AssertionError("Original failure");
		String secureLog = "secure log";
		JPostmanAssertionError error = JPostmanAssertionError.wrap(original, secureLog);
		assertTrue(error.getMessage().contains("Original failure"));
		assertEquals(error.getCause(), original);
		assertEquals(error.secureLog(), secureLog);
		assertEquals(error.getSuppressed().length, 1);
		assertTrue(error.getSuppressed()[0].getMessage().contains(secureLog));
	}

	@Test
	public void secureContextLogsLatestRequestAndResponse() {
		SecureContext secure = SecureContext.create().plain("key1", "value1").secret("key2", "secret2");
		secure.from(loginRequest());
		assertEquals(secure.log(),
				"\n********** SecureRequest: **********\n"
						+ "[POST  ] Login                                    -> /login\nHeaders:\n"
						+ "  Authorization                       = ********\n"
						+ "  API-KEY                             = ********\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"\"\n}");
		ApiResponse response = response(200, "{\"creditCard\":\"1234-4567-7890-0987\"}");
		secure.from(response);
		assertEquals(secure.log(),
				"\n********** SecureRequest: **********\n"
						+ "[POST  ] Login                                    -> /login\nHeaders:\n"
						+ "  Authorization                       = ********\n"
						+ "  API-KEY                             = ********\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"\"\n}\n\n"
						+ "**********SecureResponse: **********\nStatus Code: 200\nBody: {\n"
						+ "  \"creditCard\": \"1234-4567-7890-0987\"\n}\n");

		assertEquals(secure.get("key1"), "value1");
		assertEquals(secure.get("key2"), "secret2");
		assertEquals(secure.asString("key2"), "secret2");
	}

	@Test
	public void secureApiResponseFiltersNestedWildcardPaths() {
		ApiResponse response = response(200,
				"[{\"id\":29,\"title\":\"Juice\",\"rating\":1,\"reviews\":["
						+ "{\"rating\":2,\"comment\":\"Excellent quality!\",\"date\":\"2025-04-30T09:41:02.053Z\"},"
						+ "{\"rating\":3,\"comment\":\"Would buy again!\",\"date\":\"2025-04-30T09:41:02.053Z\"}]}]",
				Map.of());
		SecureContext secure = SecureContext.create().filter("id", "title", "/reviews/*/date", "/**/rating");
		SecureResponse secureResponse = secure.from(response);
		assertEquals(secureResponse.filtered(),
				"[\n  {\n    \"id\": 29,\n    \"title\": \"Juice\",\n    \"rating\": 1,\n"
						+ "    \"reviews\": [\n      {\n        \"rating\": 2,\n"
						+ "        \"date\": \"2025-04-30T09:41:02.053Z\"\n      },\n      {\n"
						+ "        \"rating\": 3,\n        \"date\": \"2025-04-30T09:41:02.053Z\"\n      }\n    ]\n  }\n]");
	}

	@Test
	public void secureApiResponsePathsReturnsValuesForRecursiveWildcardRule() {
		ApiResponse response = response(200,
				"{\"products\":[{\"id\":1,\"title\":\"Essence Mascara Lash Princess\"},"
						+ "{\"id\":2,\"title\":\"Eyeshadow Palette\"},{\"id\":3,\"title\":\"Powder Canister\"}]}",
				Map.of());
		SecureResponse res = SecureResponse.from(response);
		assertEquals(res.paths("/**/id"), Params.asList(1, 2, 3));
	}

	@Test
	public void secureApiResponsePathReturnsFirstValueForRecursiveWildcardRule() {
		ApiResponse response = response(200, "{\"products\":[{\"id\":101,\"rating\":3.47},{\"id\":104,\"rating\":4.15},"
				+ "{\"id\":105,\"rating\":3.62}]}", Map.of());
		SecureResponse res = SecureResponse.from(response);
		assertEquals(res.path("/**/rating"), 3.47);
		assertEquals(res.paths("/**/rating"), Params.asList(3.47, 4.15, 3.62));
	}

	@Test
	public void regexpSliceCanDisplayOnlyPhoneCountryCode() {
		ApiResponse response = response(200,
				"{\"phone\":\"+81 965-431-3024\",\"backupPhone\":\"+1 999-999-9999\",\"otherPhone\":\"+12 555-1234\"}",
				Map.of());

		// Match fields by key name using a regex rule.
		SecureResponse secureResponse = SecureResponse.from(response).redact("regex:(?i).*phone.*");
		String pretty = secureResponse.pretty();
		assertTrue(pretty.contains("\"phone\": \"********\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"********\""), pretty);
		assertTrue(pretty.contains("\"otherPhone\": \"********\""), pretty);

		// Keep only the first three characters by using a slice rule.
		secureResponse = SecureResponse.from(response).redact("phone[:3]", "backupPhone[:3]", "otherPhone[:3]");
		pretty = secureResponse.pretty();
		assertTrue(pretty.contains("\"phone\": \"********+81\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"********+1 \""), pretty);
		assertTrue(pretty.contains("\"otherPhone\": \"********+12\""), pretty);

		// Keep only the value part matched by the regex.
		secureResponse = SecureResponse.from(response).redact("phone[regex:^\\+\\d{1,2}]",
				"backupPhone[regex:^\\+\\d{1,2}]", "otherPhone[regex:^\\+\\d{1,2}]");
		pretty = secureResponse.pretty();
		assertTrue(pretty.contains("\"phone\": \"+81\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"+1\""), pretty);
		assertTrue(pretty.contains("\"otherPhone\": \"+12\""), pretty);
		assertFalse(pretty.contains("965-431-3024"), pretty);
		assertFalse(pretty.contains("999-999-9999"), pretty);

		secureResponse = SecureContext.create().redactRegex("(?i).*phone.*", "[:3]").from(response);
		pretty = secureResponse.pretty();
		assertTrue(pretty.contains("\"phone\": \"********+81\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"********+1 \""), pretty);
		assertTrue(pretty.contains("\"otherPhone\": \"********+12\""), pretty);

		secureResponse = SecureContext.create().redactRegex("(?i).*phone.*", "[regex:^\\+\\d{1,2}]").from(response);
		pretty = secureResponse.pretty();
		assertTrue(pretty.contains("\"phone\": \"+81\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"+1\""), pretty);
		assertTrue(pretty.contains("\"otherPhone\": \"+12\""), pretty);

		secureResponse = SecureContext.create().redactRegex("(?i).*phone.*").from(response);
		pretty = secureResponse.pretty();
		assertTrue(pretty.contains("\"phone\": \"********\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"********\""), pretty);
		assertTrue(pretty.contains("\"otherPhone\": \"********\""), pretty);
	}

	@Test
	public void policySplitKeepsRegexpCommasInsideSliceRules() throws Exception {
		String policy = "[default]\nredact=phone[regex:^\\+\\d{1,2}],backupPhone[regex:^\\+\\d{1,2}]\n";
		ApiResponse response = response(200, "{\"phone\":\"+81 965-431-3024\",\"backupPhone\":\"+1 999-999-9999\"}");

		SecureContext secure = SecureContext.create()
				.loadPolicy(new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)));

		String pretty = secure.from(response).pretty();

		assertTrue(pretty.contains("\"phone\": \"+81\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"+1\""), pretty);
		assertFalse(pretty.contains("965-431-3024"), pretty);
		assertFalse(pretty.contains("999-999-9999"), pretty);
	}

	@Test
	public void iniPolicyCanUseRedactRegexWithValueExpressions() throws Exception {
		String policy = "[default]\nredactRegex=(?i).*phone.* -> [:3],(?i).*mobile.* -> [regex:^\\+\\d{1,2}],other -> [regex:^\\+\\S+]\n";
		ApiResponse response = response(200,
				"{\"phone\":\"+81 965-431-3024\",\"backupPhone\":\"+1 999-999-9999\",\"mobile\":\"+1 555-1234\",\"other\":\"+1 123-4567\"}",
				Map.of());

		SecureContext secure = SecureContext.create()
				.loadPolicy(new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)));

		String pretty = secure.from(response).pretty();

		assertTrue(pretty.contains("\"phone\": \"********+81\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"********+1 \""), pretty);
		assertTrue(pretty.contains("\"mobile\": \"+1\""), pretty);
		assertTrue(pretty.contains("\"other\": \"+1\""), pretty);
		assertFalse(pretty.contains("965-431-3024"), pretty);
		assertFalse(pretty.contains("555-1234"), pretty);
	}

	@Test
	public void regexSliceExpressionCanParseAnotherSliceExpression() {
		SliceExpressionFactory regexSlice = new DefaultSliceExpressionFactory().parse("[regex:^\\+\\d{1,2}]");
		SliceExpressionFactory parsed = regexSlice.parse("[:3]");
		assertEquals(parsed.mask("+81 965-431-3024", "********"), "********+81");
	}

	@Test
	public void redactionPolicyCanRemoveRegexKeySliceRule() {
		ApiResponse response = response(200, "{\"phone\":\"+81 965-431-3024\",\"backupPhone\":\"+1 999-999-9999\"}");

		RedactionPolicy policy = RedactionPolicy.defaults().addRegexRule("(?i).*phone.*", "[:3]")
				.removeRules("regex:(?i).*phone.*");

		String pretty = SecureResponse.from(response).redactionPolicy(policy).pretty();
		assertTrue(pretty.contains("\"phone\": \"+81 965-431-3024\""), pretty);
		assertTrue(pretty.contains("\"backupPhone\": \"+1 999-999-9999\""), pretty);
	}

	@Test
	public void secureContextHeaderFilterReplacesExistingRules() {
		ApiResponse response = response(200, "{\"status\":\"ok\"}",
				Map.of("X-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));
		SecureContext secure = SecureContext.create().headersFilter("X-Cookie", "X-Trace-Id").headersFilter("X-Cookie");

		String log = secure.from(response).log(true);

		assertTrue(log.contains("X-Cookie                            = [refreshToken=jwt-value]"), log);
		assertFalse(log.contains("X-Trace-Id"), log);
		assertFalse(log.contains("trace-secret"), log);
	}

	@Test
	public void secureResponsePathsSupportsExactFieldNamesAfterRuleRefactor() {
		ApiResponse response = response(200, "{\"products\":[{\"id\":1,\"title\":\"A\"},{\"id\":2,\"title\":\"B\"}]}");

		SecureResponse secureResponse = SecureResponse.from(response);

		assertEquals(secureResponse.paths("id").toString(), Params.asList(1, 2).toString());
	}

	@Test
	public void secureHeadersCanUseRegexRulesAndUnheaders() {
		ApiResponse response = response(200, "{\"status\":\"ok\"}",
				Map.of("X-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));

		SecureContext secure = SecureContext.create().headers("regex:.*cookie.*").headersFilter("regex:^x-.*");

		String log = secure.from(response).log(true);
		assertTrue(log.contains("X-Cookie                            = ********"), log);
		assertTrue(log.contains("X-Trace-Id                          = [trace-secret]"), log);

		log = secure.unheaders("regex:.*cookie.*").from(response).log(true);
		assertTrue(log.contains("X-Cookie                            = [refreshToken=jwt-value]"), log);

		// Test unheaders for request
		secure = SecureContext.create().headers("Content-Type").request(loginRequest());
		log = secure.request().log(true);
		assertTrue(log.contains("Content-Type                        = ********"), log);

		log = secure.request().unheaders("Content-Type").log(true);
		assertTrue(log.contains("Content-Type                        = application/json"), log);

		// Test unheaders for response
		log = secure.headers("X-Cookie").response(response).log(true);
		assertTrue(log.contains("X-Cookie                            = ********"), log);

		log = secure.response().unheaders("X-Cookie").log(true);
		assertTrue(log.contains("X-Cookie                            = [refreshToken=jwt-value]"), log);
	}

	@Test
	public void secureContextLoadsIniPolicyProfilesAndClonesForRules() throws Exception {
		String policy = "# Default policy rules\n[default]\nunsecret=baseUrl\n"
				+ "redact=api-key,password,token\nheaders=Authorization,Set-Cookie\n"
				+ "headersFilter=Date,Set-Cookie\n\n[login]\nextends=default\nredact=username\n"
				+ "\n[debug_login]\nextends=login\nunredact=username\nunheaders=Set-Cookie\n"
				+ "headersFilter=Set-Cookie\n";

		SecureContext base = SecureContext
				.create().secret(Params.asMap("baseUrl", "https://api.example.com", "accessToken", "real-token",
						"API-KEY", "real-api-key"))
				.loadPolicy(new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)));

		assertFalse(base.values().get("baseUrl").isProtected());
		assertFalse(base.redactionPolicy().isProtectedKey("username"));

		SecureContext login = base.loadRules("login");
		assertTrue(login.redactionPolicy().isProtectedKey("username"));
		assertFalse(base.redactionPolicy().isProtectedKey("username"));

		ApiResponse response = response(200, "{\"status\":\"ok\"}", Map.of("Date", List.of("Mon"), "Set-Cookie",
				List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));

		String loginLog = login.from(response).log(true);
		assertTrue(loginLog.contains("Date                                = [Mon]"), loginLog);
		assertTrue(loginLog.contains("Set-Cookie                          = ********"), loginLog);
		assertFalse(loginLog.contains("X-Trace-Id"), loginLog);

		SecureContext debugLogin = base.loadRules("debug_login");
		String debugLog = debugLogin.from(response).log(true);
		assertFalse(debugLog.contains("Date"), debugLog);
		assertTrue(debugLog.contains("Set-Cookie                          = [refreshToken=jwt-value]"), debugLog);
		assertFalse(debugLogin.redactionPolicy().isProtectedKey("username"));
	}

	@Test
	public void shouldCreateSecureResponseFromApiExecutor() {
		ApiResponse response = response(200, "{\"status\":\"ok\"}");
		ApiExecutor executor = () -> response;
		SecureResponse secureResponse = SecureContext.create().from(executor);
		assertEquals(secureResponse.statusCode(), 200);
		assertEquals(secureResponse.pretty(), "{\n  \"status\": \"ok\"\n}");

		SecureContext secure = SecureContext.create().response(executor);
		assertEquals(secure.response().statusCode(), 200);
		assertEquals(secure.response().pretty(), "{\n  \"status\": \"ok\"\n}");
	}

	@Test
	public void secureValueKeepsOriginalObjectTypes() {
		Map<String, Object> profile = Map.of("id", 123, "active", true, "roles", List.of("admin", "tester"));
		SecureValue secret = SecureValue.secret(profile);
		SecureValue plain = SecureValue.plain(profile);

		assertEquals(secret.reveal(), profile);
		assertEquals(secret.toString(), SecureValue.DEFAULT_MASK);
		assertEquals(plain.reveal(), profile);
		assertEquals(plain.toString(), profile.toString());
	}

	@Test
	public void secureValuesKeepsMapListNumberAndBooleanTypes() {
		Map<String, Object> profile = Map.of("id", 123, "active", true, "roles", List.of("admin", "tester"));
		SecureValues values = SecureValues.builder().plain("count", 5).plain("enabled", true).plain("profile", profile)
				.secret("token", "abc123").secret("secretProfile", profile).build();

		assertEquals(values.get("count").reveal(), 5);
		assertEquals(values.get("enabled").reveal(), true);
		assertEquals(values.get("profile").reveal(), profile);
		assertEquals(values.get("secretProfile").reveal(), profile);
		assertEquals(values.asMap().get("count"), 5);
		assertEquals(values.asMap().get("enabled"), true);
		assertEquals(values.asMap().get("profile"), profile);
		assertEquals(values.asMap().get("token"), SecureValue.DEFAULT_MASK);
		assertEquals(values.asMap().get("secretProfile"), SecureValue.DEFAULT_MASK);
	}

	@Test
	public void redactRegexCanUsePrefixAndSuffixAroundRegexValueExpression() {
		ApiResponse response = response(200, "{\"title\":\"Manager\"}");

		assertTrue(SecureContext.create().redactRegex("title", "[regex:\\S+]").from(response).pretty()
				.contains("\"title\": \"Manager\""));

		assertTrue(SecureContext.create().redactRegex("title", "****[regex:\\S+]").from(response).pretty()
				.contains("\"title\": \"****Manager\""));

		assertTrue(SecureContext.create().redactRegex("title", "****[regex:\\S+]****").from(response).pretty()
				.contains("\"title\": \"****Manager****\""));

		assertTrue(SecureContext.create().redactRegex("title", "[regex:\\S+]****").from(response).pretty()
				.contains("\"title\": \"Manager****\""));
	}

	@Test
	public void filterListCanKeepFullFirstItemAndSelectedFieldsForOtherItems() throws Exception {
		String policy = "[default]\nfilterList=/**/reviews[0],/**/reviews/*/rating,/**/reviews/*/reviewerName\n"
				+ "redact=regex:(?i).*email.*\n";

		ApiResponse response = response(200, "{\"products\":[{\"id\":1,\"title\":\"Mascara\",\"reviews\":["
				+ "{\"rating\":3,\"comment\":\"Would not recommend!\",\"reviewerName\":\"Eleanor\",\"reviewerEmail\":\"e@example.com\"},"
				+ "{\"rating\":4,\"comment\":\"Very satisfied!\",\"reviewerName\":\"Lucas\",\"reviewerEmail\":\"l@example.com\"}"
				+ "]}]}");

		SecureContext secure = SecureContext.create()
				.loadPolicy(new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)));

		String filtered = secure.from(response).filtered();

		assertTrue(filtered.contains("\"title\": \"Mascara\""), filtered);
		assertTrue(filtered.contains("\"comment\": \"Would not recommend!\""), filtered);
		assertTrue(filtered.contains("\"reviewerEmail\": \"********\""), filtered);
		assertTrue(filtered.contains("\"rating\": 4"), filtered);
		assertTrue(filtered.contains("\"reviewerName\": \"Lucas\""), filtered);
		assertFalse(filtered.contains("Very satisfied!"), filtered);
		assertFalse(filtered.contains("l@example.com"), filtered);

		SecureResponse secureResponse = SecureResponse.from(response).redact("regex:(?i).*email.*")
				.filterList("/**/reviews[0]", "/**/reviews/*/rating", "/**/reviews/*/reviewerName");

		filtered = secureResponse.filtered();

		assertTrue(filtered.contains("\"title\": \"Mascara\""), filtered);
		assertTrue(filtered.contains("\"comment\": \"Would not recommend!\""), filtered);
		assertTrue(filtered.contains("\"reviewerEmail\": \"********\""), filtered);
		assertTrue(filtered.contains("\"rating\": 4"), filtered);
		assertTrue(filtered.contains("\"reviewerName\": \"Lucas\""), filtered);
		assertFalse(filtered.contains("Very satisfied!"), filtered);
		assertFalse(filtered.contains("l@example.com"), filtered);
	}

	@Test
	public void responseCanUseCurrentContextFunction() {
		ApiResponse response = response(200, "{\"accessToken\":\"abc123\"}");
		SecureContext cxt = SecureContext.create().request(loginRequest()).response(ctx -> response);
		assertTrue(cxt.exists("accessToken"), "Access token not found");
		assertEquals(cxt.response().statusCode(), 200);
	}

	private static ApiResponse response(int statusCode, String body) {
		return response(statusCode, body, Map.of());
	}

	private static ApiResponse response(int statusCode, String body, Map<String, List<String>> headers) {
		return new ApiResponse(statusCode, body, new byte[0], headers);
	}

	private static Request loginRequest() {
		String json = "{\"method\":\"POST\",\"url\":{\"raw\":\"{{baseUrl}}/login\","
				+ "\"host\":[\"{{baseUrl}}\"],\"path\":[\"login\"]},\"header\":["
				+ "{\"key\":\"Authorization\",\"value\":\"Bearer {{accessToken}}\"},"
				+ "{\"key\":\"API-KEY\",\"value\":\"{{API-KEY}}\"},"
				+ "{\"key\":\"Content-Type\",\"value\":\"application/json\"}],\"body\":{\"mode\":\"raw\","
				+ "\"raw\":\"{\\\"username\\\":\\\"sam\\\",\\\"password\\\":\\\"secret\\\",\\\"ssn\\\":\\\"{{SSN}}\\\"}\""
				+ "}}";
		return Request.from("Login", "secure", JsonParser.parseString(json).getAsJsonObject());
	}

	private static Request userRequest() {
		String json = "{\"method\":\"GET\",\"url\":{\"raw\":\"{{baseUrl}}/users/123\","
				+ "\"host\":[\"{{baseUrl}}\"],\"path\":[\"users\",\"123\"]},\"header\":["
				+ "{\"key\":\"Authorization\",\"value\":\"Bearer {{accessToken}}\"},"
				+ "{\"key\":\"API-KEY\",\"value\":\"{{API-KEY}}\"},"
				+ "{\"key\":\"Content-Type\",\"value\":\"application/json\"}]}";
		return Request.from("Get User", "secure", JsonParser.parseString(json).getAsJsonObject());
	}
}
