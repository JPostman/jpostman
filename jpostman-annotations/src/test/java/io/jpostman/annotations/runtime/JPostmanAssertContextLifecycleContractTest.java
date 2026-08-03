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
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;

/** Regression contract for the request-scoped AssertContext lifecycle. */
public class JPostmanAssertContextLifecycleContractTest {

	@Test
	void publicAssertFacadeExposesTemporarySoftAndVerifyButNotAssertAll() throws Exception {
		assertNotNull(JPostman.Assert.class.getMethod("soft"));
		assertNotNull(JPostman.Assert.class.getMethod("verify"));
		assertThrows(NoSuchMethodException.class, () -> JPostman.Assert.class.getMethod("assertAll"));
		assertEquals(JPostman.Assert.class, JPostman.Assert.class.getMethod("soft").getReturnType());
		assertEquals(JPostman.Test.class, JPostman.Assert.class.getMethod("verify").getReturnType());
	}

	@Test
	void allMatchMessagesAreOptionalForEveryOverload() throws Exception {
		assertNotNull(JPostman.Assert.class.getMethod("allMatch", String.class, Predicate.class));
		assertNotNull(JPostman.Assert.class.getMethod("allMatch", String.class, BiPredicate.class));
		assertNotNull(JPostman.Assert.class.getMethod("allMatch", String.class, Class.class, BiPredicate.class));
	}

	@Test
	void messageOptionalAllMatchOverloadsDelegateWithBlankMessage() {
		AllMatchContext context = new AllMatchContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);

		Predicate<Number> positive = value -> value.doubleValue() > 0;
		BiPredicate<Object, Integer> nonNull = (value, index) -> value != null;
		BiPredicate<String, Integer> nonBlank = (value, index) -> !value.isBlank();

		asserts.allMatch("prices", positive).allMatch("items", nonNull).allMatch("names", String.class, nonBlank);

		assertEquals(List.of("number:", "object:", "typed:"), context.target.calls);
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
	void temporarySoftIsVerifiedAfterResponseAndOriginalFacadeRemainsHard() throws Exception {
		ProxyContext context = new ProxyContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);
		TemporarySoftFixture fixture = new TemporarySoftFixture(asserts);
		Method response = TemporarySoftFixture.class.getDeclaredMethod("response");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, response);
		try {
			assertDoesNotThrow(() -> asserts.soft().isTrue(false).statusCode(200));
			AssertionError failure = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(response));
			assertAggregate(failure);
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}

		assertThrows(AssertionError.class, () -> asserts.isTrue(false),
				"the injected facade must return to hard behavior for the next test");
		assertEquals(1, context.softCalls);
		assertEquals(1, context.hardCalls);
	}

	@Test
	void temporarySoftIsAutomaticallyEligibleForCallMethods() throws Exception {
		ProxyContext context = new ProxyContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);
		TemporarySoftFixture fixture = new TemporarySoftFixture(asserts);
		Method call = TemporarySoftFixture.class.getDeclaredMethod("call");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, call);
		try {
			assertDoesNotThrow(() -> asserts.soft().isTrue(false));
			AssertionError failure = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(call));
			assertTrue(failure.getMessage().contains("expected [true] but found [false]"), failure.getMessage());
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
	}

	@Test
	void runtimeContextSoftUsesTheSameAutomaticMethodExitVerification() throws Exception {
		RuntimeStyleContext context = new RuntimeStyleContext();
		JPostman.Test test = JPostmanTestProxy.wrap(context);
		TemporarySoftFixture fixture = new TemporarySoftFixture(null);
		Method response = TemporarySoftFixture.class.getDeclaredMethod("response");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, response);
		try {
			assertDoesNotThrow(() -> test.soft().isTrue(false));
			AssertionError failure = assertThrows(AssertionError.class,
					() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(response));
			assertTrue(failure.getMessage().contains("expected [true] but found [false]"), failure.getMessage());
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}

		assertEquals(1, context.softCalls);
	}

	@Test
	void manualTemporarySoftVerifyPreventsDuplicateMethodExitFailure() throws Exception {
		ProxyContext context = new ProxyContext();
		JPostman.Assert asserts = JPostmanTestProxy.wrapAssert(() -> context, false, true);
		TemporarySoftFixture fixture = new TemporarySoftFixture(asserts);
		Method response = TemporarySoftFixture.class.getDeclaredMethod("response");

		JPostmanAnnotationEngine.beginAssertionCleanup(fixture, response);
		try {
			JPostman.Assert temporary = asserts.soft();
			temporary.isTrue(false);
			assertThrows(AssertionError.class, temporary::verify);
			assertDoesNotThrow(() -> JPostmanAnnotationEngine.verifyExplicitSoftAssertions(response));
		} finally {
			JPostmanAnnotationEngine.endAssertionCleanup();
		}
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

	private static final class TemporarySoftFixture {
		@JPostman.AssertContext
		private final JPostman.Assert asserts;

		private TemporarySoftFixture(JPostman.Assert asserts) {
			this.asserts = asserts;
		}

		@JPostman.Response
		private void response() {
		}

		@JPostman.Call
		private void call() {
		}
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

	public static final class AllMatchContext {
		private final AllMatchTarget target = new AllMatchTarget();

		public AllMatchTarget asserts(boolean secure) {
			return target;
		}
	}

	public static final class AllMatchTarget {
		private final List<String> calls = new ArrayList<>();

		public AllMatchTarget allMatch(String path, Predicate<Number> predicate, String message) {
			calls.add("number:" + message);
			return this;
		}

		public AllMatchTarget allMatch(String path, BiPredicate<Object, Integer> predicate, String message) {
			calls.add("object:" + message);
			return this;
		}

		public <V> AllMatchTarget allMatch(String path, Class<V> type, BiPredicate<V, Integer> predicate,
				String message) {
			calls.add("typed:" + message);
			return this;
		}
	}

	public static final class RuntimeStyleContext {
		private final AssertHandler soft = new AssertHandler(true, 201);
		private int softCalls;

		public JPostman.Assert soft() {
			softCalls++;
			return soft.proxy();
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
