package io.jpostman.codegen.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void responseCodegenAddsMissingExecutorDependencyToPom() throws Exception {
		Path pom = tempDir.resolve("pom.xml");
		Files.writeString(pom, minimalPom(), StandardCharsets.UTF_8);

		int exit = JPostmanCodegenCli
				.run(new String[] { "response", "--method", "getCurrentAuthUser", "--request", "Get current auth user",
						"--executor-class", "io.jpostman.httpclient.HttpClientExecutor", "--pom", pom.toString() });

		assertEquals(0, exit);
		String text = Files.readString(pom, StandardCharsets.UTF_8);
		assertTrue(text.contains("<groupId>io.github.jpostman</groupId>"));
		assertTrue(text.contains("<artifactId>jpostman-httpclient</artifactId>"));
		assertTrue(text.contains("<version>${jpostman.version}</version>"));
		assertTrue(text.contains("<scope>test</scope>"));
	}

	@Test
	void responseCodegenDoesNotDuplicateExistingExecutorDependency() throws Exception {
		Path pom = tempDir.resolve("pom.xml");
		Files.writeString(pom, minimalPom(), StandardCharsets.UTF_8);

		String[] args = { "response", "--method", "getCurrentAuthUser", "--request", "Get current auth user",
				"--executor", "io.jpostman.restassured.RestAssuredExecutor", "--pom", pom.toString() };

		assertEquals(0, JPostmanCodegenCli.run(args));
		assertEquals(0, JPostmanCodegenCli.run(args));

		String text = Files.readString(pom, StandardCharsets.UTF_8);
		assertEquals(1, count(text, "<artifactId>jpostman-restassured</artifactId>"));
	}

	@Test
	void responseCodegenDoesNotRenderExecutorAnnotationAttribute() throws Exception {
		Path output = tempDir.resolve("method.java");

		int exit = JPostmanCodegenCli.run(new String[] { "response", "--method", "getCurrentAuthUser", "--request",
				"Get current auth user", "--executor-class", "io.jpostman.restassured.RestAssuredExecutor", "--output",
				output.toString() });

		assertEquals(0, exit);
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertTrue(text.contains("@JPostman.Response(request = \"Get current auth user\")"));
		assertFalse(text.contains("@Test"));
		assertFalse(text.contains("executor"));
		assertFalse(text.contains("RestAssuredExecutor"));
	}

	@Test
	void responseCodegenIncludesTestAnnotationWhenRequested() throws Exception {
		Path output = tempDir.resolve("response.java");

		int exit = JPostmanCodegenCli.run(new String[] { "response", "--method", "getCurrentAuthUser", "--request",
				"Get current auth user", "--include-test", "--output", output.toString() });

		assertEquals(0, exit);
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertTrue(text.startsWith("@Test" + System.lineSeparator()));
		assertTrue(text.contains("@JPostman.Response(request = \"Get current auth user\")"));
	}

	@Test
	void includeTestFalseDoesNotRenderTestAnnotation() throws Exception {
		Path output = tempDir.resolve("runner.java");

		int exit = JPostmanCodegenCli.run(new String[] { "runner", "--method", "runAll", "--include-test", "false",
				"--output", output.toString() });

		assertEquals(0, exit);
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertFalse(text.contains("@Test"));
	}

	@Test
	void requestCodegenDoesNotRenderExecutorAnnotationAttribute() throws Exception {
		Path output = tempDir.resolve("request.java");

		int exit = JPostmanCodegenCli
				.run(new String[] { "request", "--method", "loadCurrentUser", "--request", "Get current auth user",
						"--executor", "io.jpostman.httpclient.HttpClientExecutor", "--output", output.toString() });

		assertEquals(0, exit);
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertTrue(text.contains("@JPostman.Request(request = \"Get current auth user\")"));
		assertFalse(text.contains("@Test"));
		assertFalse(text.contains("executor"));
		assertFalse(text.contains("HttpClientExecutor"));
	}

	@Test
	void propertiesCommandSeparatesRuntimeBlocksWithBlankLine() throws Exception {
		Path output = tempDir.resolve("jpostman.properties");

		assertEquals(0, JPostmanCodegenCli.run(new String[] { "properties", "--executor-class",
				"io.jpostman.httpclient.HttpClientExecutor", "--collection", "src/test/resources/collection.json",
				"--environment", "src/test/resources/environment.json", "--output", output.toString() }));
		assertEquals(0, JPostmanCodegenCli.run(new String[] { "properties", "--namespace", "test", "--executor-class",
				"io.jpostman.httpclient.HttpClientExecutor", "--collection", "src/test/resources/collection.json",
				"--environment", "src/test/resources/environment.json", "--output", output.toString() }));

		String newline = System.lineSeparator();
		String text = Files.readString(output, StandardCharsets.UTF_8);
		assertTrue(text.contains("executor=io.jpostman.httpclient.HttpClientExecutor" + newline
				+ "collection=classpath:collection.json" + newline + "environment=classpath:environment.json" + newline
				+ newline + "executor.test=io.jpostman.httpclient.HttpClientExecutor" + newline
				+ "collection.test=classpath:collection.json" + newline
				+ "environment.test=classpath:environment.json"));
	}

	private static String minimalPom() {
		return "<project>\n\t<modelVersion>4.0.0</modelVersion>\n\t<groupId>com.example</groupId>\n"
				+ "\t<artifactId>demo</artifactId>\n\t<version>1.0.0-SNAPSHOT</version>\n\t<dependencies>\n"
				+ "\t\t<dependency>\n\t\t\t<groupId>io.github.jpostman</groupId>\n"
				+ "\t\t\t<artifactId>jpostman-core</artifactId>\n\t\t\t<version>${jpostman.version}</version>\n"
				+ "\t\t\t<scope>test</scope>\n\t\t</dependency>\n\t</dependencies>\n</project>\n";
	}

	private static int count(String text, String value) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(value, index)) >= 0) {
			count++;
			index += value.length();
		}
		return count;
	}
}
