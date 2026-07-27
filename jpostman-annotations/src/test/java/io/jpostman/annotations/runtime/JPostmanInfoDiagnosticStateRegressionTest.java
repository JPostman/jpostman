package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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
