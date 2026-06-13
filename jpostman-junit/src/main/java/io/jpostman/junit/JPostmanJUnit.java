package io.jpostman.junit;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.jpostman.secure.JPostmanAssertionError;

/**
 * Enables JPostman JUnit test defaults.
 *
 * <p>
 * This enables per-class test lifecycle. Failure printing can be enabled for
 * IDE runners that show assertion details only in the JUnit failure trace
 * panel.
 * </p>
 */
@Target(TYPE)
@Retention(RUNTIME)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(JPostmanJUnit.FailurePrinter.class)
public @interface JPostmanJUnit {

	/**
	 * Enables printing test failures to the console.
	 *
	 * @return {@code true} to print failures to the console
	 */
	boolean printFailures() default false;

	/**
	 * Prints test failures to the console after test execution.
	 */
	final class FailurePrinter implements AfterTestExecutionCallback {

		@Override
		public void afterTestExecution(ExtensionContext context) {
			JPostmanJUnit annotation = context.getRequiredTestClass().getAnnotation(JPostmanJUnit.class);

			boolean skipPrintFailures = Boolean.parseBoolean(System.getProperty("skipPrintFailures", "false"));
			if (skipPrintFailures || annotation == null || !annotation.printFailures()) {
				return;
			}

			Optional<Throwable> error = context.getExecutionException();
			if (error.isEmpty()) {
				return;
			}

			printFailure(context, error.get());
		}

		private void printFailure(ExtensionContext context, Throwable error) {
			System.err.println();
			System.err.println("********** JUnit Failure **********");
			System.err.println(context.getDisplayName());
			System.err.println(error.getClass().getName() + ": " + nullToEmpty(error.getMessage()));

			if (error instanceof JPostmanAssertionError) {
				String secureLog = ((JPostmanAssertionError) error).secureLog();
				if (secureLog != null && !secureLog.isBlank()) {
					System.err.println();
					System.err.println(secureLog.trim());
				}
			}

			System.err.println("***********************************");
			System.err.println();
		}

		private String nullToEmpty(String value) {
			return value == null ? "" : value;
		}
	}
}