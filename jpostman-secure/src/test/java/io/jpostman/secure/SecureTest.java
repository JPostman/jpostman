package io.jpostman.secure;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.gson.JsonParser;

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
		ApiResponse response = new ApiResponse(200, "{\"creditCard\":\"1234-4567-7890-0987\"}", new byte[0], Map.of());
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
		ApiResponse response = new ApiResponse(200, "{\"token\":\"abc123\",\"trace\":\"trace-secret\"}", new byte[0],
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
		ApiResponse response = new ApiResponse(200, "{\"status\":\"ok\"}", new byte[0],
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
		ApiResponse response = new ApiResponse(200, "{\"status\":\"ok\"}", new byte[0],
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

		assertTrue(resolved.contains("Authorization                       = Bearer ********"));

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
		secure.unsecret("accessToken");
		login = secure.from(loginRequest());
		String afterUnsecret = login.log();
		assertTrue(afterUnsecret.contains("Authorization                       = Bearer real-token"), afterUnsecret);
		assertEquals(secure.values().get("accessToken").isProtected(), false);
	}

	@Test
	public void secureApiResponseRedactsKnownValuesAndProtectedKeys() {
		ApiResponse response = new ApiResponse(200,
				"{\"accessToken\":\"abc123\",\"username\":\"testuser\",\"apiKey\":\"key-1\","
						+ "\"token\":\"ABCD\",\"ssn\":\"999-999-9999\",\"creditCard\":\"1234-4567-7890-0987\"}",
				new byte[0], Map.of("Authorization", Params.asList("Bearer abc123")));

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
		ApiResponse response = new ApiResponse(200,
				"{\"key1\": {\"subkey\": \"value\"},\n\"key2\": {\"subkey\": \"value\"}\n}", new byte[0], Map.of());
		SecureResponse secureResponse = SecureResponse.from(response).redact("/key2/subkey");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"key1\": {\n    \"subkey\": \"value\"\n  }"));
		assertTrue(log.contains("\"key2\": {\n    \"subkey\": \"********\"\n  }"));
	}

	@Test
	public void secureApiResponseRedactsJsonParentPath() {
		ApiResponse response = new ApiResponse(200,
				"{\"key1\": {\"subkey\": \"value\"},\n\"key2\": {\"subkey\": \"value\"}\n}", new byte[0], Map.of());
		SecureResponse secureResponse = SecureResponse.from(response).redact("/key2");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"key1\": {\n    \"subkey\": \"value\"\n  }"));
		assertTrue(log.contains("\"key2\": \"" + SecureValue.DEFAULT_MASK + "\""));
	}

	@Test
	public void secureApiResponseRedactsJsonArrayParentPath() {
		ApiResponse response = new ApiResponse(200,
				"{\"users\":[{\"name\":\"sam\"},{\"name\":\"bob\"}],\"status\":\"ok\"}", new byte[0], Map.of());
		SecureResponse secureResponse = SecureResponse.from(response).redact("/users");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"users\": \"" + SecureValue.DEFAULT_MASK + "\""));
		assertTrue(log.contains("\"status\": \"ok\""));
	}

	@Test
	public void secureApiResponseRedactsJsonArrayChildPath() {
		ApiResponse response = new ApiResponse(200,
				"{\"users\":[{\"name\":\"sam\"},{\"name\":\"bob\"}],\"status\":\"ok\"}", new byte[0], Map.of());
		SecureResponse secureResponse = SecureResponse.from(response).redact("/users/0/name");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"users\": [\n    {\n      \"name\": \"********\"\n    },\n"
				+ "    {\n      \"name\": \"bob\"\n    }\n  ]"));
		assertTrue(log.contains("\"status\": \"ok\""));
	}

	@Test
	public void secureApiResponseSupportsSliceRedactionRules() {
		ApiResponse response = new ApiResponse(200,
				"{\"index0\":\"ABCDE\",\"range13\":\"ABCDE\",\"range0Minus1\":\"ABCDE\","
						+ "\"keepLastByEnd\":\"1234-4567-7890-0987\",\"keepLastByStart\":\"1234-4567-7890-0987\","
						+ "\"singleNegativeIndex\":\"1234-4567-7890-0987\"}",
				new byte[0], Map.of());
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
				.load(SecureTest.class.getClassLoader().getResourceAsStream("secure-rules.ini"))
				.loadRules("debug_forgot");
		SecureRequest login = secure.from(loginRequest());
		assertEquals(login.log(false),
				"[POST  ] Login                                    -> {{baseUrl}}/login\n"
						+ "Headers:\n  Authorization                       = Bearer {{accessToken}}\n"
						+ "  API-KEY                             = {{API-KEY}}\n"
						+ "  Content-Type                        = ********json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"secret\",\n  \"ssn\": \"{{SSN}}\"\n}");
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
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				new byte[0], Map.of());

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
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				new byte[0], Map.of());

		SecureResponse secureResponse = SecureResponse.from(response).redactionPolicy(RedactionPolicy.defaults())
				.redact("/**/reviews");

		String log = secureResponse.log(true);
		assertTrue(log.contains("\"reviews\": \"" + SecureValue.DEFAULT_MASK + "\""));
	}

	@Test
	public void secureApiResponseRedactsWildcardJsonPathWithSlice() {
		ApiResponse response = new ApiResponse(200, "{\"products\":[{\"id\":1,\"cardNumber\":\"1234-4567-7890-0987\"},"
				+ "{\"id\":2,\"cardNumber\":\"1111-2222-3333-4444\"}]}", new byte[0], Map.of());

		SecureResponse secureResponse = SecureResponse.from(response).redactionPolicy(RedactionPolicy.defaults())
				.redact("/products/*/cardNumber[-4:]");
		String log = secureResponse.log(true);
		assertTrue(log.contains("\"cardNumber\": \"********0987\""));
		assertTrue(log.contains("\"cardNumber\": \"********4444\""));
	}

	@Test
	public void secureApiResponseRedactsCaseInsensitiveRegexKeyRules() {
		ApiResponse response = new ApiResponse(200,
				"{\"accessToken\":\"abc123\",\"id_token\":\"id123\",\"username\":\"sam\"}", new byte[0], Map.of());

		SecureResponse secureResponse = SecureResponse.from(response)
				.redactionPolicy(RedactionPolicy.builder().protectRule("regex:(?i).*token.*").build());

		String log = secureResponse.log(true);
		assertTrue(log.contains("\"accessToken\": " + SecureValue.DEFAULT_MASK));
		assertTrue(log.contains("\"id_token\": " + SecureValue.DEFAULT_MASK));
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
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				new byte[0], Map.of());

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
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\",\"reviewerEmail\":\"b@test.com\"}]}],"
						+ "\"orders\":["
						+ "{\"id\":10,\"reviews\":[{\"comment\":\"keep\",\"reviewerEmail\":\"order@test.com\"}]}]}",
				new byte[0], Map.of());

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
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\",\"reviewerEmail\":\"a@test.com\"}]}]}",
				new byte[0], Map.of());
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
		ApiResponse response = new ApiResponse(200,
				"{\"accessToken\":\"abc123\",\"products\":[{\"id\":1,\"title\":\"Phone\"}]}", new byte[0], Map.of());
		SecureResponse secureResponse = SecureResponse.from(response);
		assertTrue(secureResponse.exists("accessToken"));
		assertTrue(secureResponse.exists("products[0].id"));
		assertTrue(secureResponse.exists("products[0].title"));
	}

	@Test
	public void secureApiResponseExistsSupportsWildcardJsonPathRules() {
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\"}]},"
						+ "{\"id\":2,\"reviews\":[{\"comment\":\"good\"}]}],\"orders\":[{\"id\":10,\"comments\":[]}]}",
				new byte[0], Map.of());
		SecureResponse secureResponse = SecureResponse.from(response);
		assertTrue(secureResponse.exists("/products/*/reviews"));
		assertTrue(secureResponse.exists("/**/reviews"));
	}

	@Test
	public void secureApiResponseExistsSupportsRegexRules() {
		ApiResponse response = new ApiResponse(200,
				"{\"token\":\"abc123\",\"products\":[{\"id\":1,\"reviews\":[{\"comment\":\"bad\"}]}],"
						+ "\"orders\":[{\"id\":10,\"reviews\":[{\"comment\":\"keep\"}]}]}",
				new byte[0], Map.of());
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

		ApiResponse response = new ApiResponse(200, "{\"id\":1,\"token\":\"abc\"}", new byte[0], Map.of());
		assertEquals(first.from(response).filtered(), "{\n  \"id\": 1\n}");
	}

	@Test
	public void secureContextLogsLatestRequestAndResponse() {
		SecureContext secure = SecureContext.create().plain("key1", "value1").secret("key2", "secret2");
		secure.from(loginRequest());
		assertEquals(secure.log(),
				"\n********** SecureRequest: **********\n"
						+ "[POST  ] Login                                    -> /login\nHeaders:\n"
						+ "  Authorization                       = ********\n"
						+ "  API-KEY                             =********\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"\"\n}");
		ApiResponse response = new ApiResponse(200, "{\"creditCard\":\"1234-4567-7890-0987\"}", new byte[0], Map.of());
		secure.from(response);
		assertEquals(secure.log(),
				"\n********** SecureRequest: **********\n"
						+ "[POST  ] Login                                    -> /login\nHeaders:\n"
						+ "  Authorization                       = ********\n"
						+ "  API-KEY                             =********\n"
						+ "  Content-Type                        = application/json\n\nBody: [raw] {\n"
						+ "  \"username\": \"sam\",\n  \"password\": \"********\",\n  \"ssn\": \"\"\n}\n\n"
						+ "**********SecureResponse: **********\nStatus Code: 200\nBody: {\n"
						+ "  \"creditCard\": \"1234-4567-7890-0987\"\n}\n");
	}

	@Test
	public void secureApiResponseFiltersNestedWildcardPaths() {
		ApiResponse response = new ApiResponse(200,
				"[{\"id\":29,\"title\":\"Juice\",\"rating\":1,\"reviews\":["
						+ "{\"rating\":2,\"comment\":\"Excellent quality!\",\"date\":\"2025-04-30T09:41:02.053Z\"},"
						+ "{\"rating\":3,\"comment\":\"Would buy again!\",\"date\":\"2025-04-30T09:41:02.053Z\"}]}]",
				new byte[0], Map.of());
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
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"title\":\"Essence Mascara Lash Princess\"},"
						+ "{\"id\":2,\"title\":\"Eyeshadow Palette\"},{\"id\":3,\"title\":\"Powder Canister\"}]}",
				new byte[0], Map.of());
		SecureResponse res = SecureResponse.from(response);
		assertEquals(res.paths("/**/id"), Params.asList(1, 2, 3));
	}

	@Test
	public void secureContextHeaderFilterReplacesExistingRules() {
		ApiResponse response = new ApiResponse(200, "{\"status\":\"ok\"}", new byte[0],
				Map.of("X-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));
		SecureContext secure = SecureContext.create().headersFilter("X-Cookie", "X-Trace-Id").headersFilter("X-Cookie");

		String log = secure.from(response).log(true);

		assertTrue(log.contains("X-Cookie                            = [refreshToken=jwt-value]"), log);
		assertFalse(log.contains("X-Trace-Id"), log);
		assertFalse(log.contains("trace-secret"), log);
	}

	@Test
	public void secureResponsePathsSupportsExactFieldNamesAfterRuleRefactor() {
		ApiResponse response = new ApiResponse(200,
				"{\"products\":[{\"id\":1,\"title\":\"A\"},{\"id\":2,\"title\":\"B\"}]}", new byte[0], Map.of());

		SecureResponse secureResponse = SecureResponse.from(response);

		assertEquals(secureResponse.paths("id").toString(), Params.asList(1, 2).toString());
	}

	@Test
	public void secureHeadersCanUseRegexRulesAndUnheaders() {
		ApiResponse response = new ApiResponse(200, "{\"status\":\"ok\"}", new byte[0],
				Map.of("X-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));

		SecureContext secure = SecureContext.create().headers("regex:.*cookie.*").headersFilter("regex:^x-.*");

		String log = secure.from(response).log(true);
		assertTrue(log.contains("X-Cookie                            = ********"), log);
		assertTrue(log.contains("X-Trace-Id                          = [trace-secret]"), log);

		log = secure.unheaders("regex:.*cookie.*").from(response).log(true);
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

		ApiResponse response = new ApiResponse(200, "{\"status\":\"ok\"}", new byte[0], Map.of("Date", List.of("Mon"),
				"Set-Cookie", List.of("refreshToken=jwt-value"), "X-Trace-Id", List.of("trace-secret")));

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
	public void unheadersRemovesProtectedHeadersForSecureRequestAndSecureResponse() {
		Request request = Request.from("Header test", "header-test",
				JsonParser.parseString("{\"method\":\"GET\",\"url\":{\"raw\":\"/secure\",\"path\":[\"secure\"]},"
						+ "\"header\":[{\"key\":\"X-Debug-Token\",\"value\":\"request-secret\"},"
						+ "{\"key\":\"X-Trace-Id\",\"value\":\"trace-request\"}]}").getAsJsonObject());

		ApiResponse response = new ApiResponse(200, "{\"status\":\"ok\"}", new byte[0], Map.of("X-Debug-Token",
				Params.asList("response-secret"), "X-Trace-Id", Params.asList("trace-response")));

		SecureRequest secureRequest = SecureRequest.from(request).headers("X-Debug-Token").unheaders("X-Debug-Token");

		SecureResponse secureResponse = SecureResponse.from(response).headers("X-Debug-Token")
				.unheaders("X-Debug-Token");

		String requestLog = secureRequest.log(true);
		String responseLog = secureResponse.log(true);

		assertTrue(requestLog.contains("X-Debug-Token"), requestLog);
		assertTrue(requestLog.contains("request-secret"), requestLog);
		assertFalse(requestLog.contains("X-Debug-Token                       = ********"), requestLog);

		assertTrue(responseLog.contains("X-Debug-Token"), responseLog);
		assertTrue(responseLog.contains("response-secret"), responseLog);
		assertFalse(responseLog.contains("X-Debug-Token                       = ********"), responseLog);
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
