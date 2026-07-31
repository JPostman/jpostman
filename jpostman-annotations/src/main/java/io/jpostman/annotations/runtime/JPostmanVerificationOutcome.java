package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;

/**
 * Tracks a successful verify=1 execution until the test framework finalizes the
 * test.
 */
final class JPostmanVerificationOutcome {

	private static final ThreadLocal<JPostmanInfo> SKIP = new ThreadLocal<>();

	private JPostmanVerificationOutcome() {
	}

	static void requestSkip(JPostmanInfo info) {
		if (SKIP.get() == null) {
			SKIP.set(info);
		}
	}

	static boolean requested() {
		return SKIP.get() != null;
	}

	static void clear() {
		SKIP.remove();
	}

	static String message(Method method) {
		String kind = "request";
		if (method != null) {
			if (JPostmanAnnotations.runner(method) != null) {
				kind = "runner";
			} else if (JPostmanAnnotations.call(method) != null) {
				kind = "call";
			} else if (JPostmanAnnotations.response(method) != null) {
				kind = "response";
			}
		}
		return "JPostman " + kind + " skipped.\n"
				+ "verify = 1 completed execution without status-code verification and marked the test skipped.";
	}
}
