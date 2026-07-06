package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.testng.IHookCallBack;
import org.testng.IInvokedMethod;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.internal.ConstructorOrMethod;

import com.google.gson.JsonParser;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Collection;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanRunner;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotationListener;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotations;
import io.jpostman.annotations.testng.TestNgPostmanFramework;
import io.jpostman.junit.JUnitContext;
import io.jpostman.testng.TestNgContext;

public class JPostmanAnnotationCoverageTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";

	/**
	 * Verifies that JUnitContext and TestNgContext can check plain and protected
	 * secure value keys by key presence.
	 *
	 * <p>
	 * This covers the hasKey helper used when callers need to distinguish between a
	 * missing key and a key whose resolved value may be empty or null.
	 * </p>
	 */
	@Test
	public void contextsCanCheckPlainAndSecretKeyPresence() {
		JUnitContext junit = JUnitContext.create().plain("plainKey", "plainValue").secret("secretKey", "secretValue");

		assertTrue(junit.hasKey("plainKey"));
		assertTrue(junit.hasKey("secretKey"));
		assertFalse(junit.hasKey("missingKey"));
		assertFalse(junit.hasKey(null));

		TestNgContext testng = TestNgContext.create().plain("plainKey", "plainValue").secret("secretKey",
				"secretValue");

		assertTrue(testng.hasKey("plainKey"));
		assertTrue(testng.hasKey("secretKey"));
		assertFalse(testng.hasKey("missingKey"));
		assertFalse(testng.hasKey(null));
	}

	/**
	 * Verifies that PreparedContext stores the same context object and collection
	 * reference passed to its constructor.
	 */
	@Test
	public void preparedContextStoresContextAndCollection() {
		JUnitContext context = JUnitContext.create();

		PreparedContext<JUnitContext> prepared = new PreparedContext<>(context, null);

		assertSame(context, prepared.context);
		assertNull(prepared.collection);
	}

	/**
	 * Verifies namespace storage and lookup in PreparedContexts.
	 *
	 * <p>
	 * Covers put, resolve, context, collection, and isEmpty.
	 * </p>
	 */
	@Test
	public void preparedContextsStoresAndResolvesByNamespace() {
		JUnitContext context = JUnitContext.create();
		PreparedContext<JUnitContext> prepared = new PreparedContext<>(context, null);

		PreparedContexts<JUnitContext> contexts = new PreparedContexts<>();

		contexts.put("product", prepared);

		assertEquals(contexts.isEmpty(), false);
		assertSame(prepared, contexts.resolve("product"));
		assertSame(context, contexts.context("product"));
		assertNull(contexts.collection("product"));
	}

	/**
	 * Verifies default namespace normalization.
	 *
	 * <p>
	 * A null namespace and an empty namespace should both resolve to the default
	 * context.
	 * </p>
	 */
	@Test
	public void preparedContextsUsesEmptyNamespaceForNull() {
		JUnitContext context = JUnitContext.create();
		PreparedContext<JUnitContext> prepared = new PreparedContext<>(context, null);

		PreparedContexts<JUnitContext> contexts = new PreparedContexts<>();

		contexts.put(null, prepared);

		assertSame(prepared, contexts.resolve(null));
		assertSame(prepared, contexts.resolve(""));
		assertTrue(contexts.contains(""));
	}

	/**
	 * Verifies duplicate namespace validation.
	 *
	 * <p>
	 * The annotation runtime should fail fast if two @JPostmanTestContext fields
	 * use the same namespace.
	 * </p>
	 */
	@Test
	public void preparedContextsFailsOnDuplicateNamespace() {
		PreparedContexts<JUnitContext> contexts = new PreparedContexts<>();

		contexts.put("product", new PreparedContext<>(JUnitContext.create(), null));

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> contexts.put("product", new PreparedContext<>(JUnitContext.create(), null)));

		assertEquals("Duplicate @JPostmanTestContext namespace: product", error.getMessage());
	}

	/**
	 * Verifies missing namespace validation.
	 *
	 * <p>
	 * The annotation runtime should fail clearly if a request or response points to
	 * a namespace that was not prepared.
	 * </p>
	 */
	@Test
	public void preparedContextsFailsWhenNamespaceNotFound() {
		PreparedContexts<JUnitContext> contexts = new PreparedContexts<>();

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> contexts.resolve("missing"));

		assertEquals("No JPostman runtime context found for namespace: missing", error.getMessage());
	}

	/**
	 * Verifies that TestNgPostmanFramework delegates all runtime operations to a
	 * real TestNgContext.
	 *
	 * <p>
	 * Covers: name, contextType, create, setCurrent, clearCurrent, cache getter,
	 * cache setter, secret, load, loadRules, request, response, and verify.
	 * </p>
	 */
	@Test
	public void testNgFrameworkCallsAllWrapperMethodsWithoutMock() throws Exception {
		TestNgPostmanFramework framework = new TestNgPostmanFramework();

		assertEquals("TestNG", framework.name());
		assertTrue(framework.skipException(null, "skip") instanceof SkipException);
		assertEquals(TestNgContext.class, framework.contextType());

		TestNgContext context = framework.create();
		assertNotNull(context);

		framework.setCurrent(context);
		assertEquals(context, TestNgContext.current());

		framework.cache(context, "accessToken", "abc123");
		assertEquals("abc123", framework.cache(context, "accessToken"));

		Environment environment = new Environment("test");
		framework.secret(context, environment);

		try (InputStream rules = new ByteArrayInputStream("[user]\nfilter=id\n".getBytes())) {
			framework.load(context, rules);
		}

		framework.loadRules(context, "user");

		Request request = Collection
				.load(JsonParser.parseString("{\"item\": [{\"name\":\"Unnamed\",\"request\":{}}]}").getAsJsonObject())
				.getRequest("Unnamed");

		framework.request(context, request);
		assertNotNull(context.request());

		ApiExecutor executor = okExecutor("{\"id\":1,\"firstName\":\"John\"}");

		context = framework.filter(context, "id", "firstName");
		context = framework.response(context, executor);

		assertNotNull(context.response());
		framework.verify(context, 200, false, false);

		framework.clearCurrent();

		AssertionError error = assertThrows(AssertionError.class, () -> TestNgContext.current());
		assertTrue(error.getMessage().contains("No current TestNgContext"));
	}

	/**
	 * Verifies that JUnitPostmanFramework delegates all runtime operations to a
	 * real JUnitContext.
	 *
	 * <p>
	 * Covers the same adapter responsibilities as the TestNG framework test, but
	 * for the JUnit implementation.
	 * </p>
	 */
	@Test
	public void junitFrameworkCallsAllWrapperMethodsWithoutMock() throws Exception {
		JUnitPostmanFramework framework = new JUnitPostmanFramework();

		assertEquals("JUnit", framework.name());
		assertTrue(framework.skipException(null, "skip") instanceof TestAbortedException);
		assertEquals(JUnitContext.class, framework.contextType());

		JUnitContext context = framework.create();
		assertNotNull(context);

		framework.setCurrent(context);
		assertEquals(context, JUnitContext.current());

		framework.cache(context, "accessToken", "abc123");
		assertEquals("abc123", framework.cache(context, "accessToken"));

		Environment environment = new Environment("test");
		framework.secret(context, environment);
		framework.filter(context, "id");

		try (InputStream rules = new ByteArrayInputStream("[user]\nfilter=id\n".getBytes())) {
			framework.load(context, rules);
		}

		framework.loadRules(context, "user");

		Request request = Collection
				.load(JsonParser.parseString("{\"item\": [{\"name\":\"Unnamed\",\"request\":{}}]}").getAsJsonObject())
				.getRequest("Unnamed");

		framework.request(context, request);
		assertNotNull(context.request());

		ApiExecutor executor = okExecutor("{\"id\":1,\"firstName\":\"John\"}");

		framework.response(context, executor);
		assertNotNull(context.response());

		framework.verify(context, 200, true, true);

		framework.clearCurrent();

		AssertionError error = assertThrows(AssertionError.class, () -> JUnitContext.current());
		assertTrue(error.getMessage().contains("No current JUnitContext"));
	}

	/**
	 * Verifies JPostmanAnnotationRunner behavior when a JUnit test class has no
	 * 
	 * @JPostmanTestContext fields.
	 *
	 *                      <p>
	 *                      Responsibility: the runner should clear the current
	 *                      JUnit context and return safely.
	 *                      </p>
	 */
	@Test
	public void annotationRunnerClearsJUnitCurrentContextWhenNoContextFieldsExist() throws Exception {
		JUnitPostmanFramework framework = new JUnitPostmanFramework();
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(framework);

		JUnitContext context = JUnitContext.create();
		JUnitContext.setCurrent(context);
		assertEquals(context, JUnitContext.current());

		Method method = EmptyFixture.class.getDeclaredMethod("plain");

		runner.run(new EmptyFixture(), method);

		AssertionError error = assertThrows(AssertionError.class, () -> JUnitContext.current());
		assertTrue(error.getMessage().contains("No current JUnitContext"));
	}

	/**
	 * Verifies JPostmanAnnotationRunner behavior when a TestNG test class has no
	 * 
	 * @JPostmanTestContext fields.
	 *
	 *                      <p>
	 *                      Responsibility: the runner should clear the current
	 *                      TestNG context and return safely.
	 *                      </p>
	 */
	@Test
	public void annotationRunnerClearsTestNgCurrentContextWhenNoContextFieldsExist() throws Exception {
		TestNgPostmanFramework framework = new TestNgPostmanFramework();
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(framework);

		TestNgContext context = TestNgContext.create();
		TestNgContext.setCurrent(context);
		assertEquals(context, TestNgContext.current());

		Method method = EmptyFixture.class.getDeclaredMethod("plain");

		runner.run(new EmptyFixture(), method);

		AssertionError error = assertThrows(AssertionError.class, () -> TestNgContext.current());
		assertTrue(error.getMessage().contains("No current TestNgContext"));
	}

	/**
	 * Verifies request-only annotation flow.
	 *
	 * <p>
	 * Responsibility: a test method annotated with @JPostmanRequest should prepare
	 * the context and select the configured request from the collection.
	 * </p>
	 */
	@Test
	public void runnerAppliesRequestAnnotationOnTestMethod() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		RequestFixture fixture = new RequestFixture();
		Method method = RequestFixture.class.getDeclaredMethod("prepareLoginRequest");

		runner.run(fixture, method);

		assertNotNull(fixture.base);
		assertNotNull(fixture.base.ctx().request());
		assertNotNull(JUnitContext.current().request());
	}

	/**
	 * Verifies the main annotation runtime flow.
	 *
	 * <p>
	 * Covers context preparation, properties loading, resource opening, request
	 * selection, dependency execution, dependency cache, executor lookup, executor
	 * method invocation, response execution, and response verification.
	 * </p>
	 */
	@Test
	public void runnerCoversRequestResponseDependencyExecutorAndCacheFlow() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		RequestFixture fixture = new RequestFixture();

		Method requestMethod = RequestFixture.class.getDeclaredMethod("prepareLoginRequest");

		// Covers applyRequest(C, Collection, JPostmanRequest).
		runner.run(fixture, requestMethod);

		assertNotNull(fixture.base);
		assertNotNull(fixture.base.ctx().request());
		assertNotNull(JUnitContext.current().request());

		Method responseMethod = RequestFixture.class.getDeclaredMethod("getCurrentAuthUser");

		/*
		 * loadCollectionOnly -> only covered when environment is blank runDependency ->
		 * covered when dependsOn has at least one method applyRequest -> covered
		 * by @JPostmanRequest and @JPostmanResponse executeResponse -> covered
		 * by @JPostmanResponse findExecutor -> covered by @JPostmanResponse(executor =
		 * "...") validateExecutorMethod -> covered when @JPostmanExecutor exists
		 * findNoArgMethod -> covered by dependsOn = "login" invoke -> covered when
		 * dependency method or executor method is called isCached -> covered when
		 * dependency runs and checks cache
		 * 
		 * Covers: - applyRequest(C, Collection, JPostmanResponse) -
		 * runDependencies(...) - runDependency(...) - findNoArgMethod(...) -
		 * invoke(...) - cacheKey(...) - isCached(...) - executeResponse(...) -
		 * findExecutor(...) - validateExecutorMethod(...)
		 */
		runner.run(fixture, responseMethod);

		assertNotNull(fixture.base);
		assertEquals("token-123", fixture.base.cache("accessToken"));
		assertEquals(1, fixture.loginCount);
		assertEquals(1, fixture.executorCount);
		assertEquals("authExecutor", fixture.executorMethodName);
	}

	/**
	 * Verifies namespace-specific collection-only loading.
	 *
	 * <p>
	 * Responsibility: when a namespace has a collection but no environment, the
	 * runner should load the collection without requiring an environment file.
	 * </p>
	 */
	@Test
	public void runnerCoversLoadCollectionOnlyWithProductNamespace() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		ProductFixture fixture = new ProductFixture();
		Method method = ProductFixture.class.getDeclaredMethod("prepareLoginRequest");

		runner.run(fixture, method);

		assertNotNull(fixture.loaded);
		assertNotNull(fixture.product);
		assertNotNull(fixture.product.request());
	}

	/**
	 * Verifies the public JUnit entry point in JPostmanAnnotationEngine.
	 *
	 * <p>
	 * Responsibility: runJUnit creates the JUnit framework adapter and delegates to
	 * the shared annotation runner.
	 * </p>
	 */
	@Test
	public void runJUnitCallsAnnotationRunner() throws Exception {
		Method method = EmptyFixture.class.getDeclaredMethod("plain");

		JUnitContext.setCurrent(JUnitContext.create());

		assertDoesNotThrow(() -> JPostmanAnnotationEngine.runJUnit(new EmptyFixture(), method));

		assertThrows(AssertionError.class, () -> JUnitContext.current());
	}

	/**
	 * Verifies the public TestNG entry point in JPostmanAnnotationEngine.
	 *
	 * <p>
	 * Responsibility: runTestNg creates the TestNG framework adapter and delegates
	 * to the shared annotation runner.
	 * </p>
	 */
	@Test
	public void runTestNgCallsAnnotationRunner() throws Exception {
		Method method = EmptyFixture.class.getDeclaredMethod("plain");

		TestNgContext.setCurrent(TestNgContext.create());

		assertDoesNotThrow(() -> JPostmanAnnotationEngine.runTestNg(new EmptyFixture(), method));

		assertThrows(AssertionError.class, () -> TestNgContext.current());
	}

	/**
	 * Verifies the private constructor of the utility-style engine class.
	 */
	@Test
	public void constructorIsPrivate() throws Exception {
		Constructor<JPostmanAnnotationEngine> constructor = JPostmanAnnotationEngine.class.getDeclaredConstructor();

		constructor.setAccessible(true);

		assertDoesNotThrow(() -> constructor.newInstance());
	}

	/**
	 * Verifies all Comparison operators and parse validation branches.
	 */
	@Test
	public void comparisonParsesAndComparesAllOperators() {
		Comparison equals = Comparison.parseComparison("id = 1");
		assertEquals("id", equals.path);
		assertEquals("=", equals.operator);
		assertEquals("1", equals.expected);

		Comparison comparison = new Comparison("id", "=", "1");
		assertTrue(comparison.compare(1, "=", "1"));
		assertTrue(comparison.compare(1, "==", 1));
		assertTrue(comparison.compare("John", "!=", "Jane"));
		assertTrue(comparison.compare(1, "<", 2));
		assertTrue(comparison.compare(2, "<=", 2));
		assertTrue(comparison.compare(3, ">", 2));
		assertTrue(comparison.compare(3, ">=", 3));
		assertTrue(comparison.compare(null, "=", null));
		assertFalse(comparison.compare(null, "=", "value"));

		assertEquals("<=", Comparison.parseComparison("id <= 2").operator);
		assertEquals(">=", Comparison.parseComparison("id >= 2").operator);
		assertEquals("!=", Comparison.parseComparison("id != 2").operator);
		assertEquals("<", Comparison.parseComparison("id < 2").operator);
		assertEquals(">", Comparison.parseComparison("id > 2").operator);

		IllegalStateException unsupported = assertThrows(IllegalStateException.class,
				() -> comparison.compare(1, "contains", 1));
		assertTrue(unsupported.getMessage().contains("Unsupported compare operator"));

		IllegalStateException nonNumeric = assertThrows(IllegalStateException.class,
				() -> comparison.compare("abc", ">", 1));
		assertTrue(nonNumeric.getMessage().contains("Comparison requires numeric value"));

		assertThrows(IllegalStateException.class, () -> Comparison.parseComparison("id"));
		assertThrows(IllegalStateException.class, () -> Comparison.parseComparison("id = "));
	}

	/**
	 * Verifies PreparedContexts first-context and update paths.
	 */
	@Test
	public void preparedContextsReturnsFirstContextAndUpdatesInjectedField() {
		ContextFieldFixture fixture = new ContextFieldFixture();
		JUnitContext first = JUnitContext.create();
		JUnitContext second = JUnitContext.create();
		PreparedContext<JUnitContext> prepared = new PreparedContext<>(first, null, fixture, contextField("ctx"));
		PreparedContexts<JUnitContext> contexts = new PreparedContexts<>();

		contexts.put("secondary", prepared);

		assertSame(first, contexts.firstContext());
		contexts.update("secondary", second);

		assertSame(second, contexts.context("secondary"));
		assertSame(second, fixture.ctx);
	}

	/**
	 * Verifies firstContext failure when no contexts exist.
	 */
	@Test
	public void preparedContextsFailsWhenFirstContextMissing() {
		PreparedContexts<JUnitContext> contexts = new PreparedContexts<>();

		IllegalStateException error = assertThrows(IllegalStateException.class, contexts::firstContext);

		assertEquals("No JPostman runtime context found.", error.getMessage());
	}

	/**
	 * Verifies JPostmanResourceLoader property and resource helpers.
	 */
	@Test
	public void resourceLoaderCoversPropertyOpenAndFallbackHelpers() throws Exception {
		Properties properties = JPostmanResourceLoader.loadProperties(JPostmanDataLoader.DEFAULT_CONFIG,
				JPostmanAnnotationCoverageTest.class);

		assertNotNull(JPostmanResourceLoader.open(COLLECTION, JPostmanAnnotationCoverageTest.class));
		assertEquals("collection.product", JPostmanResourceLoader.propertyKey("collection", "product"));
		assertEquals("rules", JPostmanResourceLoader.propertyKey("rules", ""));
		assertEquals(COLLECTION, JPostmanResourceLoader.property(properties, "collection", ""));
		assertEquals(COLLECTION, JPostmanResourceLoader.property(properties, "collection", "product"));

		Properties sharedCollection = new Properties();
		sharedCollection.setProperty("collection", "classpath:shared-collection.json");
		assertEquals("classpath:shared-collection.json",
				JPostmanResourceLoader.propertyOrDefault(sharedCollection, "collection", "product"));
		sharedCollection.setProperty("collection.product", "classpath:product-collection.json");
		assertEquals("classpath:product-collection.json",
				JPostmanResourceLoader.propertyOrDefault(sharedCollection, "collection", "product"));

		assertEquals("annotation", JPostmanResourceLoader.firstNonBlank("annotation", "fallback"));
		assertEquals("fallback", JPostmanResourceLoader.firstNonBlank(" ", "fallback"));
		assertEquals("", JPostmanResourceLoader.firstNonBlank(null, null));

		IOException missing = assertThrows(IOException.class,
				() -> JPostmanResourceLoader.open("missing-resource.json", JPostmanAnnotationCoverageTest.class));
		assertTrue(missing.getMessage().contains("File or classpath resource not found: missing-resource.json"),
				"Actual message: " + missing.getMessage());
	}

	/**
	 * Verifies annotation validation success and failure paths.
	 */
	@Test
	public void annotationValidatorAcceptsValidClassAndRejectsInvalidTestHelpers() throws Exception {
		assertDoesNotThrow(() -> JPostmanAnnotationValidator.validateTestClass(RequestFixture.class));

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationValidator.validateTestClass(InvalidHelperFixture.class));

		String message = error.getMessage();
		assertTrue(message.contains("- InvalidHelperFixture.invalidRequest()\n"));
		message = message.replace("- InvalidHelperFixture.invalidRequest()\n", "");

		assertTrue(message.contains("- InvalidHelperFixture.invalidCachedResponse()\n"));
		message = message.replace("- InvalidHelperFixture.invalidCachedResponse()\n", "");

		assertEquals(message, "Invalid JPostman annotation usage.\n\n"
				+ "@JPostmanRequest methods must not be annotated with @Test.\n"
				+ "They are request helper methods invoked by JPostman, not test methods invoked by the test framework.\n"
				+ "\nInvalid helper methods:\n\n@JPostmanResponse(cache) cannot be used with @Test.\n"
				+ "Remove @Test to use it as a cached dependency, or remove cache to keep it as a test.\n\n"
				+ "Invalid cached response methods:\n");
		assertEquals(2, error.getStackTrace().length);

		Constructor<JPostmanAnnotationValidator> constructor = JPostmanAnnotationValidator.class
				.getDeclaredConstructor();
		constructor.setAccessible(true);
		assertDoesNotThrow(() -> constructor.newInstance());
	}

	/**
	 * Verifies direct request discovery helpers, including include/exclude
	 * normalization.
	 * 
	 * @throws IOException
	 */
	@Test
	public void requestDiscoveryFindsNamesAndExplicitResponses() throws IOException {
		JPostmanRequestDiscovery discovery = new JPostmanRequestDiscovery();
		Collection collection = collectionWithTwoRequests();

		List<String> names = discovery.runnerRequestNames(collection, "");
		assertTrue(names.contains("Login user and get tokens"));
		assertTrue(names.contains("Get current auth user"));

		Set<String> normalized = discovery.normalizeNames(new String[] { " one ", "", null, "two", "one" });
		assertEquals(Set.of("one", "two"), normalized);
		assertTrue(discovery.normalizeNames(null).isEmpty());

		assertTrue(discovery.hasExplicitResponse(RunnerFixture.class, "", "", "Get current auth user"));
		assertFalse(discovery.hasExplicitResponse(RunnerFixture.class, "", "", "Missing"));
	}

	/**
	 * Verifies assertion runner rule loading and every supported assertion rule
	 * type.
	 */
	@Test
	public void assertionRunnerAppliesAllSupportedRules() throws Exception {
		JUnitPostmanFramework framework = new JUnitPostmanFramework();
		JUnitContext context = framework.create();
		framework.request(context, collectionWithTwoRequests().getRequest("Get current auth user"));
		framework.response(context, okExecutor(
				"{\"id\":1,\"firstName\":\"John\",\"active\":true,\"products\":[{\"stock\":5,\"price\":10,\"discount\":10},{\"stock\":2,\"price\":15,\"discount\":20}]}"));

		Map<String, Map<String, String>> rules = JPostmanAssertionRunner.loadAssertionRules(AssertionFixture.class,
				List.of("classpath:annotation-test-assertions.ini"));

		assertDoesNotThrow(() -> new JPostmanAssertionRunner<>(framework).apply(context, rules, new String[] { "base" },
				"Get current auth user", false, false));
	}

	/**
	 * Verifies assertion runner error branches for missing, invalid, circular, and
	 * unsupported rules.
	 */
	@Test
	public void assertionRunnerReportsInvalidRuleConfigurations() throws Exception {
		JUnitPostmanFramework framework = new JUnitPostmanFramework();
		JUnitContext context = framework.create();
		framework.request(context, collectionWithTwoRequests().getRequest("Get current auth user"));
		framework.response(context, okExecutor("{\"id\":1}"));
		JPostmanAssertionRunner<JUnitContext> runner = new JPostmanAssertionRunner<>(framework);

		IllegalStateException missingError = assertThrows(IllegalStateException.class, () -> runner.apply(context,
				Map.of(), new String[] { "missing" }, "Get current auth user", false, false));
		assertTrue(missingError.getMessage().contains("No JPostman assertion files configured"));

		IllegalStateException invalidLine = assertThrows(IllegalStateException.class, () -> JPostmanAssertionRunner
				.loadAssertionRules(AssertionFixture.class, List.of("classpath:annotation-test-invalid.ini")));
		assertTrue(invalidLine.getMessage().contains("Invalid assertion rule line"));

		Map<String, Map<String, String>> circularRules = JPostmanAssertionRunner
				.loadAssertionRules(AssertionFixture.class, List.of("classpath:annotation-test-assertions.ini"));
		IllegalStateException circularError = assertThrows(IllegalStateException.class, () -> runner.apply(context,
				circularRules, new String[] { "a" }, "Get current auth user", false, false));
		assertTrue(circularError.getMessage().contains("Circular assertion rule inheritance"));

		Map<String, Map<String, String>> unsupportedRules = JPostmanAssertionRunner
				.loadAssertionRules(AssertionFixture.class, List.of("classpath:annotation-test-assertions.ini"));
		IllegalStateException unsupportedError = assertThrows(IllegalStateException.class, () -> runner.apply(context,
				unsupportedRules, new String[] { "unsupported" }, "Get current auth user", false, false));
		assertTrue(unsupportedError.getMessage().contains("Unsupported JPostman assertion rule"));
	}

	/**
	 * Verifies @JPostmanRunner execution, include/exclude filtering,
	 * explicit-response skipping, executor dependencies, and three-argument
	 * executor invocation.
	 */
	@Test
	public void runnerAnnotationExecutesDiscoveredRequestsWithFiltersAndExecutorDependencies() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		RunnerFixture fixture = new RunnerFixture();
		Method method = RunnerFixture.class.getDeclaredMethod("runProducts");

		runner.run(fixture, method);

		assertNotNull(fixture.base);
		assertEquals(1, fixture.runnerDependencyCount);
		assertEquals(1, fixture.executorCount);
		assertEquals("runnerExecutor", fixture.executorMethodName);
		assertEquals("Login user and get tokens", fixture.executorRequestName);
		assertEquals("runner-token", fixture.base.cache("runnerToken"));
	}

	/**
	 * Verifies that @JPostmanRunner does not silently pass when no default executor
	 * is configured. TestNG should report this as skipped.
	 */
	@Test
	public void runnerSkipsWhenNoDefaultExecutorConfiguredForTestNg() throws Exception {
		JPostmanAnnotationRunner<TestNgContext> runner = new JPostmanAnnotationRunner<>(new TestNgPostmanFramework());
		TestNgRunnerWithoutExecutorFixture fixture = new TestNgRunnerWithoutExecutorFixture();
		Method method = TestNgRunnerWithoutExecutorFixture.class.getDeclaredMethod("productRunner");

		SkipException skipped = assertThrows(SkipException.class, () -> runner.run(fixture, method));

		assertTrue(skipped.getMessage().contains("No default @JPostmanExecutor"),
				"Actual message: " + skipped.getMessage());
	}

	/**
	 * Verifies that executor configuration errors are reported before any request
	 * flow runs, with the same user-friendly validation style as invalid @Test
	 * helpers.
	 */
	@Test
	public void executorValidationReportsDuplicateIdsDefaultsSignatureAndReturnBeforeRun() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		AssertionError error = assertThrows(AssertionError.class,
				() -> runner.setup(new InvalidExecutorValidationFixture()));

		assertTrue(error.getMessage().contains("Invalid JPostman annotation usage"));
		assertTrue(error.getMessage().contains("@JPostmanExecutor ids must be unique"));
		assertTrue(error.getMessage().contains("id=\"duplicate\""));
		assertTrue(error.getMessage().contains("Only one default @JPostmanExecutor is allowed"));
		assertTrue(error.getMessage().contains("@JPostmanExecutor methods have unsupported parameters"));
		assertTrue(error.getMessage().contains("InvalidExecutorValidationFixture.badSignature"));
		assertTrue(error.getMessage().contains("@JPostmanExecutor methods must return ApiExecutor or void"));
		assertTrue(error.getMessage().contains("InvalidExecutorValidationFixture.badReturn"));
		assertTrue(error.getStackTrace().length >= 6);
	}

	/**
	 * Verifies that explicit status verification and assertion sections are
	 * mutually exclusive.
	 */
	@Test
	public void verifyAndAssertsCannotBeUsedTogether() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		AssertionError responseError = assertThrows(AssertionError.class,
				() -> runner.run(new InvalidVerifyAndAssertsResponseFixture(),
						InvalidVerifyAndAssertsResponseFixture.class.getDeclaredMethod("response")));
		assertTrue(responseError.getMessage().contains("@JPostmanResponse cannot use verify and asserts together."),
				"Actual message: " + responseError.getMessage());

		AssertionError runnerError = assertThrows(AssertionError.class,
				() -> runner.run(new InvalidVerifyAndAssertsRunnerFixture(),
						InvalidVerifyAndAssertsRunnerFixture.class.getDeclaredMethod("runner")));
		assertTrue(runnerError.getMessage().contains("@JPostmanRunner cannot use verify and asserts together."),
				"Actual message: " + runnerError.getMessage());
	}

	/**
	 * Verifies dependency methods that are themselves annotated with
	 * 
	 * @JPostmanResponse and @JPostmanRunner.
	 *
	 *                   <p>
	 *                   This directly covers the runResponseDependency and
	 *                   runRunnerDependency paths in JPostmanAnnotationRunner. The
	 *                   dependency methods also return cache values so the test
	 *                   verifies that each dependency path executes once and stores
	 *                   its result.
	 *                   </p>
	 */
	@Test
	public void runnerExecutesResponseAndRunnerAnnotatedDependencies() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		ResponseAndRunnerDependencyFixture fixture = new ResponseAndRunnerDependencyFixture();
		Method method = ResponseAndRunnerDependencyFixture.class.getDeclaredMethod("mainResponse");

		runner.run(fixture, method);

		assertNotNull(fixture.base);
		assertEquals(1, fixture.responseDependencyCount);
		assertEquals(1, fixture.runnerDependencyCount);
		assertEquals(3, fixture.executorCount);
		assertEquals(3L, fixture.executorMethodNames.stream().filter("dependencyExecutor"::equals).count());
		assertNotNull(fixture.responseDependencyInfo);
		assertEquals("responseDependency", fixture.responseDependencyInfo.method);
		assertTrue(fixture.responseDependencyInfo.methodIndex >= 0);
		assertEquals("responseDependency",
				fixture.responseDependencyInfo.methods.get(fixture.responseDependencyInfo.methodIndex));
		assertNotNull(fixture.runnerDependencyInfo);
		assertEquals("runnerDependency", fixture.runnerDependencyInfo.method);
		assertTrue(fixture.runnerDependencyInfo.methodIndex >= 0);
		assertEquals("runnerDependency",
				fixture.runnerDependencyInfo.methods.get(fixture.runnerDependencyInfo.methodIndex));

		runner.run(fixture, method);

		assertEquals(1, fixture.responseDependencyCount);
		assertEquals(2, fixture.runnerDependencyCount);
	}

	/**
	 * Verifies dependency failure branches in JPostmanAnnotationRunner.
	 */
	@Test
	public void runnerReportsDependencyAndExecutorErrors() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		AssertionError missingDependency = assertThrows(AssertionError.class, () -> runner
				.run(new MissingDependencyFixture(), MissingDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(missingDependency.getMessage().contains("Dependency method not found"));

		AssertionError plainDependency = assertThrows(AssertionError.class, () -> runner
				.run(new PlainDependencyFixture(), PlainDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(plainDependency.getMessage().contains("Dependency method must be annotated"));

		AssertionError nullDependency = assertThrows(AssertionError.class, () -> runner.run(new NullDependencyFixture(),
				NullDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(nullDependency.getMessage().contains("Dependency method returned null"));

		AssertionError missingExecutor = assertThrows(AssertionError.class, () -> runner
				.run(new MissingExecutorFixture(), MissingExecutorFixture.class.getDeclaredMethod("response")));
		assertTrue(missingExecutor.getMessage().contains("JPostman executor not found"));

		AssertionError invalidExecutor = assertThrows(AssertionError.class,
				() -> runner.run(new InvalidExecutorSignatureFixture(),
						InvalidExecutorSignatureFixture.class.getDeclaredMethod("response")));
		assertTrue(invalidExecutor.getMessage().contains("@JPostmanExecutor methods have unsupported parameters."));

		AssertionError legacyStringExecutor = assertThrows(AssertionError.class,
				() -> runner.run(new LegacyStringExecutorFixture(),
						LegacyStringExecutorFixture.class.getDeclaredMethod("response")));
		assertTrue(legacyStringExecutor.getMessage().contains("LegacyStringExecutorFixture.legacyExecutor"));
		assertTrue(legacyStringExecutor.getMessage().contains("JUnitContext"));
		assertTrue(legacyStringExecutor.getMessage().contains("String"));
		assertTrue(legacyStringExecutor.getMessage()
				.contains("Supported signatures are: (), (context), (JPostmanInfo), or (context, JPostmanInfo)."));

		AssertionError legacyRequestExecutor = assertThrows(AssertionError.class,
				() -> runner.run(new LegacyRequestNameExecutorFixture(),
						LegacyRequestNameExecutorFixture.class.getDeclaredMethod("response")));
		assertTrue(legacyRequestExecutor.getMessage().contains("LegacyRequestNameExecutorFixture.legacyExecutor"));
		assertTrue(legacyRequestExecutor.getMessage().contains("JUnitContext"));
		assertTrue(legacyRequestExecutor.getMessage().contains("String"));
		assertTrue(legacyRequestExecutor.getMessage()
				.contains("Supported signatures are: (), (context), (JPostmanInfo), or (context, JPostmanInfo)."));

		AssertionError nullExecutor = assertThrows(AssertionError.class,
				() -> runner.run(new NullExecutorFixture(), NullExecutorFixture.class.getDeclaredMethod("response")));
		assertTrue(nullExecutor.getMessage().contains("JPostman executor returned null"));

		AssertionError wrongExecutorType = assertThrows(AssertionError.class, () -> runner
				.run(new WrongExecutorTypeFixture(), WrongExecutorTypeFixture.class.getDeclaredMethod("response")));
		assertTrue(
				wrongExecutorType.getMessage().contains("@JPostmanExecutor methods must return ApiExecutor or void."));
	}

	/**
	 * Verifies compact JPostman.Test proxy invocation, context-level default
	 * executor creation with apply(request), and report injection in one annotation
	 * execution.
	 */
	@Test
	public void compactTestProxyContextApplyExecutorAndReportAreCovered() throws Exception {
		CoverageApplyExecutor.reset();
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		CompactApplyExecutorReportFixture fixture = new CompactApplyExecutorReportFixture();
		Method method = CompactApplyExecutorReportFixture.class.getDeclaredMethod("response", JPostman.Test.class);

		runner.setup(fixture);
		runner.run(fixture, method);

		method.setAccessible(true);
		method.invoke(fixture, fixture.jctx.ctx());

		assertNotNull(fixture.jctx);
		assertNotNull(fixture.api);
		assertNotNull(fixture.report);
		assertEquals(1, CoverageApplyExecutor.applyCount);
		assertEquals(1, fixture.responseCount);
		assertTrue(fixture.sawRequest);
		assertTrue(fixture.sawResponse);
		assertEquals(1, fixture.report.passed.size());
		assertDoesNotThrow(fixture.report::summary);
	}

	/**
	 * Verifies context-level session default executor creation and reuse.
	 *
	 * <p>
	 * The first response covers create() and setRequest(request). The second
	 * response covers the cached session executor path that sets a new request
	 * before reuse.
	 * </p>
	 */
	@Test
	public void contextSessionDefaultExecutorReusesSessionAndReportAreCovered() throws Exception {
		CoverageSessionExecutor.reset();
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		CompactSessionExecutorReportFixture fixture = new CompactSessionExecutorReportFixture();
		Method login = CompactSessionExecutorReportFixture.class.getDeclaredMethod("login",
				io.jpostman.annotations.JPostman.Test.class);
		Method user = CompactSessionExecutorReportFixture.class.getDeclaredMethod("user",
				io.jpostman.annotations.JPostman.Test.class);

		runner.setup(fixture);
		runner.run(fixture, login);
		runner.run(fixture, user);

		assertNotNull(fixture.report);
		assertEquals(1, CoverageSessionExecutor.createCount);
		assertEquals(2, CoverageSessionExecutor.setRequestCount);
		assertEquals(2, CoverageSessionExecutor.responseCount);
		assertEquals(2, fixture.report.passed.size());
		assertDoesNotThrow(fixture.report::summary);
	}

	/**
	 * Verifies compact executor string values also accept Java-style .class suffix
	 * by normalizing it to a fully qualified class name.
	 */
	@Test
	public void compactExecutorStringAcceptsDotClassSuffix() {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		assertDoesNotThrow(() -> runner.setup(new CompactExecutorDotClassSyntaxFixture()));
	}

	/**
	 * Verifies compact executor string values report missing classes with the
	 * standard JPostman context error format.
	 */
	@Test
	public void compactExecutorStringRejectsMissingClassWithFormattedMessage() {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> runner.setup(new InvalidCompactExecutorMissingClassFixture()));

		assertEquals("Invalid JPostman executor class: bad.missing.Executor\n"
				+ "The executor class could not be loaded.\n"
				+ "Use a fully qualified class name available on the test classpath.\n"
				+ "Example: executor = \"io.jpostman.restassured.RestAssuredExecutor\".\n"
				+ "(@JPostmanContext: config=<default>, collection=" + COLLECTION + ", environment=<default>)\n",
				error.getMessage());
	}

	/**
	 * Verifies the compact JPostman.Info facade can expose the full runtime info
	 * attributes when a test needs direct access to method, request, id, namespace,
	 * or other runtime fields.
	 */
	@Test
	public void compactInfoAttrReturnsRuntimeInfoAttributes() {
		JPostmanInfo parent = new JPostmanInfo("filter1", "", "", "").annotation("@JPostmanResponse").id("#response");
		parent.method("filter1");
		JPostmanInfo child = parent.child("authRequest", "", "", "Get current auth user").annotation("@JPostmanRequest")
				.id("#token");
		child.method("authRequest");
		JPostman.Info info = child;

		JPostmanInfo attr = info.attr();

		assertSame(info, attr);
		assertEquals("authRequest", attr.method);
		assertEquals(1, attr.methodIndex);
		assertEquals(List.of("filter1", "authRequest"), attr.methods);
		assertEquals("Get current auth user", attr.request);
		assertEquals("token", attr.id);
	}

	/**
	 * Verifies JPostmanInfo exposes the zero-based index of the current method
	 * entry inside the shared execution chain.
	 */
	@Test
	public void infoLogShowsCurrentMethodIndex() {
		JPostmanInfo info = new JPostmanInfo("filter1", "", "", "Get current auth user")
				.annotation("@JPostmanResponse");

		info.method("filter1");
		int executorIndex = info.appendMethod("HttpClientExecutor(#token)");
		JPostmanInfo executor = info.child("HttpClientExecutor", "", "", "Get current auth user")
				.annotation("@JPostmanContext executor").methodIndex(executorIndex);
		JPostmanInfo interceptor = info
				.childExact("defaultIntercept", new String[0], "", "", "", "", "Get current auth user")
				.annotation("@JPostmanExecutor intercept");

		interceptor.method("defaultIntercept");

		assertEquals(0, info.methodIndex);
		assertEquals(1, executor.methodIndex);
		assertEquals(2, interceptor.methodIndex);
		assertTrue(interceptor.log().contains("methodIndex=2"));

		String shortLog = interceptor.log(false);
		assertFalse(shortLog.contains("methodIndex="));
		assertFalse(shortLog.contains("methods="));
		assertFalse(shortLog.contains("created="));
		assertTrue(shortLog.contains("method=defaultIntercept"));
		assertTrue(shortLog.contains(", request=Get current auth user"));
	}

	/**
	 * Verifies the context debug API defaults.
	 */
	@Test
	public void debugDefaultsUseNoAutomaticOutputButResponseLoggingEnabled() throws Exception {
		assertArrayEquals(new String[] { "debug" },
				(String[]) JPostman.Context.class.getMethod("logs").getDefaultValue());
		assertArrayEquals(new String[] { "debug" },
				(String[]) JPostmanContext.class.getMethod("logs").getDefaultValue());
		assertArrayEquals(new String[] { "none" },
				(String[]) JPostman.Context.class.getMethod("debug").getDefaultValue());
		assertArrayEquals(new String[] { "none" },
				(String[]) JPostmanContext.class.getMethod("debug").getDefaultValue());
		assertEquals("debug", JPostman.Request.class.getMethod("log").getDefaultValue());
		assertEquals("debug", JPostmanRequest.class.getMethod("log").getDefaultValue());
		assertEquals("debug", JPostman.Response.class.getMethod("log").getDefaultValue());
		assertEquals("debug", JPostmanResponse.class.getMethod("log").getDefaultValue());
		assertEquals("debug", JPostman.Runner.class.getMethod("log").getDefaultValue());
		assertEquals("debug", JPostmanRunner.class.getMethod("log").getDefaultValue());
	}

	/**
	 * Verifies debug supports combined request/response/info modes and keeps
	 * none/all exclusive.
	 */
	@Test
	public void debugSupportsCombinedModesAndRejectsExclusiveModes() {
		java.util.EnumSet<JPostmanRuntimeOptions.LogOutput> combined = JPostmanRuntimeOptions.LogOutput.from("info",
				"response");

		assertTrue(combined.contains(JPostmanRuntimeOptions.LogOutput.INFO));
		assertTrue(combined.contains(JPostmanRuntimeOptions.LogOutput.RESPONSE));
		assertFalse(combined.contains(JPostmanRuntimeOptions.LogOutput.REQUEST));

		assertThrows(IllegalArgumentException.class, () -> JPostmanRuntimeOptions.LogOutput.from("none", "info"));
		assertThrows(IllegalArgumentException.class, () -> JPostmanRuntimeOptions.LogOutput.from("all", "response"));
	}

	/**
	 * Verifies logs uses one output mode only and supports failure verbosity modes.
	 */
	@Test
	public void logsSupportSingleFailureOutputModesOnly() {
		assertEquals(JPostmanRuntimeOptions.LogMode.NONE, JPostmanRuntimeOptions.LogMode.from("none"));
		assertEquals(JPostmanRuntimeOptions.LogMode.ERROR, JPostmanRuntimeOptions.LogMode.from("error"));
		assertEquals(JPostmanRuntimeOptions.LogMode.DEBUG, JPostmanRuntimeOptions.LogMode.from("debug"));

		assertDoesNotThrow(() -> JPostmanRuntimeOptions.LogMode.validateLocal("none"));
		assertDoesNotThrow(() -> JPostmanRuntimeOptions.LogMode.validateLocal("debug"));
		assertDoesNotThrow(() -> JPostmanRuntimeOptions.LogMode.validateLocal("error"));
		assertThrows(IllegalArgumentException.class, () -> JPostmanRuntimeOptions.LogMode.validateLocal("all"));

		assertThrows(IllegalArgumentException.class, () -> JPostmanRuntimeOptions.LogMode.from("debug", "error"));
		assertThrows(IllegalArgumentException.class, () -> JPostmanRuntimeOptions.LogMode.from("debug,error"));
		assertThrows(IllegalArgumentException.class, () -> JPostmanRuntimeOptions.LogMode.from("request"));
	}

	/**
	 * Verifies compact mutable references can be updated from tag lambdas without
	 * exposing AtomicReference in user examples.
	 */
	@Test
	public void compactInfoRefSupportsLambdaUpdates() {
		JPostmanInfo info = new JPostmanInfo(new String[] { "mouse" }, "", "authRequest", "", "",
				"Get current auth user");
		JPostman.Info compact = info;

		JPostman.Ref<String> product = compact.ref("");
		JPostman.Ref<Integer> quantity = compact.ref();
		JPostman.Ref<Boolean> matched = compact.ref(false);

		compact.tags().any("mouse", "shoes").then(i -> {
			product.set("MOUSE");
			quantity.set(2);
			matched.set(true);
		}).otherwise(i -> product.set("DEFAULT"));

		assertEquals("MOUSE", product.get());
		assertEquals(2, quantity.get());
		assertEquals(true, matched.get());
		assertFalse(product.isEmpty());
		assertFalse(product.isNull());

		assertTrue(compact.ref().isEmpty());
		assertTrue(compact.ref("").isEmpty());
		assertFalse(compact.ref("value").isEmpty());
	}

	/**
	 * Verifies compact mutable references can append strings and add numeric values
	 * without exposing AtomicReference in user examples.
	 */
	@Test
	public void compactInfoRefAddSupportsStringAndNumericUpdates() {
		JPostman.Ref<String> used = new JPostman.Ref<>("");
		used.set("mouse").add(" keyboard");
		assertEquals("mouse keyboard", used.get());

		JPostman.Ref<Integer> count = new JPostman.Ref<>(1);
		count.add(2).add(3);
		assertEquals(6, count.get());

		JPostman.Ref<Double> score = new JPostman.Ref<>(1.5);
		score.add(0.25);
		assertEquals(1.75, score.get());

		JPostman.Ref<Object> unsupported = new JPostman.Ref<>(new Object());
		assertThrows(UnsupportedOperationException.class, () -> unsupported.add(new Object()));
	}

	/**
	 * Verifies otherwise runs only when none of the previous has/any tag conditions
	 * matched.
	 */
	@Test
	public void tagRulesOtherwiseRunsOnlyWhenNoConditionMatched() {
		JPostmanInfo mouse = new JPostmanInfo(new String[] { "mouse" }, "", "authRequest", "", "",
				"Get current auth user");
		JPostman.Ref<String> selected = mouse.ref("");

		mouse.tags().has("keyboard").then(i -> selected.set("KEYBOARD")).any("mouse", "shoes")
				.then(i -> selected.set("MOUSE")).otherwise(i -> selected.set("DEFAULT"));

		assertEquals("MOUSE", selected.get());

		JPostmanInfo missing = new JPostmanInfo(new String[] { "monitor" }, "", "authRequest", "", "",
				"Get current auth user");
		JPostman.Ref<String> fallback = missing.ref("");

		missing.tags().has("keyboard").then(i -> fallback.set("KEYBOARD")).any("mouse", "shoes")
				.then(i -> fallback.set("MOUSE")).otherwise(i -> fallback.set("DEFAULT"));

		assertEquals("DEFAULT", fallback.get());
	}

	/**
	 * Verifies tag rules can match regular expressions and read key/value tags.
	 */
	@Test
	public void tagRulesCanMatchRegexAndReadKeyValueTags() {
		JPostmanInfo info = new JPostmanInfo(new String[] { "mouse", "product=myMouse", "+12" }, "", "authRequest", "",
				"", "Get current auth user");
		JPostman.Ref<String> selected = info.ref("");
		JPostman.Ref<String> plainSelected = info.ref("");
		JPostman.Ref<Boolean> caseInsensitiveMatched = info.ref(false);
		JPostman.Ref<Boolean> plusNumberMatched = info.ref(false);
		JPostman.Ref<Boolean> productKeyMatched = info.ref(false);
		JPostman.Ref<Boolean> invalidRegexMatched = info.ref(false);

		info.tags().has(".*mouse.*", "product=.*").then((i, tags) -> selected.set(tags.get("product")))
				.any("(?i).*MOUSE.*").then(i -> caseInsensitiveMatched.set(true)).any("\\+\\d{1,2}")
				.then(i -> plusNumberMatched.set(true)).has("mouse")
				.then((i, tags) -> plainSelected.set(tags.get("mouse"))).has("product=.*")
				.then(i -> productKeyMatched.set(true)).any("product=[").then(i -> invalidRegexMatched.set(true));

		assertEquals("mouse", info.tags().get("mouse"));
		assertEquals("myMouse", info.tags().get("product"));
		assertNull(info.tags().get("keyboard"));
		assertEquals("myMouse", selected.get());
		assertEquals("mouse", plainSelected.get());
		assertTrue(caseInsensitiveMatched.get());
		assertTrue(plusNumberMatched.get());
		assertTrue(productKeyMatched.get());
		assertFalse(invalidRegexMatched.get());
	}

	/**
	 * Verifies runtime runner rules match the current request name and run end only
	 * for the last executed runner request.
	 */
	@Test
	public void runtimeRunnerRulesMatchRequestNamesAndEndOnlyOnLastRequest() {
		AtomicReference<String> request = new AtomicReference<>("Get all products");
		TestNgContext context = TestNgContext.create();
		JPostmanRuntime<TestNgContext> runtime = new JPostmanRuntime<>(null, "", namespace -> context, () -> context,
				() -> new JPostmanInfo("@JPostmanRunner", "runner", "", "", request.get()));
		AtomicInteger getAllMatched = new AtomicInteger();
		AtomicInteger searchMatched = new AtomicInteger();
		AtomicInteger otherwiseMatched = new AtomicInteger();
		AtomicInteger ended = new AtomicInteger();

		JPostmanRuntimeRunner.begin(List.of("Get all products", "Search products"));
		try {
			JPostmanRuntimeRunner.request(0, "Get all products");
			runtime.runner().has("Get all products").then(test -> {
				assertSame(context, test);
				getAllMatched.incrementAndGet();
			}).any("Search.*", "Limit.*").then((test, info) -> searchMatched.incrementAndGet())
					.otherwise(test -> otherwiseMatched.incrementAndGet()).end(test -> ended.incrementAndGet());

			request.set("Search products");
			JPostmanRuntimeRunner.request(1, "Search products");
			runtime.runner().has("Get all products").then(test -> getAllMatched.incrementAndGet())
					.any("Search.*", "Limit.*").then((test, info) -> {
						assertEquals("Search products", info.attr().request);
						searchMatched.incrementAndGet();
					}).otherwise(test -> otherwiseMatched.incrementAndGet()).end(test -> ended.incrementAndGet());
		} finally {
			JPostmanRuntimeRunner.clear();
		}

		assertEquals(1, getAllMatched.get());
		assertEquals(1, searchMatched.get());
		assertEquals(0, otherwiseMatched.get());
		assertEquals(1, ended.get());
	}

	/**
	 * Verifies JPostmanInfo keeps a single tag chain and searches only
	 * {@link JPostmanInfo#tags}.
	 */
	@Test
	public void infoCanSearchSingleTagChain() {
		JPostmanInfo parent = new JPostmanInfo(new String[] { "auth", "login" }, "", "login", "", "",
				"Login user and get tokens");
		JPostmanInfo child = parent.child("getCurrentAuthUser", new String[] { "user", "profile" }, "", "", "",
				"Get current auth user");
		JPostmanInfo next = child.withTags("orders", "profile");

		assertArrayEquals(new String[] { "auth", "login", "user", "profile" }, child.tags);
		assertArrayEquals(new String[] { "auth", "login", "user", "profile", "orders" }, next.tags);
		assertTrue(child.hasTag("profile"));
		assertTrue(child.hasTag("auth"));
		assertTrue(child.hasTag("missing", "login"));
		assertFalse(child.hasTag("missing", "other"));
	}

	/**
	 * Verifies dependency calls receive a single accumulated tag chain. The current
	 * annotation tags are appended only when calling the next dependency.
	 */
	@Test
	public void responseDependencyTagsAreAccumulatedForNextCalls() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		TagChainFixture fixture = new TagChainFixture();

		runner.run(fixture, TagChainFixture.class.getDeclaredMethod("response"));

		assertArrayEquals(new String[] { "keyboard" }, fixture.mouseTags);
		assertArrayEquals(new String[] { "keyboard", "mouse" }, fixture.shoesTags);
		assertArrayEquals(new String[] { "keyboard", "mouse", "shoes" }, fixture.computerTags);
	}

	/**
	 * Verifies circular dependency detection and JPostmanInfo invocation support.
	 */
	@Test
	public void runnerReportsCircularDependenciesAndPassesJPostmanInfo() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		AssertionError circular = assertThrows(AssertionError.class, () -> runner.run(new CircularDependencyFixture(),
				CircularDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(circular.getMessage().contains("Circular JPostman dependency detected"));
		assertTrue(circular.getMessage().contains("response -> requestDependency -> response"));

		InfoDependencyFixture fixture = new InfoDependencyFixture();
		runner.run(fixture, InfoDependencyFixture.class.getDeclaredMethod("response"));

		assertEquals("world", fixture.value);
		assertEquals("Get current auth user", fixture.executorRequestName);
	}

	/**
	 * Verifies context runner injection paths for @JPostmanContext and invalid
	 * field type validation.
	 */
	@Test
	public void contextRunnerInjectsLoadedContextAndRejectsInvalidCoreContextField() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		CoreContextFixture fixture = new CoreContextFixture();

		runner.setup(fixture);

		assertNotNull(fixture.base);
		assertNotNull(fixture.loaded);

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> runner.setup(new InvalidCoreContextFixture()));
		assertTrue(error.getMessage()
				.contains("field must be io.jpostman.JPostman.Context, JPostmanRuntime, or JPostman.Runtime"));
	}

	/**
	 * Verifies that an empty collection configuration fails clearly when no
	 * annotation value and no properties fallback are available.
	 */
	@Test
	public void contextRunnerRejectsBlankCollectionWhenConfigDisabled() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> runner.setup(new CollectionRequiredFixture()));

		assertTrue(error.getMessage().contains("JPostman collection is required"));
		assertTrue(error.getMessage().contains("@JPostmanContext(collection"));
	}

	/**
	 * Verifies that an explicitly configured properties file is required.
	 *
	 * <p>
	 * The default classpath:jpostman.properties is optional, but a user-provided
	 * config location must fail if it cannot be opened.
	 * </p>
	 */
	@Test
	public void contextRunnerRejectsExplicitMissingConfigFile() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		IOException error = assertThrows(IOException.class, () -> runner.setup(new MissingConfigFixture()));

		assertTrue(error.getMessage().contains("Classpath resource not found: classpath:missing-jpostman.properties"),
				"Actual message: " + error.getMessage());
	}

	/**
	 * Verifies public engine setup methods and cleaned JUnit failure helper.
	 */
	@Test
	public void annotationEngineSetupAndCleanFailureMethodsAreCovered() throws Exception {
		CoreContextFixture junit = new CoreContextFixture();
		JPostmanAnnotationEngine.setupJUnit(junit);
		assertNotNull(junit.base);

		TestNgContextFixture testng = new TestNgContextFixture();
		JPostmanAnnotationEngine.setupTestNg(testng);
		assertNotNull(testng.base);

		Method method = EmptyFixture.class.getDeclaredMethod("plain");
		Throwable cleanedAssertion = JPostmanAnnotationEngine.cleanJUnitFailure(new EmptyFixture(), method,
				new InvocationTargetException(new AssertionError("boom")));
		assertTrue(cleanedAssertion instanceof AssertionError);
		assertTrue(cleanedAssertion.getMessage().contains("boom"));

		Throwable cleanedRuntime = JPostmanAnnotationEngine.cleanJUnitFailure(new EmptyFixture(), method,
				new InvocationTargetException(new IllegalStateException("bad state")));
		assertTrue(cleanedRuntime instanceof IllegalStateException);
		assertTrue(cleanedRuntime.getMessage().contains("bad state"));
	}

	/**
	 * Verifies stack trace cleaner root-cause, copy, source-line, and
	 * suppressed-message paths.
	 */
	@Test
	public void stackTraceCleanerCoversRootCauseCleanFailureAndCleanThrowable() throws Exception {
		Method method = EmptyFixture.class.getDeclaredMethod("plain");
		AssertionError root = new AssertionError("assert failed");
		root.addSuppressed(new IllegalStateException("secure log details"));

		AssertionError cleanFailure = JPostmanStackTraceCleaner.cleanFailure(EmptyFixture.class, method,
				new InvocationTargetException(root));
		assertTrue(cleanFailure.getMessage().contains("assert failed"));
		assertTrue(cleanFailure.getMessage().contains("secure log details"));
		assertTrue(cleanFailure.getStackTrace().length > 0);

		Throwable cleanedNullPointer = JPostmanStackTraceCleaner.cleanThrowable(EmptyFixture.class, method,
				new NullPointerException());
		assertTrue(cleanedNullPointer instanceof NullPointerException);
		assertEquals("NullPointerException", cleanedNullPointer.getMessage());

		Throwable cleanedIllegalArgument = JPostmanStackTraceCleaner.cleanThrowable(EmptyFixture.class, method,
				new IllegalArgumentException("bad arg"));
		assertTrue(cleanedIllegalArgument instanceof IllegalArgumentException);

		Throwable cleanedRuntime = JPostmanStackTraceCleaner.cleanThrowable(EmptyFixture.class, method,
				new RuntimeException("runtime"));
		assertTrue(cleanedRuntime instanceof RuntimeException);

		Throwable cleanedChecked = JPostmanStackTraceCleaner.cleanThrowable(EmptyFixture.class, method,
				new Exception("checked"));
		assertTrue(cleanedChecked instanceof Exception);

		Throwable rootCause = JPostmanStackTraceCleaner.rootCause(
				new InvocationTargetException(new RuntimeException("outer", new IllegalStateException("inner"))));
		assertTrue(rootCause instanceof IllegalStateException);
		assertEquals("inner", rootCause.getMessage());

		AssertionError testNgBoundary = new AssertionError("testng boundary");
		testNgBoundary.setStackTrace(new StackTraceElement[] {
				new StackTraceElement(EmptyFixture.class.getName(), "plain", "EmptyFixture.java", 123),
				new StackTraceElement("io.jpostman.annotations.runtime.JPostmanAnnotationRunner", "run",
						"JPostmanAnnotationRunner.java", 151),
				new StackTraceElement("org.testng.internal.invokers.MethodInvocationHelper", "invokeHookable",
						"MethodInvocationHelper.java", 274),
				new StackTraceElement("org.testng.internal.invokers.TestInvoker", "invokeMethod", "TestInvoker.java",
						689) });
		StackTraceElement[] cleanedTestNgBoundary = JPostmanStackTraceCleaner.cleanStack(EmptyFixture.class, method,
				testNgBoundary);
		for (StackTraceElement element : cleanedTestNgBoundary) {
			assertFalse(element.getClassName().startsWith("org.testng.internal.invokers"));
		}

		AssertionError junitBoundary = new AssertionError("junit boundary");
		junitBoundary.setStackTrace(new StackTraceElement[] {
				new StackTraceElement(EmptyFixture.class.getName(), "plain", "EmptyFixture.java", 124),
				new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 568),
				new StackTraceElement("org.junit.platform.commons.util.ReflectionUtils", "invokeMethod",
						"ReflectionUtils.java", 728),
				new StackTraceElement("org.junit.jupiter.engine.execution.MethodInvocation", "proceed",
						"MethodInvocation.java", 60),
				new StackTraceElement(
						"org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation", "proceed",
						"InvocationInterceptorChain.java", 131),
				new StackTraceElement("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "main",
						"RemoteTestRunner.java", 211) });
		StackTraceElement[] cleanedJUnitBoundary = JPostmanStackTraceCleaner.cleanStack(EmptyFixture.class, method,
				junitBoundary);
		assertFalse(hasStackFrame(cleanedJUnitBoundary, "org.junit.jupiter.engine.execution.MethodInvocation"));
		assertFalse(hasStackFrame(cleanedJUnitBoundary, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner"));

		Properties noBoundaries = new Properties();
		noBoundaries.setProperty("stacktrace.boundary", "");
		StackTraceElement[] unbounded = JPostmanStackTraceCleaner.cleanStack(EmptyFixture.class, method, testNgBoundary,
				noBoundaries);
		assertTrue(hasStackFrame(unbounded, "org.testng.internal.invokers.TestInvoker"));

		Properties limitedBoundary = new Properties();
		limitedBoundary.setProperty("stacktrace.max", "3");
		limitedBoundary.setProperty("stacktrace.boundary",
				"org.testng.internal.invokers.MethodInvocationHelper,invoke0");
		StackTraceElement[] limited = JPostmanStackTraceCleaner.cleanStack(EmptyFixture.class, method, testNgBoundary,
				limitedBoundary);
		assertTrue(limited.length <= 3);
		assertFalse(hasStackFrame(limited, "org.testng.internal.invokers.TestInvoker"));

		Properties appendedBoundary = new Properties();
		appendedBoundary.setProperty("stacktrace.boundary.add",
				"io.jpostman.annotations.runtime.JPostmanAnnotationRunner");
		StackTraceElement[] appended = JPostmanStackTraceCleaner.cleanStack(EmptyFixture.class, method, testNgBoundary,
				appendedBoundary);
		assertFalse(hasStackFrame(appended, "org.testng.internal.invokers.MethodInvocationHelper"));

		AssertionError runtimeAssertion = new AssertionError("runtime assertion");
		runtimeAssertion.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("io.jpostman.secure.JPostmanAssertionError", "wrap",
						"JPostmanAssertionError.java", 52),
				new StackTraceElement("io.jpostman.testng.TestNgAssertions", "statusCode", "TestNgAssertions.java",
						200),
				new StackTraceElement("io.jpostman.annotations.runtime.JPostmanTestProxy", "invokeTarget",
						"JPostmanTestProxy.java", 190),
				new StackTraceElement(EmptyFixture.class.getName(), "plain", "EmptyFixture.java", 321),
				new StackTraceElement("jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0",
						"NativeMethodAccessorImpl.java", -2),
				new StackTraceElement("jdk.internal.reflect.NativeMethodAccessorImpl", "invoke",
						"NativeMethodAccessorImpl.java", 77),
				new StackTraceElement("jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke",
						"DelegatingMethodAccessorImpl.java", 43),
				new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 568),
				new StackTraceElement("org.junit.platform.commons.util.ReflectionUtils", "invokeMethod",
						"ReflectionUtils.java", 728),
				new StackTraceElement("org.junit.jupiter.engine.execution.MethodInvocation", "proceed",
						"MethodInvocation.java", 60),
				new StackTraceElement("org.junit.jupiter.engine.execution.InvocationInterceptorChain", "proceed",
						"InvocationInterceptorChain.java", 64) });
		AssertionError runtimeCleaned = JPostmanStackTraceCleaner.cleanRuntimeFailure(EmptyFixture.class, method,
				runtimeAssertion, false, true);
		assertEquals(EmptyFixture.class.getName(), runtimeCleaned.getStackTrace()[0].getClassName());
		assertEquals(321, runtimeCleaned.getStackTrace()[0].getLineNumber());
		assertFalse(hasStackFrame(runtimeCleaned.getStackTrace(), "io.jpostman.annotations.runtime.JPostmanTestProxy"));
		assertFalse(
				hasStackFrame(runtimeCleaned.getStackTrace(), "org.junit.jupiter.engine.execution.MethodInvocation"));

		Properties minimumRuntimeFrames = new Properties();
		minimumRuntimeFrames.setProperty("stacktrace.min", "5");
		StackTraceElement[] runtimeMinimum = JPostmanStackTraceCleaner.cleanStack(EmptyFixture.class, method,
				runtimeAssertion, minimumRuntimeFrames, true);
		assertTrue(runtimeMinimum.length >= 5);

		int plainSourceLine = JPostmanStackTraceCleaner.findSourceLine(EmptyFixture.class, "plain");
		assertTrue(plainSourceLine == -1 || plainSourceLine > 0);
		assertEquals(-1, JPostmanStackTraceCleaner.findSourceLine(EmptyFixture.class, "missingMethod"));
	}

	private static boolean hasStackFrame(StackTraceElement[] stackTrace, String className) {
		for (StackTraceElement element : stackTrace) {
			if (element.getClassName().equals(className) || element.getClassName().startsWith(className + ".")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Verifies TestNG listener helper paths through reflection without needing a
	 * TestNG runtime instance.
	 */
	@Test
	public void testNgListenerHelperMethodsAreCovered() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();

		Method uses = JPostmanTestNgAnnotationListener.class.getDeclaredMethod("usesJPostmanAnnotations", Object.class);
		uses.setAccessible(true);
		assertEquals(false, uses.invoke(listener, new EmptyFixture()));
		assertEquals(false, uses.invoke(listener, new Object[] { null }));
		assertEquals(true, uses.invoke(listener, new RequestFixture()));
		assertEquals(true, uses.invoke(listener, new AnnotatedTestNgFixture()));

		Method setupOnce = JPostmanTestNgAnnotationListener.class.getDeclaredMethod("setupOnce", Object.class);
		setupOnce.setAccessible(true);
		Object invalid = new InvalidHelperFixture();
		Throwable firstFailure = (Throwable) setupOnce.invoke(listener, invalid);
		Throwable secondFailure = (Throwable) setupOnce.invoke(listener, invalid);
		assertSame(firstFailure, secondFailure);
		assertTrue(firstFailure instanceof AssertionError);

		Method mark = JPostmanTestNgAnnotationListener.class.getDeclaredMethod("markSetupFailureReported",
				Object.class);
		mark.setAccessible(true);
		assertEquals(true, mark.invoke(listener, invalid));
		assertEquals(false, mark.invoke(listener, invalid));

		Method asRuntime = JPostmanTestNgAnnotationListener.class.getDeclaredMethod("asRuntime", Throwable.class);
		asRuntime.setAccessible(true);
		RuntimeException runtime = new RuntimeException("runtime");
		assertSame(runtime, asRuntime.invoke(null, runtime));
		Object wrapped = asRuntime.invoke(null, new Exception("checked"));
		assertTrue(wrapped instanceof IllegalStateException);
	}

	/**
	 * Verifies the real TestNG listener beforeInvocation paths.
	 *
	 * <p>
	 * Covers: no-op when the class has no JPostman annotations,
	 * configuration-method setup, successful test-method execution, cached setup
	 * failure reporting, and later setup-failure skip behavior.
	 * </p>
	 */
	@Test
	public void testNgListenerBeforeInvocationLifecyclePathsAreCovered() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();

		EmptyFixture empty = new EmptyFixture();
		Method plain = EmptyFixture.class.getDeclaredMethod("plain");
		assertDoesNotThrow(() -> listener.beforeInvocation(invokedMethod(plain, true, false), testResult(empty)));

		ListenerTestNgFixture lifecycle = new ListenerTestNgFixture();
		Method beforeClass = ListenerTestNgFixture.class.getDeclaredMethod("beforeClass");
		assertDoesNotThrow(
				() -> listener.beforeInvocation(invokedMethod(beforeClass, false, true), testResult(lifecycle)));
		assertNotNull(lifecycle.base);

		Method validTest = ListenerTestNgFixture.class.getDeclaredMethod("validTest");
		assertDoesNotThrow(() -> listener.run(hookCallBack(), testResult(lifecycle, validTest)));
		assertEquals(1, lifecycle.executorCount);
		assertNotNull(lifecycle.base.response());
		assertThrows(AssertionError.class, () -> TestNgContext.current());

		InvalidHelperFixture invalid = new InvalidHelperFixture();
		IInvokedMethod invalidMethod = invokedMethod(InvalidHelperFixture.class.getDeclaredMethod("invalidRequest"),
				true, false);
		AssertionError firstFailure = assertThrows(AssertionError.class,
				() -> listener.beforeInvocation(invalidMethod, testResult(invalid)));
		assertTrue(firstFailure.getMessage().contains("Invalid JPostman annotation usage"));

		SkipException skipped = assertThrows(SkipException.class,
				() -> listener.beforeInvocation(invalidMethod, testResult(invalid)));
		assertTrue(skipped.getMessage().contains("JPostman annotation setup failed"));
	}

	/**
	 * Verifies the real TestNG listener afterInvocation paths.
	 *
	 * <p>
	 * Covers: no-op when there are no JPostman annotations, throwable cleanup for a
	 * test method, TestNgContext cleanup in finally, skipped-test preservation, and
	 * configuration-method throwable cleanup.
	 * </p>
	 */
	@Test
	public void testNgListenerAfterInvocationLifecyclePathsAreCovered() throws Exception {
		JPostmanTestNgAnnotationListener listener = new JPostmanTestNgAnnotationListener();

		EmptyFixture empty = new EmptyFixture();
		Method plain = EmptyFixture.class.getDeclaredMethod("plain");
		AtomicReference<Throwable> emptyThrowable = new AtomicReference<>(new RuntimeException("empty"));
		listener.afterInvocation(invokedMethod(plain, true, false),
				testResult(empty, emptyThrowable, ITestResult.FAILURE));
		assertEquals("empty", emptyThrowable.get().getMessage());

		ListenerTestNgFixture fixture = new ListenerTestNgFixture();
		Method validTest = ListenerTestNgFixture.class.getDeclaredMethod("validTest");
		AtomicReference<Throwable> testThrowable = new AtomicReference<>(new RuntimeException("after boom"));
		TestNgContext.setCurrent(TestNgContext.create());

		listener.afterInvocation(invokedMethod(validTest, true, false),
				testResult(fixture, testThrowable, ITestResult.FAILURE));

		assertTrue(testThrowable.get().getMessage().contains("after boom"));
		assertThrows(AssertionError.class, () -> TestNgContext.current());

		Throwable skippedOriginal = new RuntimeException("skip should stay original");
		AtomicReference<Throwable> skippedThrowable = new AtomicReference<>(skippedOriginal);
		listener.afterInvocation(invokedMethod(validTest, true, false),
				testResult(fixture, skippedThrowable, ITestResult.SKIP));
		assertSame(skippedOriginal, skippedThrowable.get());

		Method beforeClass = ListenerTestNgFixture.class.getDeclaredMethod("beforeClass");
		AtomicReference<Throwable> configThrowable = new AtomicReference<>(new IllegalStateException("config boom"));
		listener.afterInvocation(invokedMethod(beforeClass, false, true),
				testResult(fixture, configThrowable, ITestResult.FAILURE));
		assertTrue(configThrowable.get().getMessage().contains("config boom"));
	}

	/**
	 * Verifies that a request-specific assertion section is authoritative when it
	 * exists.
	 *
	 * <p>
	 * Even when asserts selects the product section, the request named "Get all
	 * products" must resolve only [Get all products] and its own extends chain. It
	 * must not also merge the unrelated [product] section.
	 * </p>
	 */
	@Test
	public void assertionRunnerUsesRequestSectionBeforeConfiguredSections() throws Exception {
		JUnitPostmanFramework framework = new JUnitPostmanFramework();
		JUnitContext context = framework.create();

		framework.request(context, collectionWithTwoRequests().getRequest("Get current auth user"));
		framework.response(context, okExecutor("{}"));

		Map<String, Map<String, String>> rules = JPostmanAssertionRunner.loadAssertionRules(AssertionFixture.class,
				List.of("classpath:annotation-test-assertions.ini"));

		AssertionError error = assertThrows(AssertionError.class, () -> new JPostmanAssertionRunner<>(framework)
				.apply(context, rules, new String[0], "Get all products", true, false));

		assertTrue(error.getMessage().contains("Path not found: id"));
		assertFalse(error.getMessage().contains("Path not found: title"));
		assertFalse(error.getMessage().contains("Path not found: price"));
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> okResponse(json);
	}

	private static ApiResponse okResponse(String json) {
		return new ApiResponse(200, json, json.getBytes(), Map.of());
	}

	private static IInvokedMethod invokedMethod(Method javaMethod, boolean testMethod, boolean configurationMethod) {
		ITestNGMethod testNgMethod = testNgMethod(javaMethod);

		return (IInvokedMethod) Proxy.newProxyInstance(JPostmanAnnotationCoverageTest.class.getClassLoader(),
				new Class<?>[] { IInvokedMethod.class }, (proxy, method, args) -> {
					if ("isTestMethod".equals(method.getName())) {
						return testMethod;
					}
					if ("isConfigurationMethod".equals(method.getName())) {
						return configurationMethod;
					}
					if ("getTestMethod".equals(method.getName())) {
						return testNgMethod;
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static ITestNGMethod testNgMethod(Method javaMethod) {
		return (ITestNGMethod) Proxy.newProxyInstance(JPostmanAnnotationCoverageTest.class.getClassLoader(),
				new Class<?>[] { ITestNGMethod.class }, (proxy, method, args) -> {
					if ("getRealClass".equals(method.getName())) {
						return javaMethod.getDeclaringClass();
					}
					if ("getConstructorOrMethod".equals(method.getName())) {
						return new ConstructorOrMethod(javaMethod);
					}
					if ("getMethodName".equals(method.getName())) {
						return javaMethod.getName();
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static ITestResult testResult(Object instance) {
		return testResult(instance, new AtomicReference<>(), ITestResult.SUCCESS, null);
	}

	private static ITestResult testResult(Object instance, Method javaMethod) {
		return testResult(instance, new AtomicReference<>(), ITestResult.SUCCESS, javaMethod);
	}

	private static ITestResult testResult(Object instance, AtomicReference<Throwable> throwable, int status) {
		return testResult(instance, throwable, status, null);
	}

	private static ITestResult testResult(Object instance, AtomicReference<Throwable> throwable, int status,
			Method javaMethod) {
		return (ITestResult) Proxy.newProxyInstance(JPostmanAnnotationCoverageTest.class.getClassLoader(),
				new Class<?>[] { ITestResult.class }, (proxy, method, args) -> {
					if ("getInstance".equals(method.getName())) {
						return instance;
					}
					if ("getMethod".equals(method.getName()) && javaMethod != null) {
						return testNgMethod(javaMethod);
					}
					if ("getThrowable".equals(method.getName())) {
						return throwable.get();
					}
					if ("setThrowable".equals(method.getName())) {
						throwable.set((Throwable) args[0]);
						return null;
					}
					if ("getStatus".equals(method.getName())) {
						return status;
					}
					return defaultValue(method.getReturnType());
				});
	}

	private static IHookCallBack hookCallBack() {
		return (IHookCallBack) Proxy.newProxyInstance(JPostmanAnnotationCoverageTest.class.getClassLoader(),
				new Class<?>[] { IHookCallBack.class }, (proxy, method, args) -> defaultValue(method.getReturnType()));
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == char.class) {
			return (char) 0;
		}
		return null;
	}

	private static Collection collectionWithTwoRequests() throws IOException {
		return Collection.load(JsonParser.parseString("{\"item\": ["
				+ "{\"name\":\"Login user and get tokens\",\"request\":{\"method\":\"POST\",\"url\":\"https://example.com/auth/login\"}},"
				+ "{\"name\":\"Get current auth user\",\"request\":{\"method\":\"GET\",\"url\":\"https://example.com/auth/me\"}}"
				+ "]}").getAsJsonObject());
	}

	private static java.lang.reflect.Field contextField(String name) {
		try {
			java.lang.reflect.Field field = ContextFieldFixture.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Fixture for the main annotation runtime flow.
	 *
	 * <p>
	 * Simulates a user test class with context injection, request dependency,
	 * response annotation, named executor, executor dependency, and cache reuse.
	 * </p>
	 */

	private static final class CompactExecutorDotClassSyntaxFixture {
		@io.jpostman.annotations.JPostman.Context(config = "", collection = COLLECTION, executor = "io.jpostman.annotations.runtime.JPostmanAnnotationCoverageTest$CoverageApplyExecutor.class")
		private io.jpostman.annotations.JPostman.Runtime<io.jpostman.annotations.JPostman.Test> jctx;
	}

	private static final class InvalidCompactExecutorMissingClassFixture {
		@io.jpostman.annotations.JPostman.Context(config = "", collection = COLLECTION, executor = "bad.missing.Executor")
		private io.jpostman.annotations.JPostman.Runtime<io.jpostman.annotations.JPostman.Test> jctx;
	}

	private static final class CompactApplyExecutorReportFixture {
		@io.jpostman.annotations.JPostman.Context(config = "", collection = COLLECTION, executorClass = CoverageApplyExecutor.class)
		private io.jpostman.annotations.JPostman.Runtime<io.jpostman.annotations.JPostman.Test> jctx;

		@io.jpostman.annotations.JPostman.TestContext
		private JUnitContext api;

		@io.jpostman.annotations.JPostman.ReportContext
		private JPostmanReport report;

		private int responseCount;
		private boolean sawRequest;
		private boolean sawResponse;

		@io.jpostman.annotations.JPostman.Response(request = "Get current auth user", verify = 200)
		void response(io.jpostman.annotations.JPostman.Test ctx) {
			responseCount++;
			sawRequest = ctx.request() != null;
			sawResponse = ctx.response() != null;
			ctx.asserts(true);
			ctx.log(false);
			ctx.print(false);
		}
	}

	private static final class CompactSessionExecutorReportFixture {
		@io.jpostman.annotations.JPostman.Context(config = "", collection = COLLECTION, executorClass = CoverageSessionExecutor.class, session = true)
		private io.jpostman.annotations.JPostman.Runtime<io.jpostman.annotations.JPostman.Test> jctx;

		@io.jpostman.annotations.JPostman.ReportContext
		private JPostmanReport report;

		@io.jpostman.annotations.JPostman.Response(request = "Login user and get tokens", verify = 200)
		void login(io.jpostman.annotations.JPostman.Test ctx) {
			assertNotNull(ctx.request());
			assertNotNull(ctx.response());
		}

		@io.jpostman.annotations.JPostman.Response(request = "Get current auth user", verify = 200)
		void user(io.jpostman.annotations.JPostman.Test ctx) {
			assertNotNull(ctx.request());
			assertNotNull(ctx.response());
		}
	}

	public static final class CoverageApplyExecutor {
		private static int applyCount;

		public static ApiExecutor apply(Object request) {
			applyCount++;
			assertNotNull(request);
			return okExecutor("{\"id\":1,\"firstName\":\"John\"}");
		}

		private static void reset() {
			applyCount = 0;
		}
	}

	public static final class CoverageSessionExecutor implements ApiExecutor {
		private static int createCount;
		private static int setRequestCount;
		private static int responseCount;

		private Object request;

		public static CoverageSessionExecutor create() {
			createCount++;
			return new CoverageSessionExecutor();
		}

		public CoverageSessionExecutor setRequest(Object request) {
			setRequestCount++;
			this.request = request;
			return this;
		}

		@Override
		public ApiResponse response() {
			responseCount++;
			assertNotNull(request);
			return okResponse("{\"id\":1,\"firstName\":\"John\"}");
		}

		private static void reset() {
			createCount = 0;
			setRequestCount = 0;
			responseCount = 0;
		}
	}

	private static final class RequestFixture {

		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		/** Default namespace context injected by the annotation runner. */
		@JPostmanTestContext
		private JUnitContext base;

		private int loginCount;
		private int executorCount;
		private String executorMethodName;

		/**
		 * Dependency request method. The return value is cached under accessToken.
		 */
		@JPostmanRequest(request = "Login user and get tokens", rule = "login", cache = "accessToken")
		String login() {
			loginCount++;
			return "token-123";
		}

		/**
		 * Response flow that depends on login and uses the named auth executor.
		 */
		@JPostmanResponse(request = "Get current auth user", rule = "user", dependsOn = {
				"login" }, executor = "#auth", verify = 200)
		void getCurrentAuthUser() {
		}

		/**
		 * Named executor selected by unique id: executor = "#auth".
		 */
		@JPostmanExecutor(id = "auth", dependsOn = { "login" })
		ApiExecutor authExecutor(JUnitContext context, JPostmanInfo info) {
			executorCount++;
			executorMethodName = info.method;

			assertEquals("token-123", context.cache("accessToken"));
			assertNotNull(context.request());

			return okExecutor("{\"id\":1,\"firstName\":\"John\"}");
		}

		/** Request-only method used to cover direct @JPostmanRequest handling. */
		@JPostmanRequest(request = "Login user and get tokens", rule = "login")
		void prepareLoginRequest() {
		}
	}

	/**
	 * Fixture for the product namespace.
	 *
	 * <p>
	 * Product uses collection.product from properties and intentionally has no
	 * environment.product to cover collection-only loading.
	 * </p>
	 */
	private static final class ProductFixture {

		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext(namespace = "product")
		private JUnitContext product;

		@JPostmanRequest(namespace = "product", request = "Login user and get tokens")
		void prepareLoginRequest() {
		}
	}

	private static final class ContextFieldFixture {
		private JUnitContext ctx;
	}

	@SuppressWarnings("unused")
	private static final class AssertionFixture {
		void assertWithRules() {
		}

		void assertMissingRules() {
		}

		void assertInvalidRuleLine() {
		}

		void assertCircularRules() {
		}

		void assertUnsupportedRule() {
		}

		void assertRequestSectionOverridesConfiguredSections() {
		}
	}

	private static final class RunnerFixture {
		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext
		private JUnitContext base;

		private int runnerDependencyCount;
		private int executorCount;
		private String executorMethodName;
		private String executorRequestName;

		@JPostmanRequest(cache = "runnerToken")
		String runnerDependency() {
			runnerDependencyCount++;
			return "runner-token";
		}

		@JPostmanRunner(dependsOn = { "runnerDependency" }, include = { "Login user and get tokens",
				"Get current auth user" }, executor = "#runner", verify = 200)
		void runProducts() {
		}

		@JPostmanResponse(request = "Get current auth user", executor = "#runner")
		void explicitResponseForSkip() {
		}

		@JPostmanExecutor(id = "runner", dependsOn = { "runnerDependency" })
		ApiExecutor runnerExecutor(JUnitContext context, JPostmanInfo info) {
			executorCount++;
			executorMethodName = info.method;
			executorRequestName = info.request;
			assertEquals("runner-token", context.cache("runnerToken"));
			return okExecutor("{\"id\":1}");
		}
	}

	private static final class TestNgRunnerWithoutExecutorFixture {
		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext
		private TestNgContext base;

		@JPostmanRunner(include = { "Login user and get tokens" }, verify = 200)
		void productRunner() {
		}
	}

	private static final class InvalidExecutorValidationFixture {
		@JPostmanExecutor(id = "duplicate")
		ApiExecutor duplicateOne() {
			return okExecutor("{}");
		}

		@JPostmanExecutor(id = "duplicate")
		ApiExecutor duplicateTwo() {
			return okExecutor("{}");
		}

		@JPostmanExecutor
		ApiExecutor defaultOne() {
			return okExecutor("{}");
		}

		@JPostmanExecutor
		ApiExecutor defaultTwo() {
			return okExecutor("{}");
		}

		@JPostmanExecutor(id = "badSignature")
		ApiExecutor badSignature(String unsupported) {
			return okExecutor("{}");
		}

		@JPostmanExecutor(id = "badReturn")
		String badReturn() {
			return "not an executor";
		}
	}

	private static final class InvalidVerifyAndAssertsResponseFixture extends BaseResponseFixture {
		@JPostmanResponse(request = "Get current auth user", verify = 200, asserts = { "product" })
		void response() {
		}
	}

	private static final class InvalidVerifyAndAssertsRunnerFixture extends BaseResponseFixture {
		@JPostmanRunner(include = { "Login user and get tokens" }, verify = 200, asserts = { "product" })
		void runner() {
		}
	}

	private static final class ResponseAndRunnerDependencyFixture {
		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext
		private JUnitContext base;

		private int responseDependencyCount;
		private int runnerDependencyCount;
		private int executorCount;
		private JPostmanInfo responseDependencyInfo;
		private JPostmanInfo runnerDependencyInfo;
		private final List<String> executorMethodNames = new java.util.ArrayList<>();

		@JPostmanResponse(request = "Get current auth user", executor = "#dependency", verify = 200, cache = "responseDependency")
		String responseDependency(JUnitContext context, JPostmanInfo info) {
			responseDependencyCount++;
			responseDependencyInfo = info;
			return "response-cache-value";
		}

		@JPostmanRunner(include = { "Login user and get tokens" }, executor = "#dependency", verify = 200)
		String runnerDependency(JUnitContext context, JPostmanInfo info) {
			runnerDependencyCount++;
			runnerDependencyInfo = info;
			return "runner-cache-value";
		}

		@JPostmanResponse(request = "Get current auth user", dependsOn = { "responseDependency",
				"runnerDependency" }, executor = "#dependency", verify = 200)
		void mainResponse() {
		}

		@JPostmanExecutor(id = "dependency")
		ApiExecutor dependencyExecutor(JUnitContext context, JPostmanInfo info) {
			executorCount++;
			executorMethodNames.add(info.method);
			assertNotNull(context.request());
			assertNotNull(info.request);

			return okExecutor("{\"id\":1,\"firstName\":\"John\"}");
		}
	}

	private static final class TagChainFixture {
		@JPostmanContext(config = "", collection = COLLECTION)
		private io.jpostman.JPostman.Context jctx;

		private String[] mouseTags;
		private String[] shoesTags;
		private String[] computerTags;

		@JPostmanExecutor
		private static ApiExecutor okExecutor() {
			return () -> okResponse("{}");
		}

		@JPostmanResponse(tags = "keyboard", request = "Login user and get tokens", dependsOn = "getMouse")
		void response() {
		}

		@JPostmanRequest(tags = "mouse", dependsOn = "getShoes")
		void getMouse(JPostmanInfo info) {
			mouseTags = info.tags;
		}

		@JPostmanRequest(tags = "shoes", dependsOn = "getComputer")
		void getShoes(JPostmanInfo info) {
			shoesTags = info.tags;
		}

		@JPostmanRequest(tags = "computer")
		void getComputer(JPostmanInfo info) {
			computerTags = info.tags;
		}
	}

	private static final class CircularDependencyFixture extends BaseResponseFixture {
		@JPostmanRequest(dependsOn = { "response" })
		void requestDependency() {
		}

		@JPostmanResponse(request = "Get current auth user", dependsOn = { "requestDependency" })
		void response() {
		}
	}

	private static final class InfoDependencyFixture extends BaseContextFixture {
		private String value;
		private String executorRequestName;

		@JPostmanRequest
		void helloWorld(JUnitContext context, JPostmanInfo info) {
			info.body("hello", "world");
		}

		@JPostmanResponse(request = "Get current auth user", dependsOn = {
				"helloWorld" }, executor = "#info", verify = 200)
		void response() {
		}

		@JPostmanExecutor(id = "info")
		ApiExecutor executor(JUnitContext context, JPostmanInfo info) {
			value = String.valueOf(info.body.get("hello"));
			executorRequestName = info.request;
			return okExecutor("{\"id\":1}");
		}
	}

	private static final class MissingDependencyFixture extends BaseResponseFixture {
		@JPostmanResponse(request = "Get current auth user", dependsOn = { "missingDependency" })
		void response() {
		}
	}

	private static final class PlainDependencyFixture extends BaseResponseFixture {
		@SuppressWarnings("unused")
		void plainDependency() {
		}

		@JPostmanResponse(request = "Get current auth user", dependsOn = { "plainDependency" })
		void response() {
		}
	}

	private static final class NullDependencyFixture extends BaseResponseFixture {
		@JPostmanRequest(request = "Login user and get tokens", cache = "nullDependency")
		String nullDependency() {
			return null;
		}

		@JPostmanResponse(request = "Get current auth user", dependsOn = { "nullDependency" })
		void response() {
		}
	}

	private static final class MissingExecutorFixture extends BaseResponseFixture {
		@JPostmanResponse(request = "Get current auth user", executor = "missing")
		void response() {
		}
	}

	private static final class InvalidExecutorSignatureFixture extends BaseContextFixture {
		@JPostmanResponse(request = "Get current auth user")
		void response() {
		}

		@JPostmanExecutor
		ApiExecutor badExecutor(String value) {
			return okExecutor("{}");
		}
	}

	private static final class LegacyStringExecutorFixture extends BaseContextFixture {
		@JPostmanResponse(request = "Get current auth user", executor = "legacyExecutor")
		void response() {
		}

		@JPostmanExecutor
		ApiExecutor legacyExecutor(JUnitContext context, String methodName) {
			return okExecutor("{}");
		}
	}

	private static final class LegacyRequestNameExecutorFixture extends BaseContextFixture {
		@JPostmanResponse(request = "Get current auth user", executor = "legacyExecutor")
		void response() {
		}

		@JPostmanExecutor
		ApiExecutor legacyExecutor(JUnitContext context, String methodName, String requestName) {
			return okExecutor("{}");
		}
	}

	private static final class NullExecutorFixture extends BaseContextFixture {
		@JPostmanResponse(request = "Get current auth user")
		void response() {
		}

		@JPostmanExecutor
		ApiExecutor defaultExecutor(JUnitContext context) {
			return null;
		}
	}

	private static final class WrongExecutorTypeFixture extends BaseContextFixture {
		@JPostmanResponse(request = "Get current auth user")
		void response() {
		}

		@JPostmanExecutor
		Object defaultExecutor(JUnitContext context) {
			return "not an executor";
		}
	}

	private static class BaseResponseFixture extends BaseContextFixture {
		@JPostmanExecutor
		ApiExecutor defaultExecutor(JUnitContext context) {
			return okExecutor("{\"id\":1}");
		}
	}

	private static class BaseContextFixture {
		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext
		private JUnitContext base;
	}

	private static final class CoreContextFixture {
		@JPostmanTestContext
		private JUnitContext base;

		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;
	}

	private static final class InvalidCoreContextFixture {
		@JPostmanContext
		private String loaded;
	}

	private static final class CollectionRequiredFixture {
		@JPostmanContext(config = "")
		private io.jpostman.JPostman.Context loaded;
	}

	private static final class MissingConfigFixture {
		@JPostmanContext(config = "classpath:missing-jpostman.properties")
		private io.jpostman.JPostman.Context loaded;
	}

	private static final class TestNgContextFixture {
		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext
		private TestNgContext base;
	}

	private static final class ListenerTestNgFixture {
		@JPostmanContext
		private io.jpostman.JPostman.Context loaded;

		@JPostmanTestContext
		private TestNgContext base;

		private int executorCount;

		@SuppressWarnings("unused")
		void beforeClass() {
		}

		@org.testng.annotations.Test
		@JPostmanResponse(request = "Get current auth user", executor = "#listener", verify = 200)
		void validTest() {
		}

		@JPostmanExecutor(id = "listener")
		ApiExecutor listenerExecutor(TestNgContext context) {
			executorCount++;
			assertNotNull(context.request());
			return okExecutor("{\"id\":1}");
		}
	}

	private static final class InvalidHelperFixture {
		@Test
		@JPostmanRequest(request = "Login user and get tokens")
		void invalidRequest() {
		}

		@Test
		@JPostmanExecutor
		ApiExecutor invalidExecutor(JUnitContext context) {
			return okExecutor("{}");
		}

		@Test
		@JPostmanResponse(request = "Get current auth user", cache = "user")
		void invalidCachedResponse() {
		}

		@JPostmanRequest(request = "Login user and get tokens", cache = "token")
		void invalidCachedRequest() {
		}
	}

	@JPostmanTestNgAnnotations
	private static final class AnnotatedTestNgFixture {
	}

	/** Fixture with no JPostman annotations. */
	private static final class EmptyFixture {
		@SuppressWarnings("unused")
		void plain() {
		}
	}

	private static final class CompactJPostmanRuntimeAliasFixture {
		@io.jpostman.annotations.JPostman.Context(config = "", collection = COLLECTION)
		private io.jpostman.annotations.JPostman.Runtime<JUnitContext> jctx;
	}

	@Test
	public void contextRunnerInjectsCompactJPostmanFacadeRuntime() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		CompactJPostmanRuntimeAliasFixture fixture = new CompactJPostmanRuntimeAliasFixture();

		runner.setup(fixture);

		assertNotNull(fixture.jctx);
		assertNotNull(fixture.jctx.context());
		assertNotNull(fixture.jctx.getCollection());
	}

	@io.jpostman.annotations.JPostman.JUnit(printFailures = true)
	private static final class CompactJPostmanJUnitAnnotationFixture {
		@io.jpostman.annotations.JPostman.Context(config = "", collection = COLLECTION)
		private io.jpostman.annotations.JPostman.Runtime<JUnitContext> jctx;
	}

	@Test
	public void compactJPostmanJUnitAnnotationExposesPrintFailures() {
		io.jpostman.annotations.JPostman.JUnit annotation = CompactJPostmanJUnitAnnotationFixture.class
				.getAnnotation(io.jpostman.annotations.JPostman.JUnit.class);

		assertNotNull(annotation);
		assertTrue(annotation.printFailures());
	}

}
