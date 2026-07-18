package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JPostmanReportRunnerRequestRegressionTest {

	@Test
	void recordsEveryConcreteRequestFromTheSameRunnerMethod() {
		JPostmanReport report = new JPostmanReport();
		JPostmanInfo runner = new JPostmanInfo("testAuthRunner", "", "Auth", "").annotation("@JPostmanRunner");

		report.passed(runner.runnerRequest("Login user and get access/refresh tokens"));
		report.passed(runner.runnerRequest("Get current authenticated user"));
		report.passed(runner.runnerRequest("Refresh auth session/token"));

		assertEquals(3, report.total());
		assertEquals(3, report.passed.size());
	}

	@Test
	void updatesOnlyTheMatchingRunnerRequestStatus() {
		JPostmanReport report = new JPostmanReport();
		JPostmanInfo runner = new JPostmanInfo("testAuthRunner", "", "Auth", "").annotation("@JPostmanRunner");
		JPostmanInfo login = runner.runnerRequest("Login");
		JPostmanInfo current = runner.runnerRequest("Current user");

		report.passed(login);
		report.passed(current);
		report.failed(login);

		assertEquals(2, report.total());
		assertEquals(1, report.passed.size());
		assertEquals(1, report.failed.size());
		assertEquals("Current user", report.passed.get(0).request);
		assertEquals("Login", report.failed.get(0).request);
	}

	@Test
	void recordsParentRunnerWhenExplicitResponsesOwnEveryScopedRequest() {
		JPostmanReport report = new JPostmanReport();
		JPostmanInfo runner = new JPostmanInfo("testAuthRunner", "", "Auth", "").annotation("@JPostmanRunner");

		report.passed(runner);

		assertEquals(1, report.total());
		assertEquals("testAuthRunner", report.passed.get(0).method);
		assertEquals("", report.passed.get(0).request);
	}
}
