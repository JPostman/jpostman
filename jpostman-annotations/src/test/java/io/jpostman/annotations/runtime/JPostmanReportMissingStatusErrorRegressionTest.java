package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostmanOutputs;

class JPostmanReportMissingStatusErrorRegressionTest {

	@Test
	void failedExecutionsWithoutStatusPrintCompactErrorMessageOnNextLine() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });

		report.failed(info("refreshAuthSessionToken", "Auth", "Refresh auth session/token"),
				new AssertionError("Request not found: \"Refresh auth session/token\" (namespace=, folder=Auth)"));
		report.failed(info("testAuthRunner", "Auth", ""),
				new AssertionError("JPostman runner folder was not found.\nFolder not found: Auth"));
		report.failed(info("getCurrentAuthenticatedUser", "Auth", "Get current authenticated user"),
				new AssertionError("Request not found: \"Get current authenticated user\" (namespace=, folder=Auth)"));
		report.failed(info("loginUserAndGetAccessRefreshTokens", "Auth", "Login user and get access/refresh tokens"),
				new AssertionError(
						"Request not found: \"Login user and get access/refresh tokens\" (namespace=, folder=Auth)"));

		String output = summary(report);

		assertTrue(output.contains("refreshAuthSessionToken:  duration=00:00.000, "
				+ "{folder = Auth, request = Refresh auth session/token}" + System.lineSeparator()
				+ "\t\t Request not found: \"Refresh auth session/token\" (namespace=, folder=Auth)"), output);
		assertTrue(output.contains("testAuthRunner:  duration=00:00.000, {folder = Auth}" + System.lineSeparator()
				+ "\t\t JPostman runner folder was not found: \"Auth\" (namespace=, folder=Auth)"), output);
		assertTrue(
				output.contains("getCurrentAuthenticatedUser:  duration=00:00.000, "
						+ "{folder = Auth, request = Get current authenticated user}" + System.lineSeparator()
						+ "\t\t Request not found: \"Get current authenticated user\" (namespace=, folder=Auth)"),
				output);
		assertTrue(output.contains("loginUserAndGetAccessRefreshTokens:  duration=00:00.000, "
				+ "{folder = Auth, request = Login user and get access/refresh tokens}" + System.lineSeparator()
				+ "\t\t Request not found: \"Login user and get access/refresh tokens\" (namespace=, folder=Auth)"),
				output);
		assertFalse(output.contains("statusCode="), output);
		assertFalse(output.contains("Check @JPostmanRunner.folder"), output);
		assertFalse(output.contains("(@JPostmanRunner:"), output);
		assertFalse(output.contains("folder was not found.\nFolder not found"), output);
	}

	@Test
	void completedResponseDoesNotAppendExceptionMessageToCompactLine() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo info = info("getCurrentAuthenticatedUser", "Auth", "Get current authenticated user");
		info.statusCode(500);
		report.failed(info, new AssertionError("This must stay out of the compact status row"));

		String output = summary(report);

		assertTrue(output.contains("getCurrentAuthenticatedUser:  statusCode=500, duration=00:00.000"), output);
		assertFalse(output.contains("This must stay out of the compact status row"), output);
	}

	@Test
	void missingStatusMessageAppearsBeforeDependencyChain() {
		JPostmanReport report = new JPostmanReport().configure(true, new String[] { "ignore" });
		JPostmanInfo info = info("getCurrentAuthenticatedUser", "Auth", "Get current authenticated user");
		info.appendMethod("loginUserAndGetAccessRefreshTokensRequest");
		report.failed(info, new AssertionError("Request lookup failed"));

		String output = summary(report);
		String expected = "getCurrentAuthenticatedUser:  duration=00:00.000, "
				+ "{folder = Auth, request = Get current authenticated user}" + System.lineSeparator()
				+ "\t\t Request lookup failed" + System.lineSeparator()
				+ "\t\t (getCurrentAuthenticatedUser -> loginUserAndGetAccessRefreshTokensRequest)";

		assertTrue(output.contains(expected), output);
	}

	private JPostmanInfo info(String method, String folder, String request) {
		JPostmanInfo info = new JPostmanInfo(new String[0], "", method, "", folder, request);
		info.annotation = request == null || request.isBlank() ? "@JPostmanRunner" : "@JPostmanResponse";
		info.method(method);
		return info;
	}

	private String summary(JPostmanReport report) {
		List<String> output = new ArrayList<>();
		try (JPostmanOutputs.Scope ignored = JPostmanOutputs.use(output::add)) {
			report.summary();
		}
		return String.join("", output);
	}
}
