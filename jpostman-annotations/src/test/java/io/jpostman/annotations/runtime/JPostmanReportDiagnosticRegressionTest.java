package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
import io.jpostman.annotations.JPostmanReportContext;
import io.jpostman.junit.JUnitContext;

class JPostmanReportDiagnosticRegressionTest {

	@Test
	void reportAnnotationsExposeDiagnosticAndComposableFailValues() throws Exception {
		assertEquals("none", JPostman.ReportContext.class.getMethod("diagnostic").getDefaultValue());
		assertEquals("none", JPostmanReportContext.class.getMethod("diagnostic").getDefaultValue());
		assertArrayEquals(new String[] { "ignore" },
				(String[]) JPostman.ReportContext.class.getMethod("fail").getDefaultValue());
		assertArrayEquals(new String[] { "ignore" },
				(String[]) JPostmanReportContext.class.getMethod("fail").getDefaultValue());
	}

	@Test
	void durationUsesClassLifecycleWallClockInsteadOfSummedRequestDurations() throws Exception {
		JPostmanReport report = new JPostmanReport();
		Thread.sleep(2L);
		assertTrue(report.duration() >= 1L);
	}

	@Test
	void skipAllStartsAfterFirstFailure() {
		JPostmanReport report = new JPostmanReport().configure("skipAll");
		assertFalse(report.skipRemaining());
		capture(() -> report.failed(topLevel("failedMethod")));
		assertTrue(report.skipRemaining());
	}

	@Test
	void skipAllCountsFrameworkSkippedMethodsThatNeverStarted() throws Exception {
		SkipAllFixture fixture = new SkipAllFixture();
		JPostmanReport report = (JPostmanReport) fixture.report;
		report.configure("skipAll", "error");
		report.failed(requestInfo());

		JPostmanAnnotationRunner<JUnitContext> runner = new JPostmanAnnotationRunner<>(new JUnitPostmanFramework());
		runner.recordFinalSkip(fixture, SkipAllFixture.class.getDeclaredMethod("assertsInternalSoft"));
		runner.recordFinalSkip(fixture, SkipAllFixture.class.getDeclaredMethod("softAsserts"));
		runner.recordFinalSkip(fixture, SkipAllFixture.class.getDeclaredMethod("assertsInternalSoftVerify"));

		assertEquals(4, report.total());
		assertEquals(1, report.failed.size());
		assertEquals(3, report.skipped.size());
		assertTrue(report.log().contains("Total tests run: 4, Passes: 0, Failures: 1, Skips: 3"), report.log());
		for (JPostmanInfo skipped : report.skipped) {
			assertEquals("product", skipped.namespace);
			assertEquals("Product", skipped.folder);
			assertEquals("Add a new product", skipped.request);
		}
	}

	@Test
	void invalidOptionsFailFast() {
		JPostmanReport report = new JPostmanReport();
		assertThrows(IllegalArgumentException.class, () -> report.configure("full", new String[] { "ignore" }));
		assertThrows(IllegalArgumentException.class, () -> report.configure("verbose"));
		assertThrows(IllegalArgumentException.class, () -> report.configure("ignore", "skipAll"));
		assertThrows(IllegalArgumentException.class, () -> report.configure("all", "request"));
	}

	@Test
	void statusCountersStillReplaceTheSameExecution() {
		JPostmanReport report = new JPostmanReport().configure("ignore");
		JPostmanInfo info = topLevel("method");
		report.passed(info);
		capture(() -> report.failed(info));
		assertEquals(0, report.passed.size());
		assertEquals(1, report.failed.size());
	}

	@Test
	void diagnosticNonePrintsOnlySummary() {
		JPostmanReport report = new JPostmanReport().configure("none", new String[] { "ignore" });
		report.passed(requestInfo().requestLog("PREPARED REQUEST"));

		String output = capture(report::summary).get(0);

		assertTrue(output.contains("JPostman report"), output);
		assertFalse(output.contains("JPostman diagnostics"), output);
		assertFalse(output.contains("PREPARED REQUEST"), output);
	}

	@Test
	void diagnosticShortPrintsExecutionLineWithoutRequest() {
		JPostmanReport report = new JPostmanReport().configure("short", new String[] { "ignore" });
		report.passed(requestInfo().requestLog("PREPARED REQUEST"));

		String output = capture(report::summary).get(0);

		assertTrue(output.contains("JPostman diagnostics"), output);
		assertTrue(output.contains(
				"assertsVerify:  {namespace = product, folder = Product, request = Add a new product}, statusCode=201, duration="),
				output);
		assertTrue(output.contains("(assertsVerify -> newProduct)"), output);
		assertFalse(output.contains("PREPARED REQUEST"), output);
	}

