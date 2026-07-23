package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Regression coverage for the internal JPOSTMAN_DEBUG file channel. */
public class JPostmanDebugFileRegressionTest {

	private static final String BEGIN_MARKER = "===== JPOSTMAN_INTERNAL_DEBUG =====";
	private static final String END_MARKER = "===== END_JPOSTMAN_INTERNAL_DEBUG =====";

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void capturesAllDiagnosticsWhenUserDebugIsNone() throws Exception {
		assumeTrue(System.getenv(JPostmanDebugFile.NAME) == null || System.getenv(JPostmanDebugFile.NAME).isBlank(),
				"The environment variable takes precedence over the test system property.");

		Path output = Files.createTempFile("jpostman-debug-", ".log");
		Files.deleteIfExists(output);
		String previous = System.getProperty(JPostmanDebugFile.NAME);
		try {
			System.setProperty(JPostmanDebugFile.NAME, output.toString());
			JPostmanDebugFile.reset();
			List defaultRequests = List.of("[POST  ] Login user and get tokens -> {{base_url}}/auth/login",
					"[GET   ] Get current auth user -> {{base_url}}/auth/me");
			List productRequests = List.of("[GET   ] Get all products -> {{base_url}}/products",
					"[POST  ] Add a new product -> https://dummyjson.com/products/add");
			JPostmanDebugFile.COLLECTIONS.put("<root>", defaultRequests);
			JPostmanInfo info = new JPostmanInfo("@JPostmanResponse", "getUser", "", "", "Get user");

			JPostmanDebugFile.execution(new DebugFixture(), info, "none",
					"--- REQUEST_UNRESOLVE ---\nUNRESOLVED REQUEST\n" + "--- REQUEST_RESOLVE ---\nRESOLVED REQUEST\n"
							+ "--- RESPONSE ---\nFULL RESPONSE");
			JPostmanDebugFile.COLLECTIONS.put("Product", productRequests);
			JPostmanDebugFile.info(new DebugFixture(), info, "none");

			String captured = Files.readString(output).replace("\r\n", "\n");
			assertTrue(captured.contains("event=EXECUTION"));
			assertEquals(1, occurrences(captured, BEGIN_MARKER));
			assertEquals(1, occurrences(captured, "annotationLog=none"));
			assertEquals(1, occurrences(captured, "--- COLLECTIONS ---"));
			assertEquals(1, occurrences(captured, "--- INFO ---"));
			assertTrue(captured.indexOf("annotationLog=none") > captured.lastIndexOf(END_MARKER));
			assertTrue(captured.indexOf("--- COLLECTIONS ---") > captured.lastIndexOf(END_MARKER));
			assertTrue(captured.indexOf("--- INFO ---") > captured.indexOf("method=getUser"));
			assertTrue(captured.indexOf("--- INFO ---") < captured.indexOf("--- REQUEST_UNRESOLVE ---"));
			assertTrue(captured.indexOf("--- INFO ---") < captured.indexOf(END_MARKER));
			assertTrue(captured.lastIndexOf("--- INFO ---") < captured.lastIndexOf(END_MARKER));
			assertTrue(captured.contains("folder=<root>\n[POST  ] Login user and get tokens"));
			assertTrue(captured.contains("folder=Product\n[GET   ] Get all products"));
			assertTrue(!captured.contains("{<default>="));
			assertTrue(!captured.contains("[[POST"));
			assertTrue(!captured.contains("process="));
			assertTrue(!captured.contains("thread="));
			assertTrue(captured.contains("--- REQUEST_UNRESOLVE ---"));
			assertTrue(captured.contains("--- REQUEST_RESOLVE ---"));
			assertTrue(captured.contains("--- RESPONSE ---"));
			assertTrue(!captured.contains("--- REQUEST_RESPONSE ---"));
		} finally {
			JPostmanDebugFile.reset();
			restoreProperty(previous);
			Files.deleteIfExists(output);
		}
	}

