package io.jpostman.annotations.testng;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.JPostman;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanReportContext;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanRunner;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.annotations.runtime.JPostmanAnnotationRunner;
import io.jpostman.annotations.runtime.JPostmanInfo;
import io.jpostman.annotations.runtime.JPostmanReport;
import io.jpostman.secure.SecureContext;
import io.jpostman.testng.TestNgContext;

public class JPostmanAnnotationUnitTest {

	private static final boolean ENABLED = true;
	private static final String COLLECTION = "classpath:DummyJSON.all_product_collection.json";
	private static final String ENVIRONMENT = "classpath:DummyJSON.postman_environment.json";
	private static final String CONTEXT_CONFIG = "classpath:my_jpostman.properties";
	private static final String RULES_FILE = "classpath:jpostman-rules.ini";
	private static final String DATALOADER = "classpath:product-data.ini";
	private static final String DATALOADER_REDUNDANT = "classpath:jpostman-rules.ini";
	private static final String ASSERTION = "classpath:assertions.ini";
	private static final String ASSERTION_REDUNDANT = "classpath:jpostman-rules.ini";
	private static final String INVALID_CONTENT = "classpath:invalid-rules.ini";

	private static final Logger log = LoggerFactory.getLogger(JPostmanAnnotationUnitTest.class);

	private final Map<String, List<String>> messages = new LinkedHashMap<>();

	// JPostman collection is required
	private static final class CollectionRequiredFixture {
		@JPostmanContext(config = "")
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testCollectionRequired() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		IllegalStateException error = expectThrows(IllegalStateException.class,
				() -> runner.setup(new CollectionRequiredFixture()));
		assertEquals(debug(error.getMessage()), "JPostman collection is required for @JPostmanContext.\n"
				+ "Configure @JPostmanContext(collection = ...), or provide a valid config file with property collection.\n"
				+ "(@JPostmanContext: config=<default>, collection=<default>, environment=<default>)\n");
	}

	// JPostman collection is required for @JPostmanContext
	private static final class CollectionRequiredJPostmanContextFixture {
		@JPostmanContext
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testCollectionRequiredJPostmanContextFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		IllegalStateException error = expectThrows(IllegalStateException.class,
				() -> runner.setup(new CollectionRequiredJPostmanContextFixture()));
		assertEquals(debug(error.getMessage()), "JPostman collection is required for @JPostmanContext.\n"
				+ "Configure @JPostmanContext(collection = ...), or provide a valid config file with property collection.\n"
				+ "(@JPostmanContext: config=classpath:jpostman.properties, collection=<default>, environment=<default>)\n");
	}

	// Classpath resource not found: classpath:invalid.json
	private static final class ClasspathNotFoundFixture {
		@JPostmanContext(collection = "classpath:invalid.json")
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testClasspathNotFoundFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		IOException error = expectThrows(IOException.class, () -> runner.setup(new ClasspathNotFoundFixture()));
		assertEquals(debug(error.getMessage()), "Classpath resource not found: classpath:invalid.json\n"
				+ "(@JPostman: tags=, namespace=<default>, folder=<default>, request=<default>, executor=<default>)\n");
	}

	// File or classpath resource not found: CONFIG
	private static final class PathNotFoundFixture {
		@JPostmanContext(config = "CONFIG", environment = "ENVIRONMENT", collection = "classpath:COLLECTION")
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testPathNotFoundFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		IOException error = expectThrows(IOException.class, () -> runner.setup(new PathNotFoundFixture()));
		assertEquals(debug(error.getMessage()), "File or classpath resource not found: CONFIG\n"
				+ "(@JPostmanContext: config=CONFIG, collection=classpath:COLLECTION, environment=ENVIRONMENT)\n");
	}

	// @JPostmanRequest and @JPostmanExecutor methods must not be annotated with
	// @Test.
	private static final class JPostmanRequestTestFixture {
		@JPostmanContext
		private JPostman.Context jctx;