	@Test
	void diagnosticShortMarksSkippedExecutionWithoutZeroDuration() {
		JPostmanReport report = new JPostmanReport().configure("short", new String[] { "ignore" });
		JPostmanInfo skipped = new JPostmanInfo(new String[0], "", "addNewproduct", "product", "Product",
				"Add a new product");
		skipped.annotation = "@JPostmanResponse";
		skipped.method("addNewproduct");
		skipped.requestLog("REQUEST MUST NOT PRINT FOR A SKIPPED EXECUTION");
		report.skipped(skipped);

		String output = capture(report::summary).get(0);
		String expected = "addNewproduct:  {namespace = product, folder = Product, request = Add a new product}, SKIPPED";

		assertTrue(output.contains("Total tests run: 1, Passes: 0, Failures: 0, Skips: 1"), output);
		assertTrue(output.contains(expected), output);
		assertFalse(output.contains(
				"addNewproduct:  {namespace = product, folder = Product, request = Add a new product}, duration="),
				output);
		assertFalse(output.contains("REQUEST MUST NOT PRINT FOR A SKIPPED EXECUTION"), output);
	}

	@Test
	void diagnosticExtendPrintsShortLineAndPreparedRequest() {
		JPostmanReport report = new JPostmanReport().configure("extend", new String[] { "ignore" });
		report.passed(requestInfo().requestLog("PREPARED REQUEST"));

		String output = capture(report::summary).get(0);

		assertTrue(output.contains("JPostman diagnostics"), output);
		assertTrue(output.contains(
				"assertsVerify:  {namespace = product, folder = Product, request = Add a new product}, statusCode=201, duration="),
				output);
		assertTrue(output.contains("PREPARED REQUEST"), output);
	}

	@Test
	void defaultIgnoreSuppressesAutomaticFailureDetails() {
		JPostmanInfo info = requestInfo();
		info.requestLog("PREPARED REQUEST").responseLog("RECEIVED RESPONSE");
		JPostmanReport report = new JPostmanReport();

		assertTrue(capture(() -> report.failed(info)).isEmpty());
		String text = capture(report::summary).get(0);

		assertTrue(text.contains("JPostman report"), text);
		assertFalse(text.contains("JPostman failures"), text);
		assertFalse(text.contains("JPostman diagnostics"), text);
		assertFalse(text.contains("assertsVerify:"), text);
		assertFalse(text.contains("PREPARED REQUEST"), text);
		assertFalse(text.contains("RECEIVED RESPONSE"), text);
	}

	@Test
	void failureOptionsAppendSelectedDiagnosticsAfterReportSummary() {
		JPostmanInfo info = requestInfo();
		info.requestLog("PREPARED REQUEST").responseLog("RECEIVED RESPONSE");

		JPostmanReport requestResponseReport = new JPostmanReport().configure("request", "response");
		assertTrue(capture(() -> requestResponseReport.failed(info)).isEmpty());
		String requestResponse = capture(requestResponseReport::summary).get(0);
		assertTrue(requestResponse.indexOf("JPostman report") < requestResponse.indexOf("PREPARED REQUEST"),
				requestResponse);
		assertTrue(requestResponse.contains("RECEIVED RESPONSE"), requestResponse);

		JPostmanReport infoReport = new JPostmanReport().configure("info");
		infoReport.failed(info);
		String infoOnly = capture(infoReport::summary).get(0);
		assertTrue(infoOnly.contains("JPostmanInfo {"), infoOnly);
		assertFalse(infoOnly.contains("PREPARED REQUEST"), infoOnly);
		assertFalse(infoOnly.contains("RECEIVED RESPONSE"), infoOnly);

		JPostmanReport allReport = new JPostmanReport().configure("skipAll", "all");
		allReport.failed(info);
		String all = capture(allReport::summary).get(0);
		assertTrue(all.contains("JPostmanInfo {"), all);
		assertTrue(all.contains("PREPARED REQUEST"), all);
		assertTrue(all.contains("RECEIVED RESPONSE"), all);
	}

	@Test
	void diagnosticShortDoesNotDuplicateFailedExecutionBeforeOrAfterSummary() {
		JPostmanReport report = new JPostmanReport().configure("short", new String[] { "ignore" });
		JPostmanInfo info = requestInfo();

		assertTrue(capture(() -> report.failed(info)).isEmpty());
		String output = capture(report::summary).get(0);
		String line = "assertsVerify:  {namespace = product, folder = Product, request = Add a new product}, statusCode=201, duration=";

		assertTrue(output.indexOf("JPostman report") < output.indexOf("JPostman diagnostics"), output);
		assertEquals(output.indexOf(line), output.lastIndexOf(line), output);
	}

	@Test
	void errorOptionPrintsNamedFullFailureAfterTheReport() {
		JPostmanReport report = new JPostmanReport().configure("skipAll", "error");
		AssertionError failure = ErrorFixture.failure();

		assertTrue(capture(() -> report.failed(requestInfo(), failure)).isEmpty());
		String output = capture(report::summary).get(0);

		assertTrue(output.indexOf("JPostman report") < output.indexOf("JPostman failures"), output);
		assertTrue(
				output.contains("assertsVerify:  {namespace = product, folder = Product, request = Add a new product}"),
				output);
		assertTrue(output.contains("FAILED: ErrorFixture.assertsVerify"), output);
		assertTrue(output.contains("java.lang.AssertionError: Condition should be true"), output);
		assertTrue(output.contains("\tat " + ErrorFixture.class.getName() + ".assertsVerify("), output);
	}

