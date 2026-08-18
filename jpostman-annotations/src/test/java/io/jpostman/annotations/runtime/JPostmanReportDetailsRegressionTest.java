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

class JPostmanReportDetailsRegressionTest {

	@Test
	void reportAnnotationsExposeDetailsAndComposableFailValues() throws Exception {
		assertEquals(false, JPostman.ReportContext.class.getMethod("details").getDefaultValue());
		assertEquals(false, JPostmanReportContext.class.getMethod("details").getDefaultValue());
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
		report.configure("skipAll");
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
		assertThrows(IllegalArgumentException.class, () -> report.configure("verbose"));
		assertThrows(IllegalArgumentException.class, () -> report.configure("ignore", "skipAll"));
		assertThrows(IllegalArgumentException.class, () -> report.configure("all", "request"));
		report.configure("error");
		report.configure("response", "error");
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
	void detailsFalsePrintsOnlySummary() {
		JPostmanReport report = new JPostmanReport().configure(false, new String[] { "ignore" });
		report.passed(requestInfo().requestLog("PREPARED REQUEST"));

		String output = capture(report::summary).get(0);

		assertTrue(output.contains("JPostman report"), output);
		assertFalse(output.contains("JPostman Execution Details:"), output);
		assertFalse(output.contains("PREPARED REQUEST"), output);
	}

	@Test
	void detailsTruePrintsExecutionLineWithoutRequest() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		report.passed(requestInfo().requestLog("PREPARED REQUEST"));

		String output = capture(report::summary).get(0);

		assertTrue(output.contains("JPostman Execution Details:"), output);
		assertTrue(output.contains("assertsVerify:  statusCode=201, duration="), output);
		assertTrue(output.contains("(assertsVerify -> newProduct)"), output);
		assertFalse(output.contains("PREPARED REQUEST"), output);
	}

	@Test
	void detailsMarkSkippedExecutionWithoutZeroDuration() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo skipped = new JPostmanInfo(new String[0], "", "addNewproduct", "product", "Product",
				"Add a new product");
		skipped.annotation = "@JPostmanResponse";
		skipped.method("addNewproduct");
		skipped.requestLog("REQUEST MUST NOT PRINT FOR A SKIPPED EXECUTION");
		report.skipped(skipped);

		String output = capture(report::summary).get(0);
		String expected = "addNewproduct:  SKIPPED, {namespace = product, folder = Product, request = Add a new product}";