		@Test
		@JPostmanRequest
		public void requestExecutor(TestNgContext ctx, JPostmanInfo info) {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanRequestTestFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		IllegalStateException error = expectThrows(IllegalStateException.class,
				() -> runner.setup(new JPostmanRequestTestFixture()));
		assertEquals(debug(error.getMessage()), "JPostman collection is required for @JPostmanContext.\n"
				+ "Configure @JPostmanContext(collection = ...), or provide a valid config file with property collection.\n"
				+ "(@JPostmanContext: config=classpath:jpostman.properties, collection=<default>, environment=<default>)\n");
	}

	// @JPostmanRequest and @JPostmanExecutor methods must not be annotated with
	// @Test.
	private static final class JPostmanExecutorTestFixture {
		@JPostmanContext
		private JPostman.Context jctx;

		@Test
		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanExecutorTestFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		IllegalStateException error = expectThrows(IllegalStateException.class,
				() -> runner.setup(new JPostmanExecutorTestFixture()));
		assertEquals(debug(error.getMessage()), "JPostman collection is required for @JPostmanContext.\n"
				+ "Configure @JPostmanContext(collection = ...), or provide a valid config file with property collection.\n"
				+ "(@JPostmanContext: config=classpath:jpostman.properties, collection=<default>, environment=<default>)\n");
	}

	// No default @JPostmanExecutor was configured.
	private static final class MissingJPostmanExecutorFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@Test
		@JPostmanRunner
		public void productRunner() {
			jctx.getCollection().print();
		}
	}