	@Test
	void identicalPreparedRequestIsPrintedForEveryFailedExecution() {
		JPostmanReport report = new JPostmanReport().configure("request");
		JPostmanInfo first = requestInfo().requestLog("PREPARED REQUEST");
		JPostmanInfo second = new JPostmanInfo(new String[0], "", "assertsInternalSoft", "product", "Product",
				"Add a new product");
		second.annotation = "@JPostmanResponse";
		second.method("assertsInternalSoft");
		second.appendMethod("newProduct");
		second.statusCode(201).requestLog("PREPARED REQUEST");

		report.failed(first);
		report.failed(second);
		String output = capture(report::summary).get(0);

		assertEquals(2, occurrences(output, "PREPARED REQUEST"), output);
		assertTrue(output.indexOf("PREPARED REQUEST") < output.indexOf("assertsInternalSoft:"), output);
		assertTrue(output.lastIndexOf("PREPARED REQUEST") > output.indexOf("assertsInternalSoft:"), output);
	}

	@Test
	void additionalFailureDataIsSeparatedFromTheNextExecution() {
		JPostmanReport report = new JPostmanReport().configure("error");
		JPostmanInfo first = requestInfo();
		JPostmanInfo second = new JPostmanInfo(new String[0], "", "assertsInternalSoft", "product", "Product",
				"Add a new product");
		second.annotation = "@JPostmanResponse";
		second.method("assertsInternalSoft");
		second.appendMethod("newProduct");
		second.statusCode(201);

		AssertionError firstFailure = new AssertionError("first failure");
		firstFailure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("DemoTest10", "assertsVerify", "DemoTest10.java", 40) });
		AssertionError secondFailure = new AssertionError("second failure");
		secondFailure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("DemoTest10", "assertsInternalSoft", "DemoTest10.java", 52) });

		report.failed(first, firstFailure);
		report.failed(second, secondFailure);
		String output = capture(report::summary).get(0);
		String expectedBoundary = "\tat DemoTest10.assertsVerify(DemoTest10.java:40)" + System.lineSeparator()
				+ System.lineSeparator() + "assertsInternalSoft:";

		assertTrue(output.contains(expectedBoundary), output);
	}

	@Test
	void errorOptionMarksFailureForFullTraceCleanup() {
		JPostmanReport report = new JPostmanReport().configure("request", "error");
		AssertionError failure = new AssertionError("boom");
		capture(() -> report.failed(requestInfo(), failure));
		assertTrue(JPostmanReport.hasFullErrorTrace(failure));
		assertEquals(0, failure.getSuppressed().length);

		AssertionError wrapper = new AssertionError("wrapper");
		wrapper.initCause(failure);
		assertTrue(JPostmanReport.hasFullErrorTrace(wrapper));
	}

	@Test
	void configurationFailuresAreCombinedIntoDisplayedFailures() {
		JPostmanReport report = new JPostmanReport();
		report.passed(topLevel("softAsserts"));
		capture(() -> {
			for (int index = 1; index <= 5; index++) {
				report.failed(topLevel("failed" + index));
			}
		});
		report.configurationFailed();

		String summary = report.log();

		assertEquals(6, report.total());
		assertEquals(1, report.configurationFailures());
		assertTrue(summary.contains("Total tests run: 6, Passes: 0, Failures: 6, Skips: 0"), summary);
	}

	private static final class ErrorFixture {
		static AssertionError failure() {
			try {
				new ErrorFixture().assertsVerify();
				return null;
			} catch (AssertionError error) {
				return error;
			}
		}

		void assertsVerify() {
			throw new AssertionError("Condition should be true");
		}
	}

	private static final class SkipAllFixture {
		@JPostman.ReportContext(fail = { "skipAll", "error" })
		private JPostman.Report report = new JPostmanReport();

		@JPostman.Request(namespace = "product", folder = "Product", request = "Add a new product")
		void newProduct() {
		}

		@JPostman.Response(dependsOn = "newProduct")
		void assertsInternalSoft() {
		}

		@JPostman.Response(dependsOn = "newProduct")
		void softAsserts() {
		}

		@JPostman.Response(dependsOn = "newProduct")
		void assertsInternalSoftVerify() {
		}
	}

	private JPostmanInfo requestInfo() {
		JPostmanInfo info = new JPostmanInfo(new String[0], "", "assertsVerify", "product", "Product",
				"Add a new product");
		info.annotation = "@JPostmanResponse";
		info.method("assertsVerify");
		info.appendMethod("newProduct");
		info.statusCode(201);
		return info;
	}

	private JPostmanInfo topLevel(String method) {
		return new JPostmanInfo(new String[0], "", method, "", "", "").method(method);
	}

	private int occurrences(String value, String expected) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(expected, index)) >= 0) {
			count++;
			index += expected.length();
		}
		return count;
	}

	private List<String> capture(Runnable action) {
		List<String> output = new ArrayList<>();
		try (JPostmanOutputs.Scope ignored = JPostmanOutputs.use(output::add)) {
			action.run();
		}
		return output;
	}
}
