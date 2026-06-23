package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
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
import io.jpostman.annotations.JPostmanAnnotationEngine;
import io.jpostman.annotations.JPostmanAssert;
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

		assertEquals("No @JPostmanTestContext found for namespace: missing", error.getMessage());
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
		assertEquals("getCurrentAuthUser", fixture.executorMethodName);
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

		assertEquals("No @JPostmanTestContext found.", error.getMessage());
	}

	/**
	 * Verifies JPostmanResourceLoader property and resource helpers.
	 */
	@Test
	public void resourceLoaderCoversPropertyOpenAndFallbackHelpers() throws Exception {
		Properties properties = JPostmanResourceLoader.loadProperties("classpath:jpostman.properties",
				JPostmanAnnotationCoverageTest.class);

		assertNotNull(JPostmanResourceLoader.open("classpath:annotation-test-collection.json",
				JPostmanAnnotationCoverageTest.class));
		assertEquals("collection.product", JPostmanResourceLoader.propertyKey("collection", "product"));
		assertEquals("rules", JPostmanResourceLoader.propertyKey("rules", ""));
		assertEquals("classpath:annotation-test-collection.json",
				JPostmanResourceLoader.property(properties, "collection", ""));
		assertEquals("classpath:annotation-test-collection.json",
				JPostmanResourceLoader.property(properties, "collection", "product"));
		assertEquals("annotation", JPostmanResourceLoader.firstNonBlank("annotation", "fallback"));
		assertEquals("fallback", JPostmanResourceLoader.firstNonBlank(" ", "fallback"));
		assertEquals("", JPostmanResourceLoader.firstNonBlank(null, null));

		assertThrows(FileNotFoundException.class,
				() -> JPostmanResourceLoader.open("missing-resource.json", JPostmanAnnotationCoverageTest.class));
	}

	/**
	 * Verifies annotation validation success and failure paths.
	 */
	@Test
	public void annotationValidatorAcceptsValidClassAndRejectsInvalidTestHelpers() throws Exception {
		assertDoesNotThrow(() -> JPostmanAnnotationValidator.validateTestClass(RequestFixture.class));

		AssertionError error = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationValidator.validateTestClass(InvalidHelperFixture.class));

		assertTrue(error.getMessage().contains("Invalid JPostman annotation usage"));
		assertTrue(error.getMessage().contains("InvalidHelperFixture.invalidRequest"));
		assertTrue(error.getMessage().contains("InvalidHelperFixture.invalidExecutor"));
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
		framework.response(context, okExecutor("{\"id\":1,\"firstName\":\"John\",\"active\":true}"));

		JPostmanAssert annotation = AssertionFixture.class.getDeclaredMethod("assertWithRules")
				.getAnnotation(JPostmanAssert.class);

		assertDoesNotThrow(() -> new JPostmanAssertionRunner<>(framework).apply(AssertionFixture.class, context,
				annotation, "Get current auth user", false, false));
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

		JPostmanAssert missing = AssertionFixture.class.getDeclaredMethod("assertMissingRules")
				.getAnnotation(JPostmanAssert.class);
		IllegalStateException missingError = assertThrows(IllegalStateException.class,
				() -> runner.apply(AssertionFixture.class, context, missing, "Get current auth user", false, false));
		assertTrue(missingError.getMessage().contains("JPostman assertion rules are required"));

		JPostmanAssert invalid = AssertionFixture.class.getDeclaredMethod("assertInvalidRuleLine")
				.getAnnotation(JPostmanAssert.class);
		IllegalStateException invalidLine = assertThrows(IllegalStateException.class,
				() -> runner.apply(AssertionFixture.class, context, invalid, "Get current auth user", false, false));
		assertTrue(invalidLine.getMessage().contains("Invalid assertion rule line"));

		JPostmanAssert circular = AssertionFixture.class.getDeclaredMethod("assertCircularRules")
				.getAnnotation(JPostmanAssert.class);
		IllegalStateException circularError = assertThrows(IllegalStateException.class,
				() -> runner.apply(AssertionFixture.class, context, circular, "Get current auth user", false, false));
		assertTrue(circularError.getMessage().contains("Circular assertion rule inheritance"));

		JPostmanAssert unsupported = AssertionFixture.class.getDeclaredMethod("assertUnsupportedRule")
				.getAnnotation(JPostmanAssert.class);
		IllegalStateException unsupportedError = assertThrows(IllegalStateException.class, () -> runner
				.apply(AssertionFixture.class, context, unsupported, "Get current auth user", false, false));
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
		assertEquals("runProducts", fixture.executorMethodName);
		assertEquals("Login user and get tokens", fixture.executorRequestName);
		assertEquals("runner-token", fixture.base.cache("runnerToken"));
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
		assertEquals("response-cache-value", fixture.base.cache("responseDependencyCache"));
		assertEquals("runner-cache-value", fixture.base.cache("runnerDependencyCache"));
		assertTrue(fixture.executorMethodNames.contains("responseDependency"));
		assertTrue(fixture.executorMethodNames.contains("runnerDependency"));
		assertTrue(fixture.executorMethodNames.contains("mainResponse"));
	}

	/**
	 * Verifies dependency failure branches in JPostmanAnnotationRunner.
	 */
	@Test
	public void runnerReportsDependencyAndExecutorErrors() throws Exception {
		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());

		IllegalStateException missingDependency = assertThrows(IllegalStateException.class, () -> runner
				.run(new MissingDependencyFixture(), MissingDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(missingDependency.getMessage().contains("Dependency method not found"));

		IllegalStateException plainDependency = assertThrows(IllegalStateException.class, () -> runner
				.run(new PlainDependencyFixture(), PlainDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(plainDependency.getMessage().contains("Dependency method must be annotated"));

		IllegalStateException nullDependency = assertThrows(IllegalStateException.class, () -> runner
				.run(new NullDependencyFixture(), NullDependencyFixture.class.getDeclaredMethod("response")));
		assertTrue(nullDependency.getMessage().contains("Dependency method returned null"));

		IllegalStateException missingExecutor = assertThrows(IllegalStateException.class, () -> runner
				.run(new MissingExecutorFixture(), MissingExecutorFixture.class.getDeclaredMethod("response")));
		assertTrue(missingExecutor.getMessage().contains("JPostman executor not found"));

		IllegalStateException invalidExecutor = assertThrows(IllegalStateException.class,
				() -> runner.run(new InvalidExecutorSignatureFixture(),
						InvalidExecutorSignatureFixture.class.getDeclaredMethod("response")));
		assertTrue(invalidExecutor.getMessage().contains("@JPostmanExecutor method must accept"));

		IllegalStateException nullExecutor = assertThrows(IllegalStateException.class,
				() -> runner.run(new NullExecutorFixture(), NullExecutorFixture.class.getDeclaredMethod("response")));
		assertTrue(nullExecutor.getMessage().contains("JPostman executor returned null"));

		IllegalStateException wrongExecutorType = assertThrows(IllegalStateException.class, () -> runner
				.run(new WrongExecutorTypeFixture(), WrongExecutorTypeFixture.class.getDeclaredMethod("response")));
		assertTrue(wrongExecutorType.getMessage().contains("JPostman executor must return ApiExecutor"));
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
		assertTrue(error.getMessage().contains("@JPostmanContext field must be JPostman.Context"));
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

		int plainSourceLine = JPostmanStackTraceCleaner.findSourceLine(EmptyFixture.class, "plain");
		assertTrue(plainSourceLine == -1 || plainSourceLine > 0);
		assertEquals(-1, JPostmanStackTraceCleaner.findSourceLine(EmptyFixture.class, "missingMethod"));
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
		assertDoesNotThrow(
				() -> listener.beforeInvocation(invokedMethod(validTest, true, false), testResult(lifecycle)));
		assertEquals(1, lifecycle.executorCount);
		assertNotNull(TestNgContext.current().response());
		TestNgContext.clearCurrent();

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
	 * Verifies the private listener cleanFailure helper through reflection.
	 */
	@Test
	public void testNgListenerCleanFailureIsCovered() throws Exception {
		Method plain = EmptyFixture.class.getDeclaredMethod("plain");
		IInvokedMethod invoked = invokedMethod(plain, true, false);

		Method cleanFailure = JPostmanTestNgAnnotationListener.class.getDeclaredMethod("cleanFailure",
				IInvokedMethod.class, Throwable.class);
		cleanFailure.setAccessible(true);

		AssertionError cleaned = (AssertionError) cleanFailure.invoke(null, invoked,
				new InvocationTargetException(new AssertionError("listener boom")));

		assertTrue(cleaned.getMessage().contains("listener boom"));
		assertTrue(cleaned.getStackTrace().length > 0);
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
		return testResult(instance, new AtomicReference<>(), ITestResult.SUCCESS);
	}

	private static ITestResult testResult(Object instance, AtomicReference<Throwable> throwable, int status) {
		return (ITestResult) Proxy.newProxyInstance(JPostmanAnnotationCoverageTest.class.getClassLoader(),
				new Class<?>[] { ITestResult.class }, (proxy, method, args) -> {
					if ("getInstance".equals(method.getName())) {
						return instance;
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
	private static final class RequestFixture {

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
				"login" }, executor = "auth", verify = 200)
		void getCurrentAuthUser() {
		}

		/**
		 * Named executor selected by executor = "auth".
		 */
		@JPostmanExecutor(name = "auth", dependsOn = { "login" })
		ApiExecutor authExecutor(JUnitContext context, String methodName) {
			executorCount++;
			executorMethodName = methodName;

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

		@JPostmanTestContext(namespace = "product")
		private JUnitContext product;

		@JPostmanRequest(namespace = "product", request = "Login user and get tokens")
		void prepareLoginRequest() {
		}
	}

	private static final class ContextFieldFixture {
		private JUnitContext ctx;
	}

	private static final class AssertionFixture {
		@JPostmanAssert(rules = "classpath:annotation-assertions.ini", sections = { "base" })
		void assertWithRules() {
		}

		@JPostmanAssert
		void assertMissingRules() {
		}

		@JPostmanAssert(rules = "classpath:annotation-assertions-invalid-line.ini")
		void assertInvalidRuleLine() {
		}

		@JPostmanAssert(rules = "classpath:annotation-assertions-circular.ini", sections = { "a" })
		void assertCircularRules() {
		}

		@JPostmanAssert(rules = "classpath:annotation-assertions-unsupported.ini")
		void assertUnsupportedRule() {
		}
	}

	private static final class RunnerFixture {
		@JPostmanTestContext
		private JUnitContext base;

		private int runnerDependencyCount;
		private int executorCount;
		private String executorMethodName;
		private String executorRequestName;

		@JPostmanRequest(request = "Login user and get tokens", cache = "runnerToken")
		String runnerDependency() {
			runnerDependencyCount++;
			return "runner-token";
		}

		@JPostmanRunner(dependsOn = { "runnerDependency" }, include = { "Login user and get tokens",
				"Get current auth user" }, executor = "runner", verify = 200)
		void runProducts() {
		}

		@JPostmanResponse(request = "Get current auth user", executor = "runner")
		void explicitResponseForSkip() {
		}

		@JPostmanExecutor(name = "runner", dependsOn = { "runnerDependency" })
		ApiExecutor runnerExecutor(JUnitContext context, String methodName, String requestName) {
			executorCount++;
			executorMethodName = methodName;
			executorRequestName = requestName;
			assertEquals("runner-token", context.cache("runnerToken"));
			return okExecutor("{\"id\":1}");
		}
	}

	private static final class ResponseAndRunnerDependencyFixture {
		@JPostmanTestContext
		private JUnitContext base;

		private int responseDependencyCount;
		private int runnerDependencyCount;
		private int executorCount;
		private final List<String> executorMethodNames = new java.util.ArrayList<>();

		@JPostmanResponse(request = "Get current auth user", executor = "dependency", cache = "responseDependencyCache", verify = 200)
		String responseDependency() {
			responseDependencyCount++;
			return "response-cache-value";
		}

		@JPostmanRunner(include = {
				"Login user and get tokens" }, executor = "dependency", cache = "runnerDependencyCache", verify = 200)
		String runnerDependency() {
			runnerDependencyCount++;
			return "runner-cache-value";
		}

		@JPostmanResponse(request = "Get current auth user", dependsOn = { "responseDependency",
				"runnerDependency" }, executor = "dependency", verify = 200)
		void mainResponse() {
		}

		@JPostmanExecutor(name = "dependency")
		ApiExecutor dependencyExecutor(JUnitContext context, String methodName, String requestName) {
			executorCount++;
			executorMethodNames.add(methodName);
			assertNotNull(context.request());
			assertNotNull(requestName);

			return okExecutor("{\"id\":1,\"firstName\":\"John\"}");
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
		@JPostmanRequest(request = "Login user and get tokens")
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

	private static final class TestNgContextFixture {
		@JPostmanTestContext
		private TestNgContext base;
	}

	private static final class ListenerTestNgFixture {
		@JPostmanTestContext
		private TestNgContext base;

		private int executorCount;

		@SuppressWarnings("unused")
		void beforeClass() {
		}

		@JPostmanResponse(request = "Get current auth user", executor = "listener", verify = 200)
		void validTest() {
		}

		@JPostmanExecutor(name = "listener")
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
}
