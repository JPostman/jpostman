package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for visible spacing between completed debug executions.
 */
public class JPostmanDebugHeaderSpacingRegressionTest {

	@Test
	void methodHeaderKeepsLeadingBlankLineOnTheSameOutputStream() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream original = System.out;
		try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8.name())) {
			System.setOut(capture);
			JPostmanRuntimeOptions.printMethodHeader(new HeaderFixture(),
					new JPostmanInfo("testAuthRunner", "", "Auth", "Refresh auth session/token"));
		} finally {
			System.setOut(original);
		}

		String output = bytes.toString(StandardCharsets.UTF_8.name()).replace("\r\n", "\n");
		assertTrue(output.startsWith("\n\nDEBUG "), output);
		assertTrue(output.contains(":   === testAuthRunner ===\n"), output);
		assertEquals(1, occurrences(output, "=== testAuthRunner ==="), output);
	}

	@Test
	void requestOrResponseScopeOmitsDefaultNamespaceAndRootFolder() {
		JPostmanInfo info = new JPostmanInfo("response", "<default>", "<root>",
				"Login user and get access/refresh tokens");

		assertEquals("request=Login user and get access/refresh tokens", JPostmanRuntimeOptions.debugScope(info));
	}

	@Test
	void requestOrResponseScopeIncludesResolvedNamespaceFolderAndRequest() {
		JPostmanInfo info = new JPostmanInfo("testAuthRunner", "restage", "Auth",
				"Login user and get access/refresh tokens");

		assertEquals("namespace=restage, folder=Auth, request=Login user and get access/refresh tokens",
				JPostmanRuntimeOptions.debugScope(info));
	}

	private static int occurrences(String value, String token) {
		int count = 0;
		int offset = 0;
		while ((offset = value.indexOf(token, offset)) >= 0) {
			count++;
			offset += token.length();
		}
		return count;
	}

	private static final class HeaderFixture {
	}
}