	@Test
	public void recordsCallAnnotationBeforeRuntimeExecution() throws Exception {
		assumeTrue(System.getenv(JPostmanDebugFile.NAME) == null || System.getenv(JPostmanDebugFile.NAME).isBlank(),
				"The environment variable takes precedence over the test system property.");

		Path output = Files.createTempFile("jpostman-debug-call-", ".log");
		Files.deleteIfExists(output);
		String previous = System.getProperty(JPostmanDebugFile.NAME);
		try {
			System.setProperty(JPostmanDebugFile.NAME, output.toString());
			JPostmanDebugFile.reset();
			JPostmanInfo info = new JPostmanInfo("@JPostmanCall", "runManually", "product", "Product",
					"Delete a product");

			JPostmanDebugFile.call(new DebugFixture(), info, "none");

			String captured = Files.readString(output).replace("\r\n", "\n");
			assertEquals(1, occurrences(captured, BEGIN_MARKER));
			assertTrue(captured.contains("event=EXECUTION"));
			assertTrue(captured.contains("method=runManually"));
			assertTrue(captured.contains("annotation=@JPostmanCall"));
			assertTrue(captured.contains("folder=Product"));
			assertTrue(captured.contains("request=Delete a product"));
		} finally {
			JPostmanDebugFile.reset();
			restoreProperty(previous);
			Files.deleteIfExists(output);
		}
	}

	@Test
	public void replacesCallPlaceholderWhenRuntimeExecutionCompletes() throws Exception {
		assumeTrue(System.getenv(JPostmanDebugFile.NAME) == null || System.getenv(JPostmanDebugFile.NAME).isBlank(),
				"The environment variable takes precedence over the test system property.");

		Path output = Files.createTempFile("jpostman-debug-call-complete-", ".log");
		Files.deleteIfExists(output);
		String previous = System.getProperty(JPostmanDebugFile.NAME);
		try {
			System.setProperty(JPostmanDebugFile.NAME, output.toString());
			JPostmanDebugFile.reset();
			JPostmanInfo info = new JPostmanInfo("@JPostmanCall", "runManually", "product", "Product",
					"Delete a product");
			DebugFixture fixture = new DebugFixture();

			JPostmanDebugFile.call(fixture, info, "none");
			JPostmanDebugFile.execution(fixture, info, "none", "--- REQUEST_UNRESOLVE ---\nUNRESOLVED REQUEST\n"
					+ "--- REQUEST_RESOLVE ---\nRESOLVED REQUEST\n" + "--- RESPONSE ---\nFULL RESPONSE");

			String captured = Files.readString(output).replace("\r\n", "\n");
			assertEquals(1, occurrences(captured, BEGIN_MARKER));
			assertEquals(1, occurrences(captured, "annotation=@JPostmanCall"));
			assertTrue(captured.contains("--- REQUEST_UNRESOLVE ---"));
			assertTrue(captured.contains("--- REQUEST_RESOLVE ---"));
			assertTrue(captured.contains("--- RESPONSE ---"));
		} finally {
			JPostmanDebugFile.reset();
			restoreProperty(previous);
			Files.deleteIfExists(output);
		}
	}

	@Test
	public void internalDiagnosticsUseResolvedRequestAndFullResponseLogs() {
		VerboseContext context = new VerboseContext();

		String captured = JPostmanDebugFile.diagnosticLog(context);

		assertEquals(List.of(false, true), context.request.calls);
		assertEquals(List.of(true), context.response.calls);
		assertTrue(captured.contains("--- REQUEST_UNRESOLVE ---"));
		assertTrue(captured.contains("UNRESOLVED REQUEST"));
		assertTrue(captured.contains("--- REQUEST_RESOLVE ---"));
		assertTrue(captured.contains("RESOLVED REQUEST"));
		assertTrue(captured.contains("--- RESPONSE ---"));
		assertTrue(captured.contains("FULL RESPONSE"));
	}

	@Test
	public void omitsResponseSectionWhenResponseIsNull() {
		String captured = JPostmanDebugFile.diagnosticLog(new RequestOnlyContext());

		assertTrue(captured.contains("--- REQUEST_UNRESOLVE ---"));
		assertTrue(captured.contains("--- REQUEST_RESOLVE ---"));
		assertTrue(!captured.contains("--- RESPONSE ---"));
	}

