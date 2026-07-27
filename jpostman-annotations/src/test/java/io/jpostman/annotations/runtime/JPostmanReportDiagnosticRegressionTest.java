package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JPostmanReportDiagnosticRegressionTest {

	@Test
	void durationUsesClassLifecycleWallClockInsteadOfSummedRequestDurations() throws Exception {
		JPostmanReport report = new JPostmanReport();
		Thread.sleep(2L);
		assertTrue(report.duration() >= 1L);
	}

	@Test
	void skipAllStartsAfterFirstFailure() {
		JPostmanReport report = new JPostmanReport().configure("short", "skip all");
		assertFalse(report.skipRemaining());
		report.failed(topLevel("failedMethod"));
		assertTrue(report.skipRemaining());
	}

	@Test
	void invalidOptionsFailFast() {
		JPostmanReport report = new JPostmanReport();
		assertThrows(IllegalArgumentException.class, () -> report.configure("verbose", "ignore"));
		assertThrows(IllegalArgumentException.class, () -> report.configure("short", "stop"));
	}

	@Test
	void statusCountersStillReplaceTheSameExecution() {
		JPostmanReport report = new JPostmanReport().configure("fail", "ignore");
		JPostmanInfo info = topLevel("method");
		report.passed(info);
		report.failed(info);
		assertEquals(0, report.passed.size());
		assertEquals(1, report.failed.size());
	}

	private JPostmanInfo topLevel(String method) {
		return new JPostmanInfo(new String[0], "", method, "", "", "").method(method);
	}
}
