package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.ApiResponse;
import io.jpostman.annotations.JPostman;
import io.jpostman.junit.JUnitContext;

/** Regression coverage for class-runtime plain/secret values across methods. */
public class JPostmanAnnotationVariableScopeRegressionTest {

	@Test
	public void classRuntimeValuesRemainVisibleFromAnUnrelatedFreshContext() {
		Object owner = new Object();
		JUnitContext responseContext = JUnitContext.create();
		JPostman.Test responseTest = JPostmanTestProxy.wrap(responseContext, null, null, owner);

		try {
			responseTest.secret("refreshToken", "refresh-123");
			responseTest.plain("plainToken", "plain-123");

			/*
			 * Reproduce the annotation lifecycle boundary directly: runtime.test() may
			 * point at a newly prepared context before runtime.call() has activated the
			 * dependent request context. Class-runtime values must not depend on the old
			 * context object being the copy source.
			 */
			JUnitContext nextMethodContext = JUnitContext.create();
			JPostman.Test nextMethodTest = JPostmanTestProxy.wrap(nextMethodContext, null, null, owner);

			assertEquals("refresh-123", nextMethodTest.get("refreshToken"));
			assertEquals("plain-123", nextMethodTest.get("plainToken"));
		} finally {
			JPostmanTestProxy.clearRuntimeValues(owner);
		}
	}

	@Test
	public void responsePlainAndSecretValuesPersistIntoFollowingCall() throws Exception {
		VariableScopeFixture fixture = new VariableScopeFixture();

		JPostmanAnnotationEngine.setupJUnit(fixture);

		Method login = VariableScopeFixture.class.getDeclaredMethod("loginUserAndGetAccessRefreshTokens");
		JPostmanAnnotationEngine.runJUnitExternalResponse(fixture, login);

		assertEquals("refresh-123", fixture.secretImmediatelyAfterWrite);
		assertEquals("plain-123", fixture.plainImmediatelyAfterWrite);

		Method refresh = VariableScopeFixture.class.getDeclaredMethod("refreshAuthSessionToken");
		JPostmanAnnotationEngine.runJUnit(fixture, refresh);
		fixture.refreshAuthSessionToken();

		assertEquals("refresh-123", fixture.secretBeforeCall,
				"secret(...) must remain available to the next annotated method in the same test class.");
		assertEquals("plain-123", fixture.plainBeforeCall,
				"plain(...) must remain available to the next annotated method in the same test class.");
		assertEquals("refresh-123", fixture.secretInsideCall,
				"runtime.call(...) must receive the same persistent secret value.");
		assertEquals("plain-123", fixture.plainInsideCall,
				"runtime.call(...) must receive the same persistent plain value.");

		Method secondCall = VariableScopeFixture.class.getDeclaredMethod("readValuesAgain");
		JPostmanAnnotationEngine.runJUnit(fixture, secondCall);
		fixture.readValuesAgain();

		assertEquals("refresh-123", fixture.secretAfterCompletedCall,
				"secret(...) must survive request/response context transformations and another method boundary.");
		assertEquals("plain-123", fixture.plainAfterCompletedCall,
				"plain(...) must survive request/response context transformations and another method boundary.");
	}

	@JPostman.JUnit
	private static final class VariableScopeFixture {

		@JPostman.Context(verifyStatusCode = 200)
		private JPostman.Runtime<JPostman.Test> runtime;

		private String secretImmediatelyAfterWrite;
		private String plainImmediatelyAfterWrite;
		private String secretBeforeCall;
		private String plainBeforeCall;
		private String secretInsideCall;
		private String plainInsideCall;
		private String secretAfterCompletedCall;
		private String plainAfterCompletedCall;

		@JPostman.Response(id = "Ref1", request = "Login user and get tokens", cache = "")
		public String loginUserAndGetAccessRefreshTokens() {
			JPostman.Test test = runtime.test();
			test.secret("refreshToken", test.path("refreshToken"));
			test.plain("plainToken", "plain-123");

			secretImmediatelyAfterWrite = test.get("refreshToken");
			plainImmediatelyAfterWrite = test.get("plainToken");
			return test.path("accessToken");
		}

		@JPostman.Call(request = "Get current auth user", dependsOn = "#Ref1")
		public void refreshAuthSessionToken() {
			secretBeforeCall = runtime.test().get("refreshToken");
			plainBeforeCall = runtime.test().get("plainToken");

			runtime.call((test, info) -> {
				secretInsideCall = test.get("refreshToken");
				plainInsideCall = test.get("plainToken");
			});
		}

		@JPostman.Call(request = "Get current auth user")
		public void readValuesAgain() {
			secretAfterCompletedCall = runtime.test().get("refreshToken");
			plainAfterCompletedCall = runtime.test().get("plainToken");
			runtime.call();
		}

		@JPostman.Executor
		public ApiExecutor defaultExecutor(JUnitContext ctx, JPostman.Info info) {
			if ("Login user and get tokens".equals(info.request())) {
				return okExecutor("{\"accessToken\":\"access-123\",\"refreshToken\":\"refresh-123\"}");
			}
			return okExecutor("{\"id\":1}");
		}
	}

	private static ApiExecutor okExecutor(String json) {
		return () -> new ApiResponse(200, json, json.getBytes(), Map.of());
	}
}
