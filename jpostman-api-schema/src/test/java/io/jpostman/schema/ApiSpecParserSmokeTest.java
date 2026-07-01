package io.jpostman.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiBodyType;
import io.jpostman.schema.model.ApiExample;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParseException;
import io.jpostman.schema.parser.ApiSpecParser;
import io.jpostman.schema.parser.ApiSpecParserOptions;
import io.jpostman.schema.util.ApiOperationEnvScanner;

class ApiSpecParserSmokeTest {

	@Test
	void parsesGraphQlSchemaWithBaseUrlEnv() {
		String schema = "\"\"\"User operations\"\"\"\ntype Query {\n  \"\"\"Get user by id\"\"\"\n"
				+ "  getUser(id: ID!, token: String): User\n}\n\ntype User {\n  id: ID!\n  username: String\n}\n";

		ApiSpecParserOptions options = new ApiSpecParserOptions();
		options.setBaseUrl("https://dummy.com/graphql");

		ApiSpec spec = ApiSpecParser.parse(schema, options);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertEquals("GraphQL API", spec.getName());
		assertEquals("https://dummy.com/graphql", spec.getBaseUrl());
		assertEquals("https://dummy.com/graphql", spec.getEnvs().get("BASE_URL"));
		assertEquals("", spec.getEnvs().get("id"));
		assertEquals("", spec.getEnvs().get("accessToken"));
		assertTrue(spec.isOverrideUrl());
		assertEquals("{{BASE_URL}}", operation.getPath());
		assertEquals("getUser", operation.getMethodName());
		assertEquals("Get user by id", operation.getDescription());
		assertEquals("POST", operation.getMethod());
		assertEquals("QUERY", operation.getGraphQlOperationType());
		assertTrue(operation.getBody().getContent().contains("{{id}}"));
		assertTrue(operation.getBody().getContent().contains("{{accessToken}}"));
	}

	@Test
	void parsesOpenApiDocumentWithFolderAuthHeadersQueryBodyAndBaseUrl() {
		String openApi = "openapi: 3.0.3\ninfo:\n  title: Demo API\n  version: 1.0.0\nservers:\n"
				+ "  - url: https://dummy.com\npaths:\n  /auth/login:\n    post:\n      tags:\n"
				+ "        - Auth\n      operationId: loginUser\n"
				+ "      description: Login user and get token.\n      parameters:\n"
				+ "        - name: remember\n          in: query\n          required: false\n"
				+ "          schema:\n            type: string\n          example: \"{{remember}}\"\n"
				+ "        - name: X-Client\n          in: header\n          required: false\n"
				+ "          schema:\n            type: string\n          example: \"{{clientId}}\"\n"
				+ "      security:\n        - bearerAuth: []\n      requestBody:\n        content:\n"
				+ "          application/json:\n            example:\n"
				+ "              username: \"{{username}}\"\n              password: \"{{password}}\"\n"
				+ "components:\n  securitySchemes:\n    bearerAuth:\n      type: http\n      scheme: bearer\n";

		ApiSpec spec = ApiSpecParser.parse(openApi);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertEquals("Demo API", spec.getName());
		assertEquals("https://dummy.com", spec.getBaseUrl());
		assertFalse(spec.isOverrideUrl());
		assertEquals("https://dummy.com", spec.getEnvs().get("BASE_URL"));
		assertEquals("", spec.getEnvs().get("remember"));
		assertEquals("", spec.getEnvs().get("clientId"));
		assertEquals("", spec.getEnvs().get("username"));
		assertEquals("", spec.getEnvs().get("password"));
		assertEquals("", spec.getEnvs().get("accessToken"));

		assertEquals("Auth", spec.getFolders().get(0).getName());
		assertEquals("Auth", operation.getFolder());
		assertEquals("loginUser", operation.getMethodName());
		assertEquals("Login user and get token.", operation.getDescription());
		assertEquals("POST", operation.getMethod());
		assertEquals("/auth/login", operation.getPath());
		assertEquals("remember", operation.getQueryParams().get(0).getName());
		assertEquals("{{remember}}", operation.getQueryParams().get(0).getValue());
		assertEquals("X-Client", operation.getHeaders().get(0).getName());
		assertEquals("{{clientId}}", operation.getHeaders().get(0).getValue());
		assertNotNull(operation.getBody());
		assertNotNull(operation.getAuth());
		assertEquals("{{accessToken}}", operation.getAuth().getValue());
	}

