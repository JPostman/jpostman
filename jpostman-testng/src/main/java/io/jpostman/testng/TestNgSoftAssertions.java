package io.jpostman.testng;

import org.testng.asserts.SoftAssert;

/**
 * Fluent soft assertions.
 *
 * <p>
 * Each assertion keeps a short failure message. When secure log output is
 * enabled, the secure log is added once as suppressed failure context when
 * {@link #assertAll()} fails.
 * </p>
 */
public final class TestNgSoftAssertions extends TestNgAssertions<TestNgSoftAssertions> {

	private final SoftAssert softAssert = new SoftAssert();

	TestNgSoftAssertions(TestNgContext context, boolean includeLog) {
		super(context, includeLog);
	}

	@Override
	boolean soft() {
		return true;
	}

	@Override
	protected TestNgSoftAssertions assertWithLog(Runnable assertion) {
		try {
			assertion.run();
		} catch (AssertionError error) {
			softAssert.fail(error.getMessage());
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
	public TestNgContext verify(int statusCode) {
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
	 * compatibility with TestNG {@code SoftAssert} style.
	 * </p>
	 */
	public TestNgContext assertAll() {
		try {
			softAssert.assertAll();
		} catch (AssertionError error) {
			throw wrap(error);
		} finally {
			context.resetSoft(this);
		}
		return context;
	}
}
