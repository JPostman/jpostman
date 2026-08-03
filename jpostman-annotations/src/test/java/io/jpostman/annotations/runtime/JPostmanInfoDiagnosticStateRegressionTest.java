package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JPostmanInfoDiagnosticStateRegressionTest {

	@Test
	void childResolvedRequestLogIsVisibleFromTopLevelInfo() {
		JPostmanInfo top = new JPostmanInfo(new String[] { "mouse" }, "", "newMouseProduct", "product", "Product",
				"Add a new product");
		JPostmanInfo child = top.child("defaultExecutor", "", "", "");

		child.requestLog("resolved request with body");

		assertEquals("resolved request with body", top.requestLog());
	}

	@Test
	void copiedInfoDeduplicatesTheSameLogicalDebugOutput() {
		JPostmanInfo response = new JPostmanInfo("@JPostmanResponse", "response", "product", "Product",
				"Add a new product");
		JPostmanInfo copy = response.withTags("keyboard");
		JPostmanInfo executor = response.child("executor", "product", "Product", "Add a new product");

		assertTrue(response.markDebugOutputEmitted());
		assertFalse(copy.markDebugOutputEmitted(),
				"copies of the same annotation step must not print a second completed debug block");
		assertTrue(executor.markDebugOutputEmitted(),
				"a distinct executor step in the same diagnostic chain must still print");
	}

	@Test
	void runnerRequestsKeepIndependentDiagnosticState() {
		JPostmanInfo runner = new JPostmanInfo(new String[0], "", "runRunner", "product", "Product", "");
		JPostmanInfo first = runner.runnerRequest("First");
		JPostmanInfo second = runner.runnerRequest("Second");

		first.requestLog("first request");
		second.requestLog("second request");

		assertNotSame(first, second);
		assertEquals("first request", first.requestLog());
		assertEquals("second request", second.requestLog());
	}
}