	@Test
	void parsesOpenApiDocumentWithOverrideUrl() {
		String openApi = "openapi: 3.0.3\ninfo:\n  title: Demo API\n  version: 1.0.0\nservers:\n"
				+ "  - url: https://dummy.com\npaths:\n  /users/{{me}}:\n    get:\n      tags:\n"
				+ "        - Users\n      operationId: getCurrentUser\n";

		ApiSpecParserOptions options = new ApiSpecParserOptions();
		options.setOverrideUrl(true);

		ApiSpec spec = ApiSpecParser.parse(openApi, options);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertTrue(spec.isOverrideUrl());
		assertEquals("https://dummy.com", spec.getEnvs().get("BASE_URL"));
		assertEquals("", spec.getEnvs().get("me"));
		assertEquals("{{BASE_URL}}/users/{{me}}", operation.getPath());
	}

	@Test
	void parsesPostmanCollectionWithFolderQueryHeadersBodyAuthAndBaseUrl() {
		String postman = "{\n  \"info\": {\n    \"name\": \"Demo Collection\",\n"
				+ "    \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n"
				+ "  },\n  \"item\": [\n    {\n      \"name\": \"Auth\",\n"
				+ "      \"description\": \"Authentication requests\",\n      \"item\": [\n        {\n"
				+ "          \"name\": \"loginUser\",\n          \"request\": {\n"
				+ "            \"description\": \"Login user and get token.\",\n"
				+ "            \"method\": \"POST\",\n            \"url\": {\n"
				+ "              \"raw\": \"https://dummy.com/auth/login?remember={{remember}}\",\n"
				+ "              \"protocol\": \"https\",\n              \"host\": [\"dummy\", \"com\"],\n"
				+ "              \"path\": [\"auth\", \"login\"],\n              \"query\": [\n"
				+ "                { \"key\": \"remember\", \"value\": \"{{remember}}\" }\n              ]\n"
				+ "            },\n            \"auth\": {\n              \"type\": \"bearer\",\n"
				+ "              \"bearer\": [\n"
				+ "                { \"key\": \"token\", \"value\": \"{{accessToken}}\", \"type\": \"string\" }\n"
				+ "              ]\n            },\n            \"header\": [\n"
				+ "              { \"key\": \"Content-Type\", \"value\": \"application/json\" }\n            ],\n"
				+ "            \"body\": {\n              \"mode\": \"raw\",\n"
				+ "              \"raw\": \"{ \\\"username\\\": \\\"{{username}}\\\", \\\"password\\\": \\\"{{password}}\\\" }\"\n"
				+ "            }\n          }\n        }\n      ]\n    }\n  ]\n}\n";

		ApiSpec spec = ApiSpecParser.parse(postman);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertEquals("Demo Collection", spec.getName());
		assertEquals("https://dummy.com", spec.getBaseUrl());
		assertFalse(spec.isOverrideUrl());
		assertEquals("https://dummy.com", spec.getEnvs().get("BASE_URL"));
		assertEquals("", spec.getEnvs().get("remember"));
		assertEquals("", spec.getEnvs().get("accessToken"));
		assertEquals("", spec.getEnvs().get("username"));
		assertEquals("", spec.getEnvs().get("password"));

		assertEquals("Auth", spec.getFolders().get(0).getName());
		assertEquals("Authentication requests", spec.getFolders().get(0).getDescription());
		assertEquals("Auth", operation.getFolder());
		assertEquals("loginUser", operation.getMethodName());
		assertEquals("Login user and get token.", operation.getDescription());
		assertEquals("POST", operation.getMethod());
		assertEquals("/auth/login", operation.getPath());
		assertEquals("remember", operation.getQueryParams().get(0).getName());
		assertEquals("{{remember}}", operation.getQueryParams().get(0).getValue());
		assertEquals("Content-Type", operation.getHeaders().get(0).getName());
		assertEquals("application/json", operation.getHeaders().get(0).getValue());
		assertNotNull(operation.getBody());
		assertNotNull(operation.getAuth());
		assertEquals("{{accessToken}}", operation.getAuth().getValue());
	}

