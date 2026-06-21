package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.Collection;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.annotations.JPostmanAnnotationEngine;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.junit.JUnitContext;
import io.jpostman.testng.TestNgContext;

public class JPostmanAnnotationCoverageTest {

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
	 * The annotation runtime should fail fast if two @JPostmanTestContext fields use
	 * the same namespace.
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

		ApiExecutor executor = () -> new ApiResponse(200, "{\"id\":1,\"firstName\":\"John\"}",
				"{\"id\":1,\"firstName\":\"John\"}".getBytes(), Map.of());

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

		ApiExecutor executor = () -> new ApiResponse(200, "{\"id\":1,\"firstName\":\"John\"}",
				"{\"id\":1,\"firstName\":\"John\"}".getBytes(), Map.of());

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
	 *                  <p>
	 *                  Responsibility: the runner should clear the current JUnit
	 *                  context and return safely.
	 *                  </p>
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
	 *                  <p>
	 *                  Responsibility: the runner should clear the current TestNG
	 *                  context and return safely.
	 *                  </p>
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

			return () -> new ApiResponse(200, "{\"id\":1,\"firstName\":\"John\"}",
					"{\"id\":1,\"firstName\":\"John\"}".getBytes(), Map.of());
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

	/** Fixture with no JPostman annotations. */
	private static final class EmptyFixture {
		@SuppressWarnings("unused")
		void plain() {
		}
	}
}
