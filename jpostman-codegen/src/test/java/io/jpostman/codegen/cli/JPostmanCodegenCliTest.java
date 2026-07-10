package io.jpostman.codegen.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JPostmanCodegenCliTest {

	@TempDir
	Path tempDir;

	@Test
	void propertiesCommandWritesDefaultRuntimeKeys() throws Exception {
		Path output = tempDir.resolve("jpostman.properties");

		int exit = JPostmanCodegenCli
				.run(new String[] { "properties", "--executor-class", "io.jpostman.httpclient.HttpClientExecutor",
						"--collection", "src/test/resources/DummyJSON.collection.json", "--environment",
						"src/test/resources/DummyJSON.environment.json", "--output", output.toString() });

		assertEquals(0, exit);
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertTrue(text.contains("executor=io.jpostman.httpclient.HttpClientExecutor"));
		assertTrue(text.contains("collection=classpath:DummyJSON.collection.json"));
		assertTrue(text.contains("environment=classpath:DummyJSON.environment.json"));
	}

	@Test
	void propertiesCommandWritesNamespaceRuntimeKeys() throws Exception {
		Path output = tempDir.resolve("jpostman.properties");

		int exit = JPostmanCodegenCli.run(new String[] { "properties", "--namespace", "auth", "--executor-class",
				"io.jpostman.restassured.RestAssuredExecutor", "--collection",
				"C:/demo/src/test/resources/collection.json", "--environment",
				"C:/demo/src/test/resources/environment.json", "--output", output.toString() });

		assertEquals(0, exit);
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertTrue(text.contains("executor.auth=io.jpostman.restassured.RestAssuredExecutor"));
		assertTrue(text.contains("collection.auth=classpath:collection.json"));
		assertTrue(text.contains("environment.auth=classpath:environment.json"));
	}
}
