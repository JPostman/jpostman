package io.jpostman.junit;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

import io.jpostman.secure.JPostmanSoftAssertions;

/**
 * Fluent soft assertions.
 *
 * <p>
 * Each assertion keeps a short failure message. When secure log output is
 * enabled, the secure log is added once as suppressed failure context when
 * {@link #assertAll()} fails.
 * </p>
 */
public final class JUnitSoftAssertions extends JUnitAssertions<JUnitSoftAssertions>
		implements JPostmanSoftAssertions<JUnitContext, JUnitSoftAssertions> {

	private final List<AssertionError> failures = new ArrayList<>();

	JUnitSoftAssertions(JUnitContext context, boolean includeLog) {
		super(context, includeLog);
	}

	@Override
	boolean soft() {
		return true;
	}

	@Override
	protected JUnitSoftAssertions assertWithLog(Runnable assertion) {
		try {
			assertion.run();
		} catch (AssertionError error) {
			failures.add(error);
		}
		return this;
	}

	/**
	 * Verifies all collected soft assertions and returns the test context.
	 *
	 * <p>
	 * The expected status code is added only when no status code assertion was
	 * already collected.
	 * </p>
	 *
	 * @param statusCode expected status code
	 * @return current test context
	 */
	@Override
	public JUnitContext verify(int statusCode) {
		if (!statusCodeAsserted) {
			statusCode(statusCode);
		}
		assertAll();
		return context;
	}

	/**
	 * Verifies all collected soft assertions.
	 *
	 * <p>
	 * This method is available only for soft assertions and is provided for
	 * compatibility with JUnit {@code assertAll} style.
	 * </p>
	 */
	public JUnitContext assertAll() {
		try {
			List<Executable> executables = new ArrayList<>();
			for (AssertionError failure : failures) {
				executables.add(() -> {
					throw failure;
				});
			}
			Assertions.assertAll(executables);
		} catch (AssertionError error) {
			throw wrap(error);
		} finally {
			context.resetSoft(this);
		}
		return context;
	}
}
