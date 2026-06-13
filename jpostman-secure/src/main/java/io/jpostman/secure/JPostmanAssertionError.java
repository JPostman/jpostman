package io.jpostman.secure;

/**
 * Assertion error that carries optional secure log context.
 *
 * <p>
 * Test framework modules can use this error to keep the main assertion message
 * short while still preserving secure request and response details for failure
 * reporting.
 * </p>
 */
public class JPostmanAssertionError extends AssertionError {
	private static final long serialVersionUID = 1L;

	private final String secureLog;

	/**
	 * Creates an assertion error with optional secure log context.
	 *
	 * @param message assertion failure message
	 * @param cause original assertion error
	 * @param secureLog secure request and response log, or {@code null}
	 */
	public JPostmanAssertionError(String message, Throwable cause, String secureLog) {
		super(message, cause);
		this.secureLog = secureLog;
	}

	/**
	 * Returns the secure log attached to this assertion error.
	 *
	 * @return secure log, or {@code null} when no secure log was attached
	 */
	public String secureLog() {
		return secureLog;
	}

	/**
	 * Wraps an assertion error with optional secure log context.
	 *
	 * <p>
	 * The original assertion error is kept as the cause. When secure log content
	 * is provided, it is also added as a suppressed error so Maven, IDEs, and test
	 * reports can display it with the failure.
	 * </p>
	 *
	 * @param error original assertion error
	 * @param secureLog secure request and response log, or {@code null}
	 * @return wrapped JPostman assertion error
	 */
	public static JPostmanAssertionError wrap(AssertionError error, String secureLog) {
		JPostmanAssertionError wrapped = new JPostmanAssertionError(
				error.getMessage() + "\n",
				error,
				secureLog);

		if (secureLog != null && !secureLog.isBlank()) {
			wrapped.addSuppressed(new AssertionError(secureLog + "\n\n"));
		}

		return wrapped;
	}
}