	@Test
	void parsesPostmanCollectionWithOverrideUrl() {
		String postman = "{\n  \"info\": {\n    \"name\": \"Demo Collection\",\n"
				+ "    \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n"
				+ "  },\n  \"item\": [\n    {\n      \"name\": \"Users\",\n      \"item\": [\n"
				+ "        {\n          \"name\": \"getUser\",\n          \"request\": {\n"
				+ "            \"method\": \"GET\",\n            \"url\": \"https://dummy.com/users/{{me}}\"\n"
				+ "          }\n        }\n      ]\n    }\n  ]\n}\n";

		ApiSpecParserOptions options = new ApiSpecParserOptions();
		options.setOverrideUrl(true);

		ApiSpec spec = ApiSpecParser.parse(postman, options);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertTrue(spec.isOverrideUrl());
		assertEquals("https://dummy.com", spec.getEnvs().get("BASE_URL"));
		assertEquals("", spec.getEnvs().get("me"));
		assertEquals("{{BASE_URL}}/users/{{me}}", operation.getPath());
	}

	@Test
	void placeholderExampleValuesDoNotOverwriteEmptyEnvValues() {
		ApiOperation operation = new ApiOperation();
		operation.setBody(
				new ApiBody(ApiBodyType.JSON, "{ \"username\": \"{{username}}\", \"password\": \"{{password}}\" }"));

		ApiExample example = new ApiExample();
		example.setBody(
				new ApiBody(ApiBodyType.JSON, "{ \"username\": \"{{username}}\", \"password\": \"{{password}}\" }"));
		operation.setExample(example);

		Map<String, Object> envs = new LinkedHashMap<>();
		ApiOperationEnvScanner.scan(operation, envs);

		assertEquals("", envs.get("username"));
		assertEquals("", envs.get("password"));
	}

	@Test
	void realExampleValuesUpdateEnvValues() {
		ApiOperation operation = new ApiOperation();
		operation.setBody(
				new ApiBody(ApiBodyType.JSON, "{ \"username\": \"{{username}}\", \"password\": \"{{password}}\" }"));

		ApiExample example = new ApiExample();
		example.setBody(new ApiBody(ApiBodyType.JSON, "{ \"username\": \"emilys\", \"password\": \"emilyspass\" }"));
		operation.setExample(example);

		Map<String, Object> envs = new LinkedHashMap<>();
		ApiOperationEnvScanner.scan(operation, envs);

		assertEquals("emilys", envs.get("username"));
		assertEquals("emilyspass", envs.get("password"));
	}

	@Test
	void parsesSwagger2Document() {
		String swagger = "swagger: '2.0'\ninfo:\n  title: Swagger Demo API\n  version: 1.0.0\n"
				+ "host: dummy.com\nschemes:\n  - https\nbasePath: /api\npaths:\n"
				+ "  /users/{{me}}:\n    get:\n      tags:\n        - Users\n"
				+ "      operationId: getUser\n      parameters:\n        - name: me\n"
				+ "          in: path\n          required: true\n          schema:\n"
				+ "            type: string\n          example: \"{{me}}\"\n          examples:\n"
				+ "            demo:\n              value: \"123\"\n        - name: limit\n"
				+ "          in: query\n          type: string\n          default: '{{limit}}'\n";

		ApiSpec spec = ApiSpecParser.parse(swagger);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertEquals("Swagger Demo API", spec.getName());
		assertEquals("https://dummy.com/api", spec.getBaseUrl());
		assertEquals("Users", operation.getFolder());
		assertEquals("getUser", operation.getMethodName());
		assertEquals("GET", operation.getMethod());
		assertEquals("/users/{{me}}", operation.getPath());
		assertEquals("", spec.getEnvs().get("me"));
	}

