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
		JPostman.Assert methodSoft = fixture.asserts.soft(true);
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
		JPostman.Assert methodSoft = fixture.asserts.soft(true);
		assertDoesNotThrow(() -> methodSoft.isTrue(false));
		assertThrows(AssertionError.class, () -> fixture.asserts.isTrue(false));
		assertThrows(AssertionError.class, methodSoft::verify);
	}

	@Test
	void case8ExplicitVerifyFlushesImmediatelyEvenWithSoftFieldAndRunner() {
		SoftFixture fixture = new SoftFixture();
		assertDoesNotThrow(() -> fixture.asserts.soft(true).isTrue(false));
		assertThrows(AssertionError.class, fixture.asserts::verify);
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
		JPostman.Assert methodSoft = JPostmanTestProxy.wrapAssert(() -> context, false, false).soft(true);

		JPostmanRuntimeRunner.begin(List.of("Login"));
		try {
			JPostmanRuntimeRunner.afterRequest(0, "Login");
			assertDoesNotThrow(() -> methodSoft.isTrue(false));
			assertTrue(JPostmanRuntimeRunner.hasSoftFailure(),
					"soft(true) returned from a hard AssertContext must remain behind the Runner-aware facade");
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
		private int verifyCalls;

		private AssertHandler(boolean soft) {
			this(soft, new ArrayList<>());
		}

		private AssertHandler(boolean soft, List<AssertionError> failures) {
			this.soft = soft;
			this.failures = failures;
		}

		private JPostman.Assert proxy() {
			return (JPostman.Assert) Proxy.newProxyInstance(JPostman.Assert.class.getClassLoader(),
					new Class<?>[] { JPostman.Assert.class }, this);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			String name = method.getName();
			if ("soft".equals(name)) {
				return new AssertHandler(true, failures).proxy();
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
					AssertionError aggregate = new AssertionError(failures.get(0).getMessage());
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