	@Test(enabled = ENABLED)
	public void testMissingJPostmanExecutorFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		MissingJPostmanExecutorFixture fixture = new MissingJPostmanExecutorFixture();
		Method method = MissingJPostmanExecutorFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		SkipException error = expectThrows(SkipException.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "No default @JPostmanExecutor was configured.\n"
				+ "Add one default executor, for example @JPostmanExecutor, @JPostmanExecutor(id = \"default\"), or specify executor = \"#id\".\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=<default>, executor=<default>)\n");
	}

	// WARN JPostman runner found zero requests
	private static final class ZeroRequestsFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@Test
		@JPostmanRunner
		public void productRunner() {
			jctx.getCollection().print();
		}
	}

	@Test(enabled = ENABLED)
	public void testMissingZeroRequestsFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ZeroRequestsFixture fixture = new ZeroRequestsFixture();
		Method method = ZeroRequestsFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		SkipException error = expectThrows(SkipException.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "WARN JPostman runner found zero requests\n"
				+ "Check the namespace, folder, include/exclude values, or collection structure.\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=<default>, executor=<default>)\n");
	}

	// No JPostman data files configured.
	private static final class JPostmanDataMissingFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", data = "todo")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanDataMissingFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanDataMissingFixture fixture = new JPostmanDataMissingFixture();
		Method method = JPostmanDataMissingFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		IllegalStateException error = expectThrows(IllegalStateException.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "No JPostman data files configured.\n"
				+ "Add @JPostmanContext(dataload = {...}) or config properties key dataload.\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// JPostman data section not found: todo
	private static final class JPostmanDataNotFoundFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json", dataload = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", data = "todo")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanDataNotFoundFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanDataNotFoundFixture fixture = new JPostmanDataNotFoundFixture();
		Method method = JPostmanDataNotFoundFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		IllegalStateException error = expectThrows(IllegalStateException.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman data section not found: todo\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// JPostman executor returned null: authExecutor
	private static final class JPostmanExecutorNullFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return null;
		}

		@Test
		@JPostmanRunner
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanExecutorNullFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanExecutorNullFixture fixture = new JPostmanExecutorNullFixture();
		Method method = JPostmanExecutorNullFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman executor returned null: authExecutor\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// JPostman execution failed
	private static final class ExecutionFailedFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			throw new RuntimeException("Connection refused: connect");
		}

		@Test
		@JPostmanRunner
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testExecutionFailedFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ExecutionFailedFixture fixture = new ExecutionFailedFixture();
		Method method = ExecutionFailedFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\nConnection refused: connect\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// JPostman execution failed
	private static final class ExecutionFailed400Fixture {
		@JPostmanContext(verifyStatusCode = 200, collection = "classpath:DummyJSON.all_product_collection.json", environment = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			throw statusCodeMismatchError(false);
		}

		@Test
		@JPostmanRunner
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testExecutionFailed400Fixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ExecutionFailed400Fixture fixture = new ExecutionFailed400Fixture();
		Method method = ExecutionFailed400Fixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()),
				"(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Get current auth user, executor=<default>)\n"
						+ "Status code mismatch: expected [200] but found [401]\n",
				"Actual message: " + error.getMessage());
	}

	// JPostman execution failed
	private static final class ExecutionFailed400LogsFixture {
		@JPostmanContext(verifyStatusCode = 200, logs = { "request",
				"response" }, collection = "classpath:DummyJSON.all_product_collection.json", environment = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			throw statusCodeMismatchError(true);
		}

		@Test
		@JPostmanRunner
		public void productRunner() {
			throw new AssertionError("Call not allowed");
		}
	}

	@Test(enabled = ENABLED)
	public void ExecutionFailed400LogsFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ExecutionFailed400LogsFixture fixture = new ExecutionFailed400LogsFixture();
		Method method = ExecutionFailed400LogsFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertTrue(debug(error.getMessage()).contains(
				"(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Get current auth user, executor=<default>)\n"
						+ "Status code mismatch: expected [200] but found [401]\n"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("********** SecureRequest: **********"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("https://dummyjson.com/auth/me\n"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("**********SecureResponse: **********"),
				"Actual message: " + error.getMessage());

	}

	// Call not allowed
	private static final class ExecutionFailedSoftFixture {
		@JPostmanContext(logs = "error", collection = "classpath:DummyJSON.all_product_collection.json", environment = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(soft = true)
		public void productRunner() {
			throw new AssertionError("Call not allowed");
		}
	}

	@Test(enabled = ENABLED)
	public void testExecutionFailedSoftFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ExecutionFailedSoftFixture fixture = new ExecutionFailedSoftFixture();
		Method method = ExecutionFailedSoftFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		runner.run(fixture, method);
		AssertionError error = expectThrows(AssertionError.class, () -> invokeTestMethod(fixture, method));
		assertEquals(debug(error.getMessage()), "Call not allowed");
		assertFalse(error.getMessage().contains("********** SecureRequest: **********"),
				"Actual message: " + error.getMessage());
		assertFalse(error.getMessage().contains("**********SecureResponse: **********"),
				"Actual message: " + error.getMessage());
	}

	// The following asserts failed
	private static final class ExecutionFailedSoftVerifyFixture {
		@JPostmanContext(verifyStatusCode = 200, collection = "classpath:DummyJSON.all_product_collection.json", environment = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@JPostmanTestContext
		private TestNgContext api;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 401);
		}

		@Test
		@JPostmanRunner(soft = true)
		public void productRunner() {
			throw softStatusCodeMismatchError(false);
		}
	}

	@Test(enabled = ENABLED)
	public void testExecutionFailedSoftVerifyFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ExecutionFailedSoftVerifyFixture fixture = new ExecutionFailedSoftVerifyFixture();
		Method method = ExecutionFailedSoftVerifyFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		runner.run(fixture, method);
		AssertionError error = expectThrows(AssertionError.class, () -> invokeTestMethod(fixture, method));
		assertEquals(debug(error.getMessage()), "The following asserts failed:\n"
				+ "	(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Get current auth user, executor=<default>)\n"
				+ "Status code mismatch: expected [200] but found [401]\n");
		assertFalse(error.getMessage().contains("********** SecureRequest: **********"),
				"Actual message: " + error.getMessage());
		assertFalse(error.getMessage().contains("**********SecureResponse: **********"),
				"Actual message: " + error.getMessage());
	}

	// The following asserts failed
	private static final class ExecutionFailedSoftVerifyTrueFixture {
		@JPostmanContext(verifyStatusCode = 200, collection = "classpath:DummyJSON.all_product_collection.json", environment = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@JPostmanTestContext
		private TestNgContext api;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 401);
		}

		@Test
		@JPostmanRunner(soft = true)
		public void productRunner() {
			throw softStatusCodeMismatchError(false, true);
		}
	}

	@Test(enabled = ENABLED)
	public void testExecutionFailedSoftVerifyTrueFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		ExecutionFailedSoftVerifyTrueFixture fixture = new ExecutionFailedSoftVerifyTrueFixture();
		Method method = ExecutionFailedSoftVerifyTrueFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		runner.run(fixture, method);
		AssertionError error = expectThrows(AssertionError.class, () -> invokeTestMethod(fixture, method));
		assertTrue(debug(error.getMessage()).contains("The following asserts failed:\n"
				+ "	(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Get current auth user, executor=<default>)\n"
				+ "Status code mismatch: expected [200] but found [401]\n"), "Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("********** SecureRequest: **********"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("**********SecureResponse: **********"),
				"Actual message: " + error.getMessage());
	}

	// The following asserts failed
	private static final class JPostmanReportSummaryFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json", environment = "classpath:DummyJSON.postman_environment.json")
		private JPostman.Context jctx;

		@JPostmanTestContext
		private TestNgContext api;

		@JPostmanReportContext
		private JPostmanReport report;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(soft = true, include = "Login user and get tokens")
		public void productRunner() {
			api.verify();
			report.summary();
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanReportSummaryFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanReportSummaryFixture fixture = new JPostmanReportSummaryFixture();
		Method method = JPostmanReportSummaryFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		runner.run(fixture, method);
		String output = captureOutput(() -> invokeTestMethod(fixture, method));
		assertTrue(
				debug(output).contains("[36m===============================================\nJPostman report\n"
						+ "Total tests run: 1, Passes: 1, Failures: 0, Skips: 0, Duration: "),
				"Actual output: " + output);
	}

	// JPostman execution failed
	private static final class JPostmanExecutionFailedFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			throw new RuntimeException("Connection refused: connect");
		}

		@JPostmanRequest
		public String requestExecutor(TestNgContext ctx, JPostmanInfo info) {
			return null;
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanExecutionFailedFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanExecutionFailedFixture fixture = new JPostmanExecutionFailedFixture();
		Method method = JPostmanExecutionFailedFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\nConnection refused: connect\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// JPostman execution failed
	private static final class JPostmanExecutionFailedSoftFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			throw new RuntimeException("Connection refused: connect");
		}

		@JPostmanRequest
		public String requestExecutor(TestNgContext ctx, JPostmanInfo info) {
			return null;
		}

		@Test
		@JPostmanRunner(soft = true, include = "Login user and get tokens")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanExecutionFailedSoftFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanExecutionFailedSoftFixture fixture = new JPostmanExecutionFailedSoftFixture();
		Method method = JPostmanExecutionFailedSoftFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman runner failed for 1 request.\n\n"
				+ "JPostman execution failed\nConnection refused: connect\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// JPostman execution failed
	private static final class JPostmanRequestReturnNullFixture {
		@JPostmanContext(collection = "classpath:DummyJSON.all_product_collection.json")
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@JPostmanRequest(cache = "token")
		public String requestExecutor(TestNgContext ctx, JPostmanInfo info) {
			return null;
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", dependsOn = "requestExecutor")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanRequestReturnNullFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanRequestReturnNullFixture fixture = new JPostmanRequestReturnNullFixture();
		Method method = JPostmanRequestReturnNullFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()),
				"Dependency method returned null and cannot be cached: requestExecutor\n"
						+ "Use void for setup-only dependencies, or return a non-null value when another request needs the cached value.\n"
						+ "(@JPostmanRequest: tags=, namespace=<default>, folder=<default>, request=<default>, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// Redundant collection/environment/rules mappings should be reported.
	private static final class RedundantContextMappingsFixture {
		@JPostmanContext(config = CONTEXT_CONFIG, collection = COLLECTION, environment = ENVIRONMENT, rules = RULES_FILE)
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testRedundantContextMappingsFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		RedundantContextMappingsFixture fixture = new RedundantContextMappingsFixture();

		String output = captureError(() -> {
			try {
				runner.setup(fixture);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		assertEquals(debug(output), "Redundant JPostman collection mapping ignored.\n"
				+ "The same field is configured in @JPostmanContext and config properties file.\n"
				+ "Using @JPostmanContext value: collection=classpath:DummyJSON.all_product_collection.json\n"
				+ "Ignored config mapping: classpath:my_jpostman.properties -> collection=classpath:DummyJSON.all_product_collection.json\n"
				+ "(@JPostmanContext: config=classpath:my_jpostman.properties, collection=classpath:DummyJSON.all_product_collection.json, environment=classpath:DummyJSON.postman_environment.json)\n"
				+ System.lineSeparator() + "Redundant JPostman environment mapping ignored.\n"
				+ "The same field is configured in @JPostmanContext and config properties file.\n"
				+ "Using @JPostmanContext value: environment=classpath:DummyJSON.postman_environment.json\n"
				+ "Ignored config mapping: classpath:my_jpostman.properties -> environment=classpath:DummyJSON.postman_environment.json\n"
				+ "(@JPostmanContext: config=classpath:my_jpostman.properties, collection=classpath:DummyJSON.all_product_collection.json, environment=classpath:DummyJSON.postman_environment.json)\n"
				+ System.lineSeparator() + "Redundant JPostman rules mapping ignored.\n"
				+ "The same field is configured in @JPostmanContext and config properties file.\n"
				+ "Using @JPostmanContext value: rules=classpath:jpostman-rules.ini\n"
				+ "Ignored config mapping: classpath:my_jpostman.properties -> rules=classpath:jpostman-rules.ini\n"
				+ "(@JPostmanContext: config=classpath:my_jpostman.properties, collection=classpath:DummyJSON.all_product_collection.json, environment=classpath:DummyJSON.postman_environment.json)\n"
				+ System.lineSeparator(), "Actual output: " + output);
	}

	// Redundant dataload mappings should be ignored and reported.
	// Clean naming rule: dataload belongs to JPostmanContext and loads INI files.
	// data belongs to JPostmanRunner/JPostmanRequest/JPostmanResponse and selects
	// an INI section.
	private static final class RedundantDataMappingsFixture {
		@JPostmanContext(config = CONTEXT_CONFIG, collection = COLLECTION, dataload = DATALOADER)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return null;
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", data = "default")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testRedundantDataMappingsFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		RedundantDataMappingsFixture fixture = new RedundantDataMappingsFixture();
		Method method = RedundantDataMappingsFixture.class.getDeclaredMethod("productRunner");

		String output = captureError(() -> {
			try {
				runner.setup(fixture);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		assertEquals(debug(output), "Redundant JPostman dataload mapping ignored.\n"
				+ "The same data file is configured more than once.\n"
				+ "Using @JPostmanContext value: dataload=classpath:product-data.ini\n"
				+ "Ignored config mapping: classpath:my_jpostman.properties -> dataload=classpath:product-data.ini\n"
				+ "(@JPostmanContext: config=classpath:my_jpostman.properties, collection=classpath:DummyJSON.all_product_collection.json, environment=<default>)\n"
				+ System.lineSeparator(), output);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman executor returned null: authExecutor\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// Duplicate sections in different files should still fail.
	private static final class DuplicateDataSectionFixture {
		@JPostmanContext(collection = COLLECTION, dataload = { DATALOADER, DATALOADER_REDUNDANT })
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", data = "default")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testDuplicateDataSectionFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		DuplicateDataSectionFixture fixture = new DuplicateDataSectionFixture();
		Method method = DuplicateDataSectionFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);

		IllegalStateException error = expectThrows(IllegalStateException.class, () -> runner.run(fixture, method));

		assertEquals(debug(error.getMessage()), "Duplicate JPostman data section: default\n"
				+ "The same section was found in more than one loaded data file.\nFound in:\n"
				+ "- classpath:product-data.ini\n- classpath:jpostman-rules.ini\n"
				+ "Keep each section name unique across loaded data files.\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// No JPostman assertion files configured.
	// Clean naming rule: assertions belongs to JPostmanContext and loads assertion
	// INI files.
	// asserts belongs to JPostmanRunner/JPostmanRequest/JPostmanResponse and
	// selects assertion sections.
	private static final class JPostmanAssertionsMissingFixture {
		@JPostmanContext(config = "", collection = COLLECTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", asserts = "default")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanAssertionsMissingFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanAssertionsMissingFixture fixture = new JPostmanAssertionsMissingFixture();
		Method method = JPostmanAssertionsMissingFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\n"
				+ "No JPostman assertion files configured.\n"
				+ "Add @JPostmanContext(assertions = {...}) or config properties key assertions.\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// JPostman assertion section not found: missing
	private static final class JPostmanAssertionSectionNotFoundFixture {
		@JPostmanContext(config = "", collection = COLLECTION, assertions = ASSERTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", asserts = "missing")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanAssertionSectionNotFoundFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanAssertionSectionNotFoundFixture fixture = new JPostmanAssertionSectionNotFoundFixture();
		Method method = JPostmanAssertionSectionNotFoundFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\n"
				+ "JPostman assertion section not found: missing\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// Unsupported JPostman assertion rule: unsupported
	private static final class UnsupportedAssertionRuleFixture {
		@JPostmanContext(config = "", collection = COLLECTION, assertions = ASSERTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", asserts = "default")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testUnsupportedAssertionRuleFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		UnsupportedAssertionRuleFixture fixture = new UnsupportedAssertionRuleFixture();
		Method method = UnsupportedAssertionRuleFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\n"
				+ "Unsupported JPostman assertion rule: badRule\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// Invalid assertion rule line.
	private static final class InvalidAssertionRuleLineFixture {
		@JPostmanContext(config = "", collection = COLLECTION, assertions = INVALID_CONTENT)
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testInvalidAssertionRuleLineFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());

		IllegalStateException error = expectThrows(IllegalStateException.class,
				() -> runner.setup(new InvalidAssertionRuleLineFixture()));
		assertEquals(debug(error.getMessage()), "Invalid assertion rule line: this line has no equals sign");
	}

	// Duplicate assertion sections in different files should fail during setup.
	private static final class DuplicateAssertionSectionFixture {
		@JPostmanContext(config = "", collection = COLLECTION, assertions = { ASSERTION, ASSERTION_REDUNDANT })
		private JPostman.Context jctx;
	}

	@Test(enabled = ENABLED)
	public void testDuplicateAssertionSectionFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());

		IllegalStateException error = expectThrows(IllegalStateException.class,
				() -> runner.setup(new DuplicateAssertionSectionFixture()));
		assertEquals(debug(error.getMessage()), "Duplicate JPostman assertion section: default\n"
				+ "The same assertion section was found in more than one loaded assertion file.\nFound in:\n"
				+ "- classpath:assertions.ini\n- classpath:jpostman-rules.ini\n"
				+ "Keep each assertion section name unique across loaded files.\n"
				+ "(@JPostmanAssertionRunner: tags=, namespace=<default>, folder=<default>, request=<default>, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// verify and asserts cannot be used together.
	private static final class VerifyAndAssertsFixture {
		@JPostmanContext(config = "", collection = COLLECTION, assertions = ASSERTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", verify = 200, asserts = "default")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testVerifyAndAssertsFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		VerifyAndAssertsFixture fixture = new VerifyAndAssertsFixture();
		Method method = VerifyAndAssertsFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "Invalid JPostman verification configuration.\n"
				+ "@JPostmanRunner cannot use verify and asserts together.\n"
				+ "Use verify for status-code verification, or use asserts for assertion sections.\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// @JPostmanResponse(skip = true) should skip the response test.
	private static final class JPostmanResponseSkipFixture {
		@JPostmanContext(collection = COLLECTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanResponse(request = "Login user and get tokens", skip = true)
		public void login() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanResponseSkipFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanResponseSkipFixture fixture = new JPostmanResponseSkipFixture();
		Method method = JPostmanResponseSkipFixture.class.getDeclaredMethod("login");
		runner.setup(fixture);

		SkipException error = expectThrows(SkipException.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman response skipped.\n"
				+ "(@JPostmanResponse: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// @JPostmanContext(skipAll = true) should skip response tests by default.
	private static final class JPostmanContextSkipAllFixture {
		@JPostmanContext(collection = COLLECTION, skipAll = true)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanResponse(request = "Login user and get tokens")
		public void login() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanContextSkipAllFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanContextSkipAllFixture fixture = new JPostmanContextSkipAllFixture();
		Method method = JPostmanContextSkipAllFixture.class.getDeclaredMethod("login");
		runner.setup(fixture);

		SkipException error = expectThrows(SkipException.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman response skipped.\n"
				+ "@JPostmanContext(skipAll = true) is enabled.\n"
				+ "(@JPostmanResponse: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// @JPostmanResponse(enabled = true) should override @JPostmanContext(skipAll =
	// true).
	private static final class JPostmanContextSkipAllEnabledFixture {
		@JPostmanContext(collection = COLLECTION, skipAll = true)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanResponse(request = "Login user and get tokens", enabled = true)
		public void login() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanContextSkipAllEnabledFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanContextSkipAllEnabledFixture fixture = new JPostmanContextSkipAllEnabledFixture();
		Method method = JPostmanContextSkipAllEnabledFixture.class.getDeclaredMethod("login");
		runner.setup(fixture);
		runner.run(fixture, method);
	}

	// enabled and skip cannot be used together on @JPostmanResponse.
	private static final class JPostmanResponseEnabledAndSkipFixture {
		@JPostmanContext(collection = COLLECTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@Test
		@JPostmanResponse(request = "Login user and get tokens", enabled = true, skip = true)
		public void login() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanResponseEnabledAndSkipFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanResponseEnabledAndSkipFixture fixture = new JPostmanResponseEnabledAndSkipFixture();
		Method method = JPostmanResponseEnabledAndSkipFixture.class.getDeclaredMethod("login");
		runner.setup(fixture);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "Invalid JPostman skip configuration.\n"
				+ "enabled and skip cannot be defined on the same @JPostmanResponse annotation.\n"
				+ "Use enabled = true to override @JPostmanContext(skipAll = true),\n"
				+ "or use skip = true to disable this response.\n"
				+ "(@JPostmanResponse: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n");
	}

	// @JPostmanRequest(skip = true) should be allowed when used as a dependency.
	private static final class JPostmanRequestDependencySkipFixture {
		@JPostmanContext(collection = COLLECTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@JPostmanRequest(skip = true)
		public void skippedSetup() {
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", dependsOn = "skippedSetup")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanRequestDependencySkipFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanRequestDependencySkipFixture fixture = new JPostmanRequestDependencySkipFixture();
		Method method = JPostmanRequestDependencySkipFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		runner.run(fixture, method);
	}

	// @JPostmanRequest(skipReason = ...) should also imply skip when used as a
	// dependency.
	private static final class JPostmanRequestDependencySkipReasonFixture {
		@JPostmanContext(collection = COLLECTION)
		private JPostman.Context jctx;

		@JPostmanExecutor
		public ApiExecutor authExecutor(TestNgContext ctx, JPostmanInfo info) {
			return okExecutor("{}", 200);
		}

		@JPostmanRequest
		public void skippedSetup() {
		}

		@Test
		@JPostmanRunner(include = "Login user and get tokens", dependsOn = "skippedSetup")
		public void productRunner() {
		}
	}

	@Test(enabled = ENABLED)
	public void testJPostmanRequestDependencySkipReasonFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		JPostmanRequestDependencySkipReasonFixture fixture = new JPostmanRequestDependencySkipReasonFixture();
		Method method = JPostmanRequestDependencySkipReasonFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);
		runner.run(fixture, method);
	}

	// Invalid context executor class should fail with a clear message.
	private static final class InvalidDefaultExecutorClassFixture {
		@JPostmanContext(collection = COLLECTION, executor = InvalidDefaultExecutor.class)
		private JPostman.Context jctx;

		@Test
		@JPostmanRunner(include = "Login user and get tokens")
		public void productRunner() {
		}
	}

	private static final class InvalidDefaultExecutor {
	}

	@Test(enabled = ENABLED)
	public void testInvalidDefaultExecutorClassFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		InvalidDefaultExecutorClassFixture fixture = new InvalidDefaultExecutorClassFixture();
		Method method = InvalidDefaultExecutorClassFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\n"
				+ "Executor class does not provide static apply(request): io.jpostman.annotations.testng.JPostmanAnnotationUnitTest$InvalidDefaultExecutor\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	// Invalid session executor class should fail when create() is missing.
	private static final class InvalidSessionExecutorClassFixture {
		@JPostmanContext(collection = COLLECTION, executor = InvalidSessionExecutor.class, session = true)
		private JPostman.Context jctx;

		@Test
		@JPostmanRunner(include = "Login user and get tokens")
		public void productRunner() {
		}
	}

	private static final class InvalidSessionExecutor {
	}

	@Test(enabled = ENABLED)
	public void testInvalidSessionExecutorClassFixture() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		InvalidSessionExecutorClassFixture fixture = new InvalidSessionExecutorClassFixture();
		Method method = InvalidSessionExecutorClassFixture.class.getDeclaredMethod("productRunner");
		runner.setup(fixture);

		AssertionError error = expectThrows(AssertionError.class, () -> runner.run(fixture, method));
		assertEquals(debug(error.getMessage()), "JPostman execution failed\n"
				+ "Unable to create JPostman default executor from: io.jpostman.annotations.testng.JPostmanAnnotationUnitTest$InvalidSessionExecutor\n"
				+ "(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Login user and get tokens, executor=<default>)\n",
				"Actual message: " + error.getMessage());
	}

	/**********************************
	 * HELPER FUNCTIONS
	 **********************************/
	@AfterClass
	private void tearDown() {
		StringBuffer sb = new StringBuffer();
		List<String> keys = new ArrayList<>(messages.keySet());
		Collections.sort(keys);
		for (String message : keys) {
			sb.append("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n");
			for (String method : messages.get(message)) {
				sb.append("========= " + method + " =========\n");
			}
			sb.append(message);
			sb.append("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n");
		}
		if (sb.length() > 0)
			log.debug(sb.toString());
	}

	private String debug(String message) {
		List<String> cached = messages.get(message);
		if (cached == null) {
			cached = new ArrayList<>();
			messages.put("'" + message + "'", cached);
		}
		cached.add(SecureContext.callerMethodName(2));
		return message;
	}

	private static ApiExecutor okExecutor(String json, int statusCode) {
		return () -> new ApiResponse(statusCode, json, json.getBytes(StandardCharsets.UTF_8), Map.of());
	}

	private static AssertionError statusCodeMismatchError(boolean addSupressed) {
		String supressed = "********** SecureRequest: **********\nhttps://dummyjson.com/auth/me\n"
				+ "**********SecureResponse: **********";

		AssertionError error = new AssertionError(
				"(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Get current auth user, executor=<default>)\n"
						+ "Status code mismatch: expected [200] but found [401]\n");

		if (addSupressed) {
			error.addSuppressed(new AssertionError(supressed));
		}

		return error;
	}

	private static AssertionError softStatusCodeMismatchError(boolean addSupressed) {
		return softStatusCodeMismatchError(addSupressed, false);
	}

	private static AssertionError softStatusCodeMismatchError(boolean addSupressed, boolean addSupressedToMessage) {
		String supressed = "********** SecureRequest: **********\nhttps://dummyjson.com/auth/me\n"
				+ "**********SecureResponse: **********";

		AssertionError error = new AssertionError("The following asserts failed:\n"
				+ "\t(@JPostmanRunner: tags=, namespace=<default>, folder=<default>, request=Get current auth user, executor=<default>)\n"
				+ "Status code mismatch: expected [200] but found [401]\n" + (addSupressedToMessage ? supressed : ""));

		if (addSupressed) {
			error.addSuppressed(new AssertionError(supressed));
		}

		return error;
	}

	private static void invokeTestMethod(Object fixture, Method method) {
		try {
			method.setAccessible(true);
			method.invoke(fixture);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			if (cause instanceof AssertionError) {
				throw (AssertionError) cause;
			}

			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}

			throw new RuntimeException(cause);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static String captureError(Runnable action) {
		PrintStream originalErr = System.err;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
			action.run();
			return output.toString(StandardCharsets.UTF_8);
		} finally {
			System.setErr(originalErr);
		}
	}

	private static String captureOutput(Runnable action) {
		PrintStream originalOut = System.out;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
			action.run();
			return output.toString(StandardCharsets.UTF_8);
		} finally {
			System.setOut(originalOut);
		}
	}
}
