package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;

/** Regression contract for the request-scoped AssertContext lifecycle. */
public class JPostmanAssertContextLifecycleContractTest {

	@Test
	void publicAssertFacadeExposesVerifyButNotSoftOrAssertAll() throws Exception {
		assertNotNull(JPostman.Assert.class.getMethod("verify"));
		assertThrows(NoSuchMethodException.class, () -> JPostman.Assert.class.getMethod("soft"));
		assertThrows(NoSuchMethodException.class, () -> JPostman.Assert.class.getMethod("assertAll"));
		assertEquals(JPostman.Test.class, JPostman.Assert.class.getMethod("verify").getReturnType());
	}

	@Test
	void compactAssertsAliasIsRemoved() {
		for (Class<?> nested : JPostman.class.getDeclaredClasses()) {
			assertFalse("Asserts".equals(nested.getSimpleName()), "JPostman.Asserts must not remain public API");
		}
	}

	@Test
	void responseAndRunnerAnnotationsNoLongerExposeSoft() {
		assertThrows(NoSuchMethodException.class,
				() -> io.jpostman.annotations.JPostmanResponse.class.getMethod("soft"));
		assertThrows(NoSuchMethodException.class, () -> io.jpostman.annotations.JPostmanRunner.class.getMethod("soft"));
		assertThrows(NoSuchMethodException.class, () -> JPostman.Response.class.getMethod("soft"));
		assertThrows(NoSuchMethodException.class, () -> JPostman.Runner.class.getMethod("soft"));
	}

	@Test
	void hardAssertContextFailsImmediatelyAndVerifyIsNoOp() {
		ProxyContext context = new ProxyContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);

