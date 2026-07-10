package io.jpostman.schema.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiSchemaCliTest {

	@TempDir
	Path tempDir;

	@Test
	void postmanCommandExportsFromModelAndIgnoresUiOnlyOpenFields() throws Exception {
		Path model = tempDir.resolve("api-spec.json");
		Path collection = tempDir.resolve("collection.json");
		Path environment = tempDir.resolve("environment.json");

		Files.writeString(model, modelJsonWithOpenFlags(), StandardCharsets.UTF_8);

		ApiSchemaCli.execute(new String[] { "postman", "--model", model.toString(), "--collection-output",
				collection.toString(), "--environment-output", environment.toString(), "--pretty" });

		String collectionJson = Files.readString(collection, StandardCharsets.UTF_8);
		String environmentJson = Files.readString(environment, StandardCharsets.UTF_8);

		assertTrue(collectionJson.contains("\"name\" : \"Login user and get access/refresh tokens\""),
				"Postman collection should use ApiOperation.name as the item name.");
		assertTrue(collectionJson.contains("{{BASE_URL}}/auth/login"));
		assertTrue(environmentJson.contains("\"key\" : \"username\""));
		assertTrue(environmentJson.contains("\"value\" : \"emilys\""));
	}

	private static String modelJsonWithOpenFlags() {
		return "{\n  \"version\": \"1.0.0\",\n  \"name\": \"DummyJSON API\",\n"
				+ "  \"baseUrl\": \"https://dummyjson.com\",\n  \"overrideUrl\": false,\n  \"folders\": [ {\n"
				+ "    \"name\": \"Auth\",\n    \"open\": true,\n    \"folders\": [],\n"
				+ "    \"operations\": [ {\n      \"folder\": \"Auth\",\n"
				+ "      \"name\": \"Login user and get access/refresh tokens\",\n"
				+ "      \"methodName\": \"postAuthLogin\",\n"
				+ "      \"description\": \"Login response with user and tokens\",\n      \"method\": \"POST\",\n"
				+ "      \"allowedMethods\": [\"POST\"],\n      \"path\": \"/auth/login\",\n"
				+ "      \"queryParams\": [],\n      \"headers\": [],\n"
				+ "      \"body\": { \"type\": \"JSON\", \"content\": \"{\\n  \\\"username\\\" : \\\"{{username}}\\\"\\n}\" },\n"
				+ "      \"responses\": [],\n      \"protocol\": \"REST\",\n      \"urlResolved\": true,\n"
				+ "      \"open\": true\n    } ]\n  } ],\n  \"operations\": [],\n"
				+ "  \"envs\": { \"username\": \"emilys\", \"BASE_URL\": \"https://dummyjson.com\" },\n"
				+ "  \"uiState\": { \"activeTab\": \"preview\" }\n}\n";
	}
}
