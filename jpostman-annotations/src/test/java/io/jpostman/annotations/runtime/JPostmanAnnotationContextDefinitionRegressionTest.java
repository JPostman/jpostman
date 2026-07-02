package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.testng.TestNgContext;

/**
 * Regression coverage for app-level @JPostman.Context definition rules.
 */
public class JPostmanAnnotationContextDefinitionRegressionTest {

	private static final String COLLECTION = "classpath:annotation-test-collection.json";

	@Test
	public void onlyOneAppContextFieldIsAllowed() {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> JPostmanAnnotationEngine.setupTestNg(new MultipleAppContextFixture()));

		assertTrue(error.getMessage().contains("Only one @JPostman.Context/@JPostmanContext field is allowed"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("@JPostman.TestContext(namespace = \"...\")"),
				"Actual message: " + error.getMessage());
	}

	@Test
	public void legacyAppContextWithoutNamespaceIsAllowed() {
		LegacyAppContextFixture fixture = new LegacyAppContextFixture();

		assertDoesNotThrow(() -> JPostmanAnnotationEngine.setupTestNg(fixture));

		assertNotNull(fixture.jpostman);
	}

	@Test
	public void singleAppContextCanExposeDefaultAndNamespaceTestContexts() {
		SingleAppContextWithNamespaceMirrorsFixture fixture = new SingleAppContextWithNamespaceMirrorsFixture();

		assertDoesNotThrow(() -> JPostmanAnnotationEngine.setupTestNg(fixture));

		assertNotNull(fixture.jpostman);
		assertNotNull(fixture.api);
		assertNotNull(fixture.product);
		assertNotNull(fixture.jpostman.ctx());
		assertNotNull(fixture.jpostman.ctx("product"));
	}

	private static final class MultipleAppContextFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> product;
	}

	private static final class LegacyAppContextFixture {
		@JPostmanContext(config = "", collection = COLLECTION)
		private JPostmanRuntime<TestNgContext> jpostman;
	}

	private static final class SingleAppContextWithNamespaceMirrorsFixture {
		@JPostman.Context(config = "", collection = COLLECTION)
		private JPostman.Runtime<JPostman.Test> jpostman;

		@JPostman.TestContext(active = false)
		private TestNgContext api;

		@JPostman.TestContext(namespace = "product")
		private TestNgContext product;
	}
}