		assertTrue(output.contains("Total tests run: 1, Passes: 0, Failures: 0, Skips: 1"), output);
		assertTrue(output.contains(expected), output);
		assertFalse(output.contains("addNewproduct:  duration="), output);
		assertFalse(output.contains("REQUEST MUST NOT PRINT FOR A SKIPPED EXECUTION"), output);
	}

	@Test
	void detailsIncludeStatusCodeForExecutedVerifySkip() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo skipped = new JPostmanInfo(new String[0], "", "testAuthRunner", "", "Auth",
				"Get current authenticated user");
		skipped.annotation = "@JPostmanRunner";
		skipped.method("testAuthRunner");
		skipped.statusCode(401);
		report.skipped(skipped);

		String output = capture(report::summary).get(0);
		String expected = "testAuthRunner:  statusCode=401, SKIPPED, {folder = Auth, request = Get current authenticated user}";

		assertTrue(output.contains(expected), output);
		assertFalse(output.contains("testAuthRunner:  SKIPPED,"), output);
	}

	@Test
	void detailsDoNotDuplicateDebugRequestOutput() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		report.passed(requestInfo().requestLog("PREPARED REQUEST"));

		String output = capture(report::summary).get(0);

		assertTrue(output.contains("JPostman Execution Details:"), output);
		assertTrue(output.contains("assertsVerify:  statusCode=201, duration="), output);
		assertFalse(output.contains("PREPARED REQUEST"), output);
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
		assertFalse(text.contains("JPostman Execution Details:"), text);
		assertFalse(text.contains("assertsVerify:"), text);
		assertFalse(text.contains("PREPARED REQUEST"), text);
		assertFalse(text.contains("RECEIVED RESPONSE"), text);
	}

	@Test
	void failureOptionsAppendSelectedOutputAfterReportSummary() {
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
	void detailsDoNotDuplicateFailedExecutionBeforeOrAfterSummary() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo info = requestInfo();

		assertTrue(capture(() -> report.failed(info)).isEmpty());
		String output = capture(report::summary).get(0);
		String line = "assertsVerify:  statusCode=201, duration=";

		assertTrue(output.indexOf("JPostman report") < output.indexOf("JPostman Execution Details:"), output);
		assertEquals(output.indexOf(line), output.lastIndexOf(line), output);
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
		JPostmanReport report = new JPostmanReport().configure("request");
		JPostmanInfo first = requestInfo().requestLog("FIRST REQUEST");
		JPostmanInfo second = new JPostmanInfo(new String[0], "", "assertsInternalSoft", "product", "Product",
				"Add a new product");
		second.annotation = "@JPostmanResponse";
		second.method("assertsInternalSoft");
		second.appendMethod("newProduct");
		second.statusCode(201).requestLog("SECOND REQUEST");

		report.failed(first, new AssertionError("first failure"));
		report.failed(second, new AssertionError("second failure"));
		String output = capture(report::summary).get(0);
		String expectedBoundary = "FIRST REQUEST" + System.lineSeparator() + System.lineSeparator()
				+ "assertsInternalSoft:";

		assertTrue(output.contains(expectedBoundary), output);
	}

	@Test
	void blankTopLevelResultMergesCompletedDependencyAndUsesMultilineChain() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo parent = new JPostmanInfo(new String[0], "", "getCurrentAuthenticatedUser", "restage", "Auth",
				"Get current authenticated user");
		parent.annotation = "@JPostmanResponse";
		parent.method("getCurrentAuthenticatedUser");

		JPostmanInfo helper = parent.child("loginUserAndGetAccessRefreshTokensRequest", "restage", "Auth",
				"Get current authenticated user");
		helper.annotation = "@JPostmanRequest";
		helper.method("loginUserAndGetAccessRefreshTokensRequest");

		JPostmanInfo dependency = helper.childExact("loginUserAndGetAccessRefreshTokens", "restage", "Auth",
				"Login user and get access/refresh tokens");
		dependency.annotation = "@JPostmanResponse";
		dependency.method("loginUserAndGetAccessRefreshTokens");
		dependency.appendMethod("HttpClientExecutor(#Ref1)");
		dependency.syntheticError(503, "Service Unavailable",
				new IllegalStateException("Failed to execute request",
						new java.net.ConnectException("Connection refused")),
				new java.net.ConnectException("Connection refused"));

		report.passed(dependency);
		report.failed(parent, new IllegalStateException("Parent request did not complete"));

		assertEquals(1, report.total());
		assertEquals(0, report.passed.size());
		assertEquals(1, report.failed.size());

		String output = capture(report::summary).get(0);
		String firstLine = "getCurrentAuthenticatedUser:  statusCode=503, duration=00:00.000, "
				+ "{namespace = restage, folder = Auth, request = Login user and get access/refresh tokens}";
		String chain = System.lineSeparator() + "\t\t (getCurrentAuthenticatedUser -> "
				+ "loginUserAndGetAccessRefreshTokensRequest -> loginUserAndGetAccessRefreshTokens "
				+ "-> HttpClientExecutor)";

		assertTrue(output.contains(firstLine + chain), output);
		assertEquals(1, occurrences(output, "getCurrentAuthenticatedUser:"), output);
		assertFalse(output.contains("(synthetic"), output);
		assertFalse(output.contains("ConnectException"), output);
	}

	@Test
	void callAndExecutedResponseDependencyAreReportedAsSeparateExecutions() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo call = new JPostmanInfo(new String[0], "", "getCurrentAuthenticatedUser", "restage", "Auth",
				"Get current authenticated user");
		call.annotation = "@JPostmanCall";
		call.method("getCurrentAuthenticatedUser");

		JPostmanInfo dependency = call.childExact("getAccessToken", "restage", "Auth",
				"Login user and get access/refresh tokens");
		dependency.annotation = "@JPostmanResponse";
		dependency.method("getAccessToken");
		dependency.appendMethod("HttpClientExecutor(#auth)");
		dependency.statusCode(200);

		call.appendMethod("HttpClientExecutor");
		call.statusCode(200);

		report.passed(dependency);
		report.passed(call);

		assertEquals(2, report.total());
		String output = capture(report::summary).get(0);
		assertTrue(output.contains("getAccessToken:  statusCode=200"), output);
		assertTrue(
				output.contains(
						"{namespace = restage, folder = Auth, request = Login user and get access/refresh tokens}"),
				output);
		assertTrue(output.contains("getCurrentAuthenticatedUser:  statusCode=200"), output);
		assertTrue(output.contains("{namespace = restage, folder = Auth, request = Get current authenticated user}"),
				output);
	}

	@Test
	void executionDetailsIncludeExecutorClassWithoutSelectionSuffix() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo direct = new JPostmanInfo(new String[0], "", "loginUserAndGetAccessRefreshTokens", "restage",
				"Auth", "Login user and get access/refresh tokens");
		direct.annotation = "@JPostmanResponse";
		direct.method("loginUserAndGetAccessRefreshTokens");
		direct.appendMethod("io.jpostman.httpclient.HttpClientExecutor(#Ref1)");
		direct.statusCode(200);
		report.passed(direct);

		String output = capture(report::summary).get(0);
		String expected = System.lineSeparator() + "\t\t (loginUserAndGetAccessRefreshTokens "
				+ "-> HttpClientExecutor)";

		assertTrue(output.contains(expected), output);
		assertFalse(output.contains("HttpClientExecutor(#Ref1)"), output);
		assertFalse(output.contains("io.jpostman.httpclient.HttpClientExecutor"), output);
	}

	@Test
	void completedTopLevelResponseReplacesSuccessfulDependencyRow() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo parent = new JPostmanInfo(new String[0], "", "refreshAuthSessionToken", "restage", "Auth",
				"Refresh auth session/token");
		parent.annotation = "@JPostmanResponse";
		parent.method("refreshAuthSessionToken");

		JPostmanInfo helper = parent.child("loginUserAndGetAccessRefreshTokensRequest", "restage", "Auth",
				"Refresh auth session/token");
		helper.annotation = "@JPostmanRequest";
		helper.method("loginUserAndGetAccessRefreshTokensRequest");

		JPostmanInfo dependency = helper.childExact("loginUserAndGetAccessRefreshTokens", "restage", "Auth",
				"Login user and get access/refresh tokens");
		dependency.annotation = "@JPostmanResponse";
		dependency.method("loginUserAndGetAccessRefreshTokens");
		dependency.appendMethod("HttpClientExecutor(#Ref1)");
		dependency.statusCode(200);
		report.passed(dependency);

		parent.statusCode(403);
		report.failed(parent, new AssertionError("Expected status 200 but was 403"));

		assertEquals(1, report.total());
		assertEquals(0, report.passed.size());
		assertEquals(1, report.failed.size());

		String output = capture(report::summary).get(0);
		assertEquals(1, occurrences(output, "refreshAuthSessionToken:"), output);
		assertTrue(output.contains("statusCode=403"), output);
		assertTrue(output.contains("request = Refresh auth session/token"), output);
		assertFalse(output.contains("statusCode=200"), output);
		assertFalse(output.contains("request = Login user and get access/refresh tokens}"), output);
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

	private static final class SkipAllFixture {
		@JPostman.ReportContext(fail = "skipAll")
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
