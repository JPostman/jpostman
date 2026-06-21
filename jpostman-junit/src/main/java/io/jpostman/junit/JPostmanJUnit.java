package io.jpostman.junit;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

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
@ExtendWith({ JPostmanJUnit.FailurePrinter.class, JPostmanJUnitAnnotationExtension.class })
public @interface JPostmanJUnit {

	/**
	 * Enables printing test failures to the console.
	 *
	 * @return {@code true} to print failures to the console
	 */
	boolean printFailures() default true;

	/**
	 * Prints test failures to the console.
	 */
	final class FailurePrinter implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

		@Override
		public void handleTestExecutionException(ExtensionContext context, Throwable error) throws Throwable {
			printFailure(context, error);
			throw error;
		}

		@Override
		public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable error)
				throws Throwable {
			printFailure(context, error);
			throw error;
		}

		@Override
		public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable error)
				throws Throwable {
			printFailure(context, error);
			throw error;
		}

		static void printFailure(ExtensionContext context, Throwable error) {
			JPostmanJUnit annotation = context.getRequiredTestClass().getAnnotation(JPostmanJUnit.class);

			boolean skipPrintFailures = Boolean.parseBoolean(System.getProperty("skipPrintFailures", "false"));

			if (skipPrintFailures || annotation == null || !annotation.printFailures()) {
				return;
			}

			System.err.println();
			System.err.println("********** JUnit Failure **********");
			System.err.println(context.getDisplayName());

			Throwable root = rootCause(error);

			System.err.println(root.getClass().getName() + ": " + nullToEmpty(root.getMessage()));

			JPostmanAssertionError jpostmanError = findJPostmanAssertionError(error);
			if (jpostmanError != null) {
				String secureLog = jpostmanError.secureLog();
				if (secureLog != null && !secureLog.isBlank()) {
					System.err.println();
					System.err.println(secureLog.trim());
				}
			}

			System.err.println("***********************************");
			System.err.println();
		}

		private static JPostmanAssertionError findJPostmanAssertionError(Throwable error) {
			Throwable current = error;

			while (current != null) {
				if (current instanceof JPostmanAssertionError) {
					return (JPostmanAssertionError) current;
				}

				for (Throwable suppressed : current.getSuppressed()) {
					JPostmanAssertionError found = findJPostmanAssertionError(suppressed);
					if (found != null) {
						return found;
					}
				}

				current = current.getCause();
			}

			return null;
		}

		private static Throwable rootCause(Throwable error) {
			Throwable current = error;
			while (current.getCause() != null) {
				current = current.getCause();
			}
			return current;
		}

		private static String nullToEmpty(String value) {
			return value == null ? "" : value;
		}
	}
}