	@Test
	void openApiExamplesUpdateEnvValuesFromPathQueryHeaderAndBody() {
		String openApi = "openapi: 3.0.3\ninfo:\n  title: Example API\n  version: 1.0.0\nservers:\n"
				+ "  - url: https://dummy.com\npaths:\n  /users/{{me}}:\n    post:\n      tags:\n"
				+ "        - Users\n      operationId: updateUser\n      parameters:\n"
				+ "        - name: me\n          in: path\n          required: true\n          schema:\n"
				+ "            type: string\n          example: \"{{me}}\"\n          examples:\n"
				+ "            demo:\n              value: \"123\"\n        - name: limit\n"
				+ "          in: query\n          schema:\n            type: string\n"
				+ "          example: \"{{limit}}\"\n          examples:\n            demo:\n"
				+ "              value: \"25\"\n        - name: X-Client\n          in: header\n"
				+ "          schema:\n            type: string\n          example: \"{{clientId}}\"\n"
				+ "          examples:\n            demo:\n              value: \"web-app\"\n"
				+ "      requestBody:\n        content:\n          application/json:\n"
				+ "            example:\n              username: \"{{username}}\"\n"
				+ "              password: \"{{password}}\"\n            examples:\n              demo:\n"
				+ "                value:\n                  username: emilys\n"
				+ "                  password: emilyspass\n";

		ApiSpec spec = ApiSpecParser.parse(openApi);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertEquals("123", spec.getEnvs().get("me"));
		assertEquals("25", spec.getEnvs().get("limit"));
		assertEquals("web-app", spec.getEnvs().get("clientId"));
		assertEquals("emilys", spec.getEnvs().get("username"));
		assertEquals("emilyspass", spec.getEnvs().get("password"));
		assertEquals("/users/{{me}}", operation.getPath());
		assertNotNull(operation.getExample());
		assertEquals("https://dummy.com/users/123", operation.getExample().getPath());
	}

	@Test
	void openApiExampleBodyDoesNotOverwriteWhenExamplesAreMissing() {
		String openApi = "openapi: 3.0.3\ninfo:\n  title: Example API\n  version: 1.0.0\npaths:\n"
				+ "  /auth/login:\n    post:\n      tags:\n        - Auth\n"
				+ "      operationId: loginUser\n      requestBody:\n        content:\n"
				+ "          application/json:\n            example:\n"
				+ "              username: \"{{username}}\"\n              password: \"{{password}}\"\n";

		ApiSpec spec = ApiSpecParser.parse(openApi);

		assertEquals("", spec.getEnvs().get("username"));
		assertEquals("", spec.getEnvs().get("password"));
	}

	@Test
	void postmanOriginalRequestExampleUpdatesEnvValuesFromPathQueryHeaderAndBody() {
		String postman = "{\n  \"info\": {\n    \"name\": \"Example Collection\",\n"
				+ "    \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n"
				+ "  },\n  \"item\": [\n    {\n      \"name\": \"Users\",\n      \"item\": [\n"
				+ "        {\n          \"name\": \"updateUser\",\n          \"request\": {\n"
				+ "            \"method\": \"POST\",\n            \"url\": {\n"
				+ "              \"raw\": \"https://dummy.com/users/{{me}}?limit={{limit}}\",\n"
				+ "              \"protocol\": \"https\",\n              \"host\": [\"dummy\", \"com\"],\n"
				+ "              \"path\": [\"users\", \"{{me}}\"],\n              \"query\": [\n"
				+ "                { \"key\": \"limit\", \"value\": \"{{limit}}\" }\n              ]\n"
				+ "            },\n            \"header\": [\n"
				+ "              { \"key\": \"X-Client\", \"value\": \"{{clientId}}\" }\n            ],\n"
				+ "            \"body\": {\n              \"mode\": \"raw\",\n"
				+ "              \"raw\": \"{ \\\"username\\\": \\\"{{username}}\\\", \\\"password\\\": \\\"{{password}}\\\" }\"\n"
				+ "            }\n          },\n          \"response\": [\n            {\n"
				+ "              \"name\": \"Demo example\",\n              \"originalRequest\": {\n"
				+ "                \"method\": \"POST\",\n                \"url\": {\n"
				+ "                  \"raw\": \"https://dummy.com/users/123?limit=25\",\n"
				+ "                  \"protocol\": \"https\",\n                  \"host\": [\"dummy\", \"com\"],\n"
				+ "                  \"path\": [\"users\", \"123\"],\n                  \"query\": [\n"
				+ "                    { \"key\": \"limit\", \"value\": \"25\" }\n                  ]\n"
				+ "                },\n                \"header\": [\n"
				+ "                  { \"key\": \"X-Client\", \"value\": \"web-app\" }\n                ],\n"
				+ "                \"body\": {\n                  \"mode\": \"raw\",\n"
				+ "                  \"raw\": \"{ \\\"username\\\": \\\"emilys\\\", \\\"password\\\": \\\"emilyspass\\\" }\"\n"
				+ "                }\n              }\n            }\n          ]\n        }\n"
				+ "      ]\n    }\n  ]\n}\n";

		ApiSpec spec = ApiSpecParser.parse(postman);
		ApiOperation operation = spec.getFolders().get(0).getOperations().get(0);

		assertEquals("123", spec.getEnvs().get("me"));
		assertEquals("25", spec.getEnvs().get("limit"));
		assertEquals("web-app", spec.getEnvs().get("clientId"));
		assertEquals("emilys", spec.getEnvs().get("username"));
		assertEquals("emilyspass", spec.getEnvs().get("password"));
		assertNotNull(operation.getExample());
		assertEquals("Demo example", operation.getExample().getName());
	}