		assertThrows(AssertionError.class, () -> asserts.isTrue(false));
		assertDoesNotThrow(() -> {
			asserts.verify();
		});
		assertEquals(1, context.hardCalls);
		assertEquals(0, context.softCalls);
	}

	@Test
	void softAssertContextCollectsUntilManualVerifyAndThenClears() {
		ProxyContext context = new ProxyContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, true, true);

		assertDoesNotThrow(() -> asserts.isTrue(false).statusCode(200));
		AssertionError failure = assertThrows(AssertionError.class, asserts::verify);
		assertAggregate(failure);
		assertDoesNotThrow(() -> {
			asserts.verify();
		}, "manual verify must consume the collector");
		assertEquals(1, context.softCalls);
		assertEquals(0, context.hardCalls);
	}

	@Test
	void automaticVerificationFlushesInjectedSoftContext() {
		SoftFixture fixture = new SoftFixture();
		assertDoesNotThrow(() -> fixture.asserts.isTrue(false).statusCode(200));

		AssertionError failure = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.verifySoftAssertContexts(fixture));
		assertAggregate(failure);
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.verifySoftAssertContexts(fixture));
	}

	@Test
	void manualVerificationPreventsDuplicateAutomaticFailure() {
		SoftFixture fixture = new SoftFixture();
		fixture.asserts.isTrue(false);

		assertThrows(AssertionError.class, fixture.asserts::verify);
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.verifySoftAssertContexts(fixture));
	}

	@Test
	void classCompletionDoesNotVerifyPendingSoftAssertions() {
		SoftFixture fixture = new SoftFixture();
		fixture.asserts.isTrue(false);

		assertDoesNotThrow(() -> JPostmanAnnotationEngine.completeTestClass(fixture));
		assertThrows(AssertionError.class, () -> JPostmanAnnotationEngine.verifySoftAssertContexts(fixture));
	}

	@Test
	void automaticVerificationSkipsHardAssertContexts() {
		HardFixture fixture = new HardFixture();
		assertThrows(AssertionError.class, () -> fixture.asserts.isTrue(false));
		assertDoesNotThrow(() -> JPostmanAnnotationEngine.verifySoftAssertContexts(fixture));
	}

	@Test
	void automaticVerificationAggregatesMultipleSoftFields() {
		MultipleSoftFixture fixture = new MultipleSoftFixture();
		fixture.first.isTrue(false);
		fixture.second.statusCode(200);

		AssertionError failure = assertThrows(AssertionError.class,
				() -> JPostmanAnnotationEngine.verifySoftAssertContexts(fixture));
		assertTrue(failure.getMessage().contains("expected [true] but found [false]"));
		assertEquals(1, failure.getSuppressed().length);
		assertTrue(failure.getSuppressed()[0].getMessage().contains("Status code mismatch"));
	}

	@Test
	void automaticFailureOmitsRedundantOriginatingMethod() throws Exception {
		ProxyContext context = new ProxyContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, true, true);
		OriginFixture fixture = new OriginFixture(asserts);
		Method method = OriginFixture.class.getDeclaredMethod("response");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, method);
		try {
			asserts.isTrue(false);
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}

		AssertionError failure = assertThrows(AssertionError.class, asserts::verify);
		AssertionError cleaned = JPostmanAnnotationEngine.cleanFailure(fixture, method, failure);
		assertFalse(cleaned.getMessage().contains("OriginFixture.response:"), cleaned.getMessage());
		assertFalse(cleaned.getMessage().contains("OriginFixture::response:"), cleaned.getMessage());
	}

	private static void assertAggregate(AssertionError failure) {
		String message = failure.getMessage();
		assertTrue(message.contains("The following asserts failed:"), message);
		assertTrue(message.contains("expected [true] but found [false]"), message);
		assertTrue(message.contains("Status code mismatch: expected [200] but found [201]"), message);
	}

	private static final class HardFixture {
		@JPostman.AssertContext
		private final JPostman.Assert asserts = new AssertHandler(false, 201).proxy();
	}

	private static final class SoftFixture {
		@JPostman.AssertContext(soft = true)
		private final JPostman.Assert asserts = new AssertHandler(true, 201).proxy();
	}

	private static final class MultipleSoftFixture {
		@JPostman.AssertContext(soft = true)
		private final JPostman.Assert first = new AssertHandler(true, 201).proxy();

		@JPostman.AssertContext(soft = true)
		private final JPostman.Assert second = new AssertHandler(true, 201).proxy();
	}

	private static final class OriginFixture {
		@JPostman.AssertContext(soft = true)
		private final JPostman.Assert asserts;

		private OriginFixture(JPostman.Assert asserts) {
			this.asserts = asserts;
		}

		@SuppressWarnings("unused")
		private void response() {
		}
	}

	public static final class ProxyContext {
		private final AssertHandler soft = new AssertHandler(true, 201);
		private final AssertHandler hard = new AssertHandler(false, 201);
		private int softCalls;
		private int hardCalls;

		public JPostman.Assert soft(boolean secure) {
			softCalls++;
			return soft.proxy();
		}

		public JPostman.Assert asserts(boolean secure) {
			hardCalls++;
			return hard.proxy();
		}
	}

	private static final class AssertHandler implements InvocationHandler {
		private final boolean soft;
		private final int actualStatusCode;
		private final List<AssertionError> failures = new ArrayList<>();

		private AssertHandler(boolean soft, int actualStatusCode) {
			this.soft = soft;
			this.actualStatusCode = actualStatusCode;
		}

		private JPostman.Assert proxy() {
			return (JPostman.Assert) Proxy.newProxyInstance(JPostman.Assert.class.getClassLoader(),
					new Class<?>[] { JPostman.Assert.class }, this);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			String name = method.getName();
			if ("isTrue".equals(name) && args != null && args.length > 0 && Boolean.FALSE.equals(args[0])) {
				return failOrCollect(proxy, new AssertionError("expected [true] but found [false]"));
			}
			if ("statusCode".equals(name) && args != null && args.length > 0) {
				int expected = ((Number) args[0]).intValue();
				if (expected != actualStatusCode) {
					return failOrCollect(proxy, new AssertionError(
							"Status code mismatch: expected [" + expected + "] but found [" + actualStatusCode + "]"));
				}
				return proxy;
			}
			if ("verify".equals(name)) {
				if (!failures.isEmpty()) {
					StringBuilder message = new StringBuilder("The following asserts failed:");
					for (AssertionError failure : failures) {
						message.append("\n\t").append(failure.getMessage());
					}
					failures.clear();
					throw new AssertionError(message.toString());
				}
				/* verify() returns JPostman.Test; null is valid for this isolated fixture. */
				return null;
			}
			if ("toString".equals(name)) {
				return "AssertHandler";
			}
			if ("hashCode".equals(name)) {
				return System.identityHashCode(proxy);
			}
			if ("equals".equals(name)) {
				return proxy == (args == null ? null : args[0]);
			}
			return proxy;
		}

		private Object failOrCollect(Object proxy, AssertionError failure) {
			if (!soft) {
				throw failure;
			}
			failures.add(failure);
			return proxy;
		}
	}
}
