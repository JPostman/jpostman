package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;

/**
 * Regression contract for hard, method-soft, and class-soft assertion scopes.
 */
public class JPostmanAssertContextLifecycleContractTest {

	@Test
	void case1HardContextExplicitAfterAllVerifyHasNoErrorWhenEmpty() {
		HardFixture fixture = new HardFixture();
		assertDoesNotThrow(() -> {
			fixture.asserts.verify();
		});
	}

	@Test
	void case2HardContextFailsOnAssertionLine() {
		HardFixture fixture = new HardFixture();
		assertThrows(AssertionError.class, () -> fixture.asserts.isTrue(false));
	}

	@Test
	void case3SoftContextDefersUntilImplicitClassCompletion() {
		SoftFixture fixture = new SoftFixture();
		assertDoesNotThrow(() -> fixture.asserts.isTrue(false));
		assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.completeTestClass(fixture));
	}

	@Test
	void case4SoftContextExplicitAfterAllVerifyFailsAtVerify() {
		SoftFixture fixture = new SoftFixture();
		assertDoesNotThrow(() -> fixture.asserts.isTrue(false));
		assertThrows(AssertionError.class, fixture.asserts::verify);
	}

	@Test
	void case5RunnerSoftTrueDefersMethodFailure() {
		HardFixture fixture = new HardFixture();
		JPostman.Assert methodSoft = fixture.asserts.soft();
		assertDoesNotThrow(() -> methodSoft.isTrue(false));
		assertThrows(AssertionError.class, methodSoft::verify);
	}

	@Test
	void case6RunnerSoftFalseFailsImmediately() {
		HardFixture fixture = new HardFixture();
		assertThrows(AssertionError.class, () -> fixture.asserts.isTrue(false));
	}

	@Test
	void case7ExplicitSoftFacadeDoesNotChangeBaseFacade() {
		HardFixture fixture = new HardFixture();
		JPostman.Assert methodSoft = fixture.asserts.soft();
		assertDoesNotThrow(() -> methodSoft.isTrue(false));
		assertThrows(AssertionError.class, () -> fixture.asserts.isTrue(false));
		assertThrows(AssertionError.class, methodSoft::verify);
	}

	@Test
	void case8ExplicitVerifyFlushesImmediatelyEvenWithSoftFieldAndRunner() {
		SoftFixture fixture = new SoftFixture();
		assertDoesNotThrow(() -> fixture.asserts.soft().isTrue(false));
		assertThrows(AssertionError.class, fixture.asserts::verify);
	}

	@Test
	void hardAssertContextFailsImmediatelyAndLeavesNothingForAfterClassVerify() {
		AssertHandler handler = new AssertHandler(false, 201);
		JPostman.Assert asserts = handler.proxy();

		AssertionError error = assertThrows(AssertionError.class, () -> asserts.isTrue(false).statusCode(200));

		assertTrue(error.getMessage().contains("expected [true] but found [false]"),
				"Actual message: " + error.getMessage());
		assertDoesNotThrow(() -> {
			asserts.verify();
		}, "A hard AssertContext must not queue a later status-code assertion after the first failure aborts the chain");
	}

	@Test
	void softAssertContextCollectsConditionAndStatusFailuresUntilAfterClassVerify() {
		AssertHandler handler = new AssertHandler(true, 201);
		JPostman.Assert asserts = handler.proxy();

		assertDoesNotThrow(() -> asserts.isTrue(false).statusCode(200),
				"A soft AssertContext must allow the full assertion chain to complete");

		AssertionError error = assertThrows(AssertionError.class, asserts::verify);
		assertTrue(error.getMessage().contains("expected [true] but found [false]"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + error.getMessage());
	}

	@Test
	void classSoftCollectsAllFailuresUntilAfterClassVerify() throws Exception {
		ClassSoftContext context = new ClassSoftContext(201);
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, true, true);
		DemoOrigin fixture = new DemoOrigin();
		Method testMethod = DemoOrigin.class.getDeclaredMethod("testAuthRunner");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, testMethod);
		try {
			assertDoesNotThrow(() -> asserts.isTrue(false).statusCode(200),
					"A class-soft chain must collect every failure and continue");

			assertTrue(JPostmanAnnotationEngine.takeImmediateAssertionFailure() == null,
					"@AssertContext(soft=true) must not fail or attribute its first assertion before class verification");

			AssertionError afterClassFailure = assertThrows(AssertionError.class, asserts::verify);
			assertTrue(afterClassFailure.getMessage().contains("expected [true] but found [false]"),
					"Actual message: " + afterClassFailure.getMessage());
			assertTrue(afterClassFailure.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
					"Actual message: " + afterClassFailure.getMessage());
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	@Test
	void inheritedStatusVerificationPassesWhileExplicitSoftStatusOverrideFailsWithItsOwnExpectedValue() {
		AssertHandler handler = new AssertHandler(false, 201);
		JPostman.Assert asserts = handler.proxy();

		// Equivalent to @JPostman.Context(verifyStatusCode = 201) when the
		// method-level annotation leaves verify at its default value (-1).
		assertDoesNotThrow(() -> asserts.statusCode(201));

		// A user-created soft facade is an independent explicit assertion. It must
		// retain 200 as its expected value and defer the mismatch until verify().
		JPostman.Assert soft = asserts.soft();
		assertDoesNotThrow(() -> soft.statusCode(200));

		AssertionError error = assertThrows(AssertionError.class, soft::verify);
		assertTrue(error.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + error.getMessage());
	}

	@Test
	void defaultAssertContextUsesNormalHardAssertionsWithoutImplicitStatusVerification() {
		ResponseSelectionContext context = new ResponseSelectionContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);

		AssertionError error = assertThrows(AssertionError.class, () -> asserts.isTrue(false).statusCode(200));

		assertTrue(error.getMessage().contains("expected [true] but found [false]"),
				"The first hard assertion must fail immediately: " + error.getMessage());
		assertEquals(Boolean.FALSE, context.lastSecureSelection,
				"Default AssertContext must call asserts(false), not asserts(), so it does not create an implicit status-code assertion");
		assertDoesNotThrow(() -> {
			asserts.verify();
		}, "The unexecuted chained statusCode(200) must not be queued for @AfterClass verification");
	}

	@Test
	void injectedHardAssertContextSoftSwitchDefersStatusMismatchUntilVerify() {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);

		JPostman.Assert soft = asserts.soft();
		assertDoesNotThrow(() -> soft.isTrue(false).statusCode(200),
				"asserts.soft() must collect the complete chain instead of failing statusCode immediately");

		AssertionError error = assertThrows(AssertionError.class, soft::verify);
		assertTrue(error.getMessage().contains("expected [true] but found [false]"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + error.getMessage());
		assertEquals(1, context.softCalls);
		assertEquals(0, context.hardCalls, "The explicit soft switch must not call asserts(false) after soft(false)");
	}

	@Test
	void contextAssertsSoftSwitchDefersStatusMismatchUntilVerify() {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Test test = JPostmanTestProxy.wrap(context, () -> context);

		JPostman.Assert soft = test.asserts().soft();
		assertDoesNotThrow(() -> soft.isTrue(false).statusCode(200),
				"jpostman.ctx().asserts().soft() must collect the complete chain");

		AssertionError error = assertThrows(AssertionError.class, soft::verify);
		assertTrue(error.getMessage().contains("expected [true] but found [false]"),
				"Actual message: " + error.getMessage());
		assertTrue(error.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + error.getMessage());
		assertEquals(1, context.softCalls);
	}

	@Test
	void injectedExplicitSoftIsAutomaticallyVerifiedAtMethodEnd() throws Exception {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);
		Method method = AutoVerifyFixture.class.getDeclaredMethod("response");

		JPostmanAnnotationEngine.beginAssertionCleanup(new AutoVerifyFixture(), method);
		try {
			assertDoesNotThrow(() -> asserts.soft().isTrue(false).statusCode(200));
			AssertionError error = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(method));
			assertTrue(error.getMessage().contains("expected [true] but found [false]"));
			assertTrue(error.getMessage().contains("Status code mismatch: expected [200] but found [201]"));
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	@Test
	void contextExplicitSoftIsAutomaticallyVerifiedAtMethodEnd() throws Exception {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Test test = JPostmanTestProxy.wrap(context, () -> context);
		Method method = AutoVerifyFixture.class.getDeclaredMethod("runner");

		JPostmanAnnotationEngine.beginAssertionCleanup(new AutoVerifyFixture(), method);
		try {
			assertDoesNotThrow(() -> test.asserts().soft().statusCode(200));
			AssertionError error = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(method));
			assertTrue(error.getMessage().contains("Status code mismatch: expected [200] but found [201]"));
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	@Test
	void explicitSoftModeDoesNotSelectSecureResponse() {
		ResponseSelectionContext context = new ResponseSelectionContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context);

		JPostman.Assert soft = asserts.soft();
		assertDoesNotThrow(() -> soft.isTrue(true));
		assertEquals(Boolean.FALSE, context.lastSecureSelection,
				"Assert.soft() must switch assertion mode without selecting the secure response");
	}

	@Test
	void case8ClassSoftFieldIsNotFlushedByRunnerMethodLifecycle() {
		ProxyContext context = new ProxyContext();
		JPostman.Assert classSoft = JPostmanTestProxy.wrapAssert(() -> context, true, true);

		JPostmanRuntimeRunner.begin(List.of("Login", "Me"));
		try {
			JPostmanRuntimeRunner.afterRequest(0, "Login");
			assertDoesNotThrow(() -> classSoft.isTrue(false));
			assertNull(JPostmanRuntimeRunner.takeSoftFailure(),
					"Runner lifecycle must not consume a class-scoped AssertContext collector");
		} finally {
			JPostmanRuntimeRunner.clear();
		}

		assertThrows(AssertionError.class, classSoft::verify,
				"The class-scoped collector must still fail when class verification runs");
	}

	@Test
	void runnerSoftFacadeStillDefersPerRequestWhenAssertContextIsHard() {
		ProxyContext context = new ProxyContext();
		JPostman.Assert methodSoft = JPostmanTestProxy.wrapAssert(() -> context, false, false).soft();

		JPostmanRuntimeRunner.begin(List.of("Login"));
		try {
			JPostmanRuntimeRunner.afterRequest(0, "Login");
			assertDoesNotThrow(() -> methodSoft.isTrue(false));
			assertTrue(JPostmanRuntimeRunner.hasSoftFailure(),
					"soft() returned from a hard AssertContext must remain behind the Runner-aware facade");
			assertThrows(AssertionError.class, () -> {
				AssertionError failure = JPostmanRuntimeRunner.takeSoftFailure();
				if (failure != null) {
					throw failure;
				}
			});
		} finally {
			JPostmanRuntimeRunner.clear();
		}
	}

	@Test
	void deferredClassSoftFailureRetainsOriginatingTestMethod() throws Exception {
		SoftFixture fixture = new SoftFixture();
		Method testMethod = OriginFixture.class.getDeclaredMethod("testAuthRunner");

		JPostmanAssertionCleanup.register(fixture, testMethod);
		try {
			fixture.asserts.isTrue(false);
			JPostmanAssertionCleanup.markCurrentMethod();
		} finally {
			JPostmanAssertionCleanup.clear();
		}

		assertEquals(testMethod, JPostmanAnnotationEngine.lastAssertionMethod(fixture));
		JPostmanAnnotationEngine.clearAssertionMethod(fixture);
	}

	@Test
	void deferredFailureMessageIncludesOriginatingMethodForPlainAndAnnotatedUseCases() throws Exception {
		Method testMethod = DemoOrigin.class.getDeclaredMethod("testAuthRunner");
		AssertionError aggregate = new AssertionError("Multiple Failures (2 failures)\n"
				+ "\torg.opentest4j.AssertionFailedError: Condition should be true ==> expected: <true> but was: <false>\n"
				+ "\torg.opentest4j.AssertionFailedError: Secure response is not set");

		AssertionError cleaned = JPostmanStackTraceCleaner.cleanFailure(DemoOrigin.class, testMethod, aggregate, false,
				true);

		assertTrue(cleaned.getMessage().contains(
				"DemoOrigin::testAuthRunner: Condition should be true ==> expected: <true> but was: <false>"));
		assertTrue(cleaned.getMessage().contains("DemoOrigin::testAuthRunner: Secure response is not set"));
	}

	@Test
	void reportContextIsSummarizedDuringImplicitClassCompletion() {
		ReportFixture fixture = new ReportFixture();
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.completeTestClass(fixture));
	}

	@Test
	void classSoftCollectorIsVerifiedOnlyOnceAfterSuccessfulFlush() throws Exception {
		SoftFixture fixture = new SoftFixture();
		fixture.asserts.isTrue(true);
		JPostmanAnnotationEngine.completeTestClass(fixture);
		assertEquals(1, fixture.handler.verifyCalls);
	}

	private static final class AutoVerifyFixture {
		@io.jpostman.annotations.JPostmanResponse
		void response() {
		}

		@io.jpostman.annotations.JPostmanRunner
		void runner() {
		}
	}

	public static final class ExplicitSoftVerificationContext {
		private final AssertHandler soft;
		private final AssertHandler hard;
		private int softCalls;
		private int hardCalls;

		private ExplicitSoftVerificationContext(int actualStatusCode) {
			this.soft = new AssertHandler(true, actualStatusCode);
			this.hard = new AssertHandler(false, actualStatusCode);
		}

		public JPostman.Assert soft(boolean secure) {
			softCalls++;
			return soft.proxy();
		}

		public JPostman.Assert asserts(boolean secure) {
			hardCalls++;
			return hard.proxy();
		}

		public JPostman.Assert asserts() {
			hardCalls++;
			return hard.proxy();
		}
	}

	public static final class ResponseSelectionContext {
		private Boolean lastSecureSelection;

		public JPostman.Assert soft(boolean secure) {
			lastSecureSelection = secure;
			return new AssertHandler(true).proxy();
		}

		public JPostman.Assert asserts(boolean secure) {
			lastSecureSelection = secure;
			return new AssertHandler(false).proxy();
		}

		public JPostman.Assert asserts() {
			throw new AssertionError("AssertContext must use asserts(false)");
		}
	}

	public static final class ClassSoftContext {
		private final AssertHandler soft;

		private ClassSoftContext(int actualStatusCode) {
			this.soft = new AssertHandler(true, actualStatusCode);
		}

		public JPostman.Assert soft(boolean secure) {
			return soft.proxy();
		}

		public JPostman.Assert verify() {
			soft.proxy().verify();
			return soft.proxy();
		}
	}

	public static final class ProxyContext {
		private final AssertHandler soft = new AssertHandler(true);
		private final AssertHandler hard = new AssertHandler(false);

		public JPostman.Assert soft(boolean reset) {
			return soft.proxy();
		}

		public JPostman.Assert soft() {
			return soft.proxy();
		}

		public JPostman.Assert asserts() {
			return hard.proxy();
		}

		public JPostman.Assert verify() {
			soft.proxy().verify();
			return soft.proxy();
		}
	}

	@Test
	void hardAndClassSoftAssertContextsRemainIndependentWhenSoftFieldIsUsedFirst() {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Assert softAsserts = JPostmanTestProxy.wrapAssert(() -> context, true, true);
		JPostman.Assert hardAsserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);

		assertDoesNotThrow(() -> softAsserts.statusCode(200), "The soft field must collect its own failure");
		assertThrows(AssertionError.class, () -> hardAsserts.statusCode(200),
				"The hard field must still fail immediately");

		AssertionError softFailure = assertThrows(AssertionError.class, softAsserts::verify,
				"The hard field must not replace the soft field's collector");
		assertTrue(softFailure.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + softFailure.getMessage());
	}

	@Test
	void hardAndClassSoftAssertContextsRemainIndependentWhenHardFieldIsUsedFirst() {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Assert hardAsserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);
		JPostman.Assert softAsserts = JPostmanTestProxy.wrapAssert(() -> context, true, true);

		assertThrows(AssertionError.class, () -> hardAsserts.statusCode(200), "The hard field must fail immediately");
		assertDoesNotThrow(() -> softAsserts.statusCode(200),
				"The soft field must remain soft regardless of field/invocation order");

		AssertionError softFailure = assertThrows(AssertionError.class, softAsserts::verify);
		assertTrue(softFailure.getMessage().contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + softFailure.getMessage());
	}

	@Test
	void demoUseCaseHardAssertFailsImmediatelyAndStopsTheChain() {
		AssertHandler handler = new AssertHandler(false, 201);
		JPostman.Assert asserts = handler.proxy();

		AssertionError failure = assertThrows(AssertionError.class, () -> asserts.isTrue(false).statusCode(200));

		assertTrue(failure.getMessage().contains("expected [true] but found [false]"));
		assertTrue(!failure.getMessage().contains("Status code mismatch"),
				"The chained status assertion must not execute after a hard failure");
		assertDoesNotThrow(() -> {
			asserts.verify();
		});
	}

	@Test
	void demoUseCaseInjectedAssertSoftIsAutomaticallyVerifiedAtMethodCompletion() throws Exception {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);
		AutoVerifyFixture fixture = new AutoVerifyFixture();
		Method method = AutoVerifyFixture.class.getDeclaredMethod("response");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, method);
		try {
			assertDoesNotThrow(() -> asserts.soft().isTrue(false).statusCode(200));
			AssertionError failure = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(method));
			assertAggregate(failure, false);
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	@Test
	void demoUseCaseInjectedAssertSoftManualVerifyFlushesInsideMethod() {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Assert soft = JPostmanTestProxy.wrapAssert(() -> context, false, true).soft();

		assertDoesNotThrow(() -> soft.isTrue(false).statusCode(200));
		assertAggregate(assertThrows(AssertionError.class, soft::verify), false);
		assertDoesNotThrow(() -> {
			soft.verify();
		}, "A manual verify must consume the method-soft failures");
	}

	@Test
	void demoUseCaseRuntimeContextSoftIsAutomaticallyVerifiedAtMethodCompletion() throws Exception {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Test test = JPostmanTestProxy.wrap(context, () -> context);
		AutoVerifyFixture fixture = new AutoVerifyFixture();
		Method method = AutoVerifyFixture.class.getDeclaredMethod("runner");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, method);
		try {
			assertDoesNotThrow(() -> test.asserts().soft().isTrue(false).statusCode(200));
			AssertionError failure = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(method));
			assertAggregate(failure, false);
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	@Test
	void demoUseCaseRuntimeContextSoftManualVerifyFlushesInsideMethod() {
		ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);
		JPostman.Test test = JPostmanTestProxy.wrap(context, () -> context);
		JPostman.Assert soft = test.asserts().soft();

		assertDoesNotThrow(() -> soft.isTrue(false).statusCode(200));
		assertAggregate(assertThrows(AssertionError.class, soft::verify), false);
		assertDoesNotThrow(() -> {
			soft.verify();
		});
	}

	@Test
	void demoUseCaseClassSoftManualAfterClassVerifyIncludesOriginMethodOnEveryLine() throws Exception {
		CompleteUseCaseFixture fixture = new CompleteUseCaseFixture();
		Method method = CompleteUseCaseFixture.class.getDeclaredMethod("softAsserts");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, method);
		try {
			assertDoesNotThrow(() -> fixture.softAsserts.isTrue(false).statusCode(200));
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}

		AssertionError failure = assertThrows(AssertionError.class, fixture.softAsserts::verify);
		assertAggregate(failure, true);
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.completeTestClass(fixture),
				"Automatic class completion must not report a duplicate after manual @AfterClass verify");
	}

	@Test
	void demoUseCaseClassSoftAutomaticAfterClassVerifyIncludesOriginMethodOnEveryLine() throws Exception {
		CompleteUseCaseFixture fixture = new CompleteUseCaseFixture();
		Method method = CompleteUseCaseFixture.class.getDeclaredMethod("softAsserts");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, method);
		try {
			assertDoesNotThrow(() -> fixture.softAsserts.isTrue(false).statusCode(200));
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}

		AssertionError failure = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.completeTestClass(fixture));
		assertAggregate(failure, true);
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.completeTestClass(fixture),
				"Automatic class completion must consume the collector exactly once");
	}

	private static void assertAggregate(AssertionError failure, boolean requireMethodPrefix) {
		String message = failure.getMessage();
		assertTrue(message.contains("The following asserts failed:"), "Actual message: " + message);
		assertTrue(
				message.contains("Condition should be true") || message.contains("expected [true] but found [false]"),
				"Actual message: " + message);
		assertTrue(message.contains("Status code mismatch: expected [200] but found [201]"),
				"Actual message: " + message);
		if (requireMethodPrefix) {
			String prefix = "CompleteUseCaseFixture.softAsserts: ";
			int assertionLines = 0;
			for (String line : message.split("\\R")) {
				if (line.contains("expected [true] but found [false]")
						|| line.contains("Status code mismatch: expected [200] but found [201]")) {
					assertionLines++;
					assertTrue(line.contains(prefix),
							"Each collected assertion line must include its originating method: " + line);
				}
			}
			assertEquals(2, assertionLines,
					"The aggregate must contain exactly the two collected assertion lines: " + message);
		}
	}

	private static final class CompleteUseCaseFixture {
		private final ExplicitSoftVerificationContext context = new ExplicitSoftVerificationContext(201);

		@JPostman.AssertContext(soft = true)
		private final JPostman.Assert softAsserts = JPostmanTestProxy.wrapAssert(() -> context, true, true);

		@SuppressWarnings("unused")
		void assertsInternalSoft() {
		}

		@SuppressWarnings("unused")
		void jpostmanInternalSoft() {
		}

		@SuppressWarnings("unused")
		void softAsserts() {
		}
	}

	private static class HardFixture {
		final AssertHandler handler = new AssertHandler(false);

		@JPostman.AssertContext
		JPostman.Assert asserts = handler.proxy();
	}

	private static final class SoftFixture {
		final AssertHandler handler = new AssertHandler(true);

		@JPostman.AssertContext(soft = true)
		JPostman.Assert asserts = handler.proxy();
	}

	private static final class OriginFixture {
		@SuppressWarnings("unused")
		void testAuthRunner() {
		}
	}

	private static final class DemoOrigin {
		@SuppressWarnings("unused")
		void testAuthRunner() {
		}
	}

	private static final class ReportFixture {
		@JPostman.ReportContext
		JPostman.Report report = new JPostmanReport();
	}

	private static final class AssertHandler implements InvocationHandler {
		private final boolean soft;
		private final List<AssertionError> failures;
		private final Integer actualStatusCode;
		private int verifyCalls;

		private AssertHandler(boolean soft) {
			this(soft, new ArrayList<>(), null);
		}

		private AssertHandler(boolean soft, int actualStatusCode) {
			this(soft, new ArrayList<>(), actualStatusCode);
		}

		private AssertHandler(boolean soft, List<AssertionError> failures) {
			this(soft, failures, null);
		}

		private AssertHandler(boolean soft, List<AssertionError> failures, Integer actualStatusCode) {
			this.soft = soft;
			this.failures = failures;
			this.actualStatusCode = actualStatusCode;
		}

		private JPostman.Assert proxy() {
			return (JPostman.Assert) Proxy.newProxyInstance(JPostman.Assert.class.getClassLoader(),
					new Class<?>[] { JPostman.Assert.class }, this);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			String name = method.getName();
			if ("soft".equals(name)) {
				return new AssertHandler(true, failures, actualStatusCode).proxy();
			}
			if ("statusCode".equals(name) && args != null && args.length > 0 && actualStatusCode != null) {
				int expected = ((Number) args[0]).intValue();
				if (expected != actualStatusCode.intValue()) {
					AssertionError failure = new AssertionError(
							"Status code mismatch: expected [" + expected + "] but found [" + actualStatusCode + "]");
					if (soft) {
						failures.add(failure);
						return proxy;
					}
					throw failure;
				}
				return proxy;
			}
			if ("isTrue".equals(name) && args != null && args.length > 0 && Boolean.FALSE.equals(args[0])) {
				AssertionError failure = new AssertionError("expected [true] but found [false]");
				if (soft) {
					failures.add(failure);
					return proxy;
				}
				throw failure;
			}
			if ("verify".equals(name) || "assertAll".equals(name)) {
				verifyCalls++;
				if (!failures.isEmpty()) {
					StringBuilder message = new StringBuilder("The following asserts failed:");
					for (AssertionError failure : failures) {
						message.append("\n\t").append(failure.getMessage());
					}
					AssertionError aggregate = new AssertionError(message.toString());
					failures.clear();
					throw aggregate;
				}
				return proxy;
			}
			if ("toString".equals(name)) {
				return "AssertHandler";
			}
			if ("hashCode".equals(name)) {
				return System.identityHashCode(proxy);
			}
			if ("equals".equals(name)) {
				return proxy == args[0];
			}
			return method.getReturnType() == JPostman.Assert.class ? proxy : null;
		}
	}
}