	@Test
	void manualApiExampleUpdatesEnvValuesFromBodyAndLeavesMissingValuesEmpty() {
		ApiOperation operation = new ApiOperation();
		operation.setPath("/users/{{me}}");
		operation.setBody(new ApiBody(ApiBodyType.JSON,
				"{ \"username\": \"{{username}}\", \"password\": \"{{password}}\", \"age\": \"{{age}}\" }"));

		ApiExample example = new ApiExample();
		example.setPath("/users/123");
		example.setBody(new ApiBody(ApiBodyType.JSON,
				"{ \"username\": \"emilys\", \"password\": \"emilyspass\", \"age\": \"{{age}}\" }"));
		operation.setExample(example);

		Map<String, Object> envs = new LinkedHashMap<>();
		ApiOperationEnvScanner.scan(operation, envs);

		assertEquals("123", envs.get("me"));
		assertEquals("emilys", envs.get("username"));
		assertEquals("emilyspass", envs.get("password"));
		assertEquals("", envs.get("age"));
	}

	@Test
	void rejectsEmptyDocumentWithHelpfulMessage() {
		ApiSpecParseException exception = assertThrows(ApiSpecParseException.class, () -> ApiSpecParser.parse("   "));

		assertTrue(exception.getUserMessage().contains("empty"));
		assertTrue(exception.getUserMessage().contains("OpenAPI/Swagger"));
	}

	@Test
	void rejectsUnknownDocumentWithHelpfulMessage() {
		ApiSpecParseException exception = assertThrows(ApiSpecParseException.class,
				() -> ApiSpecParser.parse("hello, this is not an api document"));

		assertTrue(exception.getUserMessage().contains("Unsupported API document format"));
		assertTrue(exception.getUserMessage().contains("Postman Collection"));
		assertTrue(exception.getUserMessage().contains("GraphQL"));
	}

	@Test
	void rejectsInvalidOpenApiDocumentWithHelpfulMessage() {
		String openApi = "openapi: 3.0.3\ninfo:\n  title: Broken API\n  version: 1.0.0\n"
				+ "paths: this-should-be-an-object\n";

		ApiSpecParseException exception = assertThrows(ApiSpecParseException.class, () -> ApiSpecParser.parse(openApi));

		assertTrue(exception.getUserMessage().contains("Invalid OpenAPI/Swagger document"));
		assertTrue(exception.getUserMessage().contains("paths"));
	}

	@Test
	void rejectsInvalidSwagger2DocumentWithHelpfulMessage() {
		String swagger = "swagger: '2.0'\ninfo:\n  title: Broken Swagger\n  version: 1.0.0\npaths: [broken\n";

		ApiSpecParseException exception = assertThrows(ApiSpecParseException.class, () -> ApiSpecParser.parse(swagger));

		assertTrue(exception.getUserMessage().contains("Invalid OpenAPI/Swagger document"));
		assertTrue(exception.getUserMessage().contains("Details:"));
	}

	@Test
	void rejectsInvalidPostmanCollectionWithHelpfulMessage() {
		String postman = "{\n  \"info\": { \"name\": \"Broken Collection\", "
				+ "\"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\" },\n"
				+ "  \"item\": [\n}\n";

		ApiSpecParseException exception = assertThrows(ApiSpecParseException.class, () -> ApiSpecParser.parse(postman));

		assertTrue(exception.getUserMessage().contains("Invalid Postman Collection document"));
		assertTrue(exception.getUserMessage().contains("JSON"));
	}

	@Test
	void rejectsInvalidGraphQlSchemaWithHelpfulMessage() {
		String schema = "type Query {\n  getUser(id: ID! User\n}\n";

		ApiSpecParseException exception = assertThrows(ApiSpecParseException.class, () -> ApiSpecParser.parse(schema));

		assertTrue(exception.getUserMessage().contains("Invalid GraphQL schema"));
		assertTrue(exception.getUserMessage().contains("Details:"));
	}

}