	@Test
	public void writesCompactFailureStackWithoutFrameworkPlumbing() throws Exception {
		assumeTrue(System.getenv(JPostmanDebugFile.NAME) == null || System.getenv(JPostmanDebugFile.NAME).isBlank(),
				"The environment variable takes precedence over the test system property.");

		Path output = Files.createTempFile("jpostman-debug-compact-error-", ".log");
		Files.deleteIfExists(output);
		String previous = System.getProperty(JPostmanDebugFile.NAME);
		try {
			System.setProperty(JPostmanDebugFile.NAME, output.toString());
			JPostmanDebugFile.reset();
			JPostmanInfo info = new JPostmanInfo("@JPostmanResponse", "newKeyboardProduct", "product", "Product",
					"Add a new product");
			AssertionError error = new AssertionError("Status code mismatch: expected [200] but found [201]");
			error.setStackTrace(new StackTraceElement[] {
					new StackTraceElement("io.jpostman.annotations.runtime.JPostmanAnnotationRunner",
							"assertionFailure", "JPostmanAnnotationRunner.java", 2045),
					new StackTraceElement(DebugFixture.class.getName(), "newKeyboardProduct", "DemoTest.java", 72),
					new StackTraceElement("org.testng.internal.invokers.TestInvoker", "invokeMethod",
							"TestInvoker.java", 689),
					new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 580) });

			JPostmanDebugFile.failure(new DebugFixture(), info, "error", "RESPONSE LOG", error);

			String captured = Files.readString(output).replace("\r\n", "\n");
			assertTrue(captured.contains("--- ERROR ---"));
			assertTrue(captured.contains("Status code mismatch: expected [200] but found [201]"));
			assertTrue(captured.contains(
					"at io.jpostman.annotations.runtime.JPostmanDebugFileRegressionTest$DebugFixture.newKeyboardProduct(DemoTest.java:"));
			assertTrue(captured.contains("stack frames omitted"));
			assertTrue(!captured.contains("JPostmanAnnotationRunner.assertionFailure"));
			assertTrue(!captured.contains("TestInvoker.invokeMethod"));
			assertTrue(!captured.contains("java.lang.reflect.Method.invoke"));
		} finally {
			JPostmanDebugFile.reset();
			restoreProperty(previous);
			Files.deleteIfExists(output);
		}
	}

	@Test
	public void recordsTheSameFailureOnlyOnce() throws Exception {
		assumeTrue(System.getenv(JPostmanDebugFile.NAME) == null || System.getenv(JPostmanDebugFile.NAME).isBlank(),
				"The environment variable takes precedence over the test system property.");

		Path output = Files.createTempFile("jpostman-debug-failure-", ".log");
		Files.deleteIfExists(output);
		String previous = System.getProperty(JPostmanDebugFile.NAME);
		try {
			System.setProperty(JPostmanDebugFile.NAME, output.toString());
			JPostmanDebugFile.reset();
			JPostmanInfo info = new JPostmanInfo("@JPostmanResponse", "getUser", "", "Users", "Get user");
			AssertionError error = new AssertionError("Expected status code 200 but received 500");

			JPostmanDebugFile.failure(new DebugFixture(), info, "none", "RESPONSE LOG", error);
			JPostmanDebugFile.failure(new DebugFixture(), info, "none", "RESPONSE LOG", error);

			String captured = Files.readString(output);
			assertEquals(1, occurrences(captured, "event=FAILURE"));
			assertTrue(captured.contains("Expected status code 200 but received 500"));
		} finally {
			JPostmanDebugFile.reset();
			restoreProperty(previous);
			Files.deleteIfExists(output);
		}
	}

	private static int occurrences(String value, String token) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

	private static void restoreProperty(String previous) {
		if (previous == null) {
			System.clearProperty(JPostmanDebugFile.NAME);
		} else {
			System.setProperty(JPostmanDebugFile.NAME, previous);
		}
	}

	public static final class VerboseContext {
		final VerboseRequest request = new VerboseRequest();
		final VerboseResponse response = new VerboseResponse();

		public VerboseRequest request() {
			return request;
		}

		public VerboseResponse response() {
			return response;
		}
	}

	public static final class RequestOnlyContext {
		final VerboseRequest request = new VerboseRequest();

		public VerboseRequest request() {
			return request;
		}

		public VerboseResponse response() {
			return null;
		}
	}

	public static final class VerboseRequest {
		final List<Boolean> calls = new ArrayList<>();

		public String log(boolean resolve) {
			calls.add(resolve);
			return resolve ? "RESOLVED REQUEST" : "UNRESOLVED REQUEST";
		}
	}

	public static final class VerboseResponse {
		final List<Boolean> calls = new ArrayList<>();

		public String log(boolean all) {
			calls.add(all);
			return all ? "FULL RESPONSE" : "BODY RESPONSE";
		}
	}

	private static final class DebugFixture {
		@SuppressWarnings("unused")
		public void newKeyboardProduct() {
		}
	}
}
