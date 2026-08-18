package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostmanContext;

class JPostmanGlobalErrorOptionRemovalRegressionTest {

	@Test
	void contextDebugRejectsError() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> JPostmanRuntimeOptions.DebugMode.validateContext("error"));
		assertTrue(error.getMessage().contains("Supported values: none, request, response, info, all."),
				error.getMessage());
	}

	@Test
	void contextOptionResolutionRejectsError() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> JPostmanRuntimeOptions.from(new GlobalErrorFixture()));
		assertTrue(error.getMessage().contains("Supported values: none, request, response, info, all."),
				error.getMessage());
	}

	@Test
	void reportContextFailAcceptsError() {
		assertDoesNotThrow(() -> new JPostmanReport().configure("error"));
		assertDoesNotThrow(() -> new JPostmanReport().configure("response", "error"));
	}

	@Test
	void methodLevelDebugStillAcceptsError() {
		assertDoesNotThrow(() -> JPostmanRuntimeOptions.DebugMode.validateLocal("error"));
	}

	private static final class GlobalErrorFixture {
		@JPostmanContext(config = "", debug = "error")
		Object runtime;
	}

}
