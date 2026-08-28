package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testng.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;

/** Regression coverage for dependency-aware JPostman.Test.cache expressions. */
public class JPostmanTestProxyCacheExpressionRegressionTest {

	@Test
	public void annotationIdReferenceResolvesEffectiveCustomCacheKey() {
		FakeContext context = new FakeContext();
		context.cache("MY_TOKEN", new FakeResponse(Map.of("level1", Map.of("accessToken", "token-123"))));
		context.cache(JPostmanTestProxy.cacheAliasKey("Ref1"), "MY_TOKEN");
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		assertEquals("token-123", test.cache("#Ref1:level1/accessToken"));
		assertTrue(test.cache("#Ref1") instanceof FakeResponse);
		assertEquals("token-123", test.cache("MY_TOKEN/level1/accessToken"));
	}

	@Test
	public void getUsesSecretPlainCacheEnvironmentPrecedence() {
		FakeContext context = new FakeContext();
		context.cache("token", "cached-token");
		JPostmanTestProxy.registerEnvironmentValues(context, Map.of("token", "environment-token"));
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		assertEquals("cached-token", test.get("token"));

		test.plain("token", "plain-token");
		assertEquals("plain-token", test.get("token"));

		test.secret("token", "secret-token");
		assertEquals("secret-token", test.get("token"));

		// A later plain value cannot override a secret until unsecret is called.
		test.plain("token", "ignored-plain-token");
		assertEquals("secret-token", test.get("token"));

		test.unsecret("token").plain("token", "replacement-plain-token");
		assertEquals("replacement-plain-token", test.get("token"));
	}

	@Test
	public void getSupportsGenericInferenceAndExplicitConversion() {
		FakeContext context = new FakeContext();
		context.cache("name", "JPostman");
		context.cache("attempts", 3);
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		String name = test.get("name");
		Integer attempts = test.get("attempts");
		Long convertedAttempts = test.get("attempts", Long.class);

		assertEquals("JPostman", name);
		assertEquals(Integer.valueOf(3), attempts);
		assertEquals(Long.valueOf(3L), convertedAttempts);
	}

	@Test
	public void getFallsBackFromCacheExpressionToEnvironment() {
		FakeContext context = new FakeContext();
		JPostmanTestProxy.registerEnvironmentValues(context,
				Map.of("accessToken", "environment-token", "region", "environment-region"));
		context.cache("Ref1", new FakeResponse(Map.of("accessToken", "cached-access-token")));
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		try (JPostmanTestProxy.CacheScope ignored = JPostmanTestProxy
				.openCacheScope(List.of(new JPostmanTestProxy.CacheDependency("#Ref1", "Ref1")))) {
			assertEquals("cached-access-token", test.get("accessToken"));
			assertEquals("environment-region", test.get("region"));
			Object missing = test.get("missing");
			assertEquals(null, missing);
		}
	}

	@Test
	public void cacheWriteOverloadRemainsSupported() {
		FakeContext context = new FakeContext();
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		assertTrue(test == test.cache("token", "token-123"));
		test.cache("type", (Object) String.class);
		test.cache("optional", (Object) null);

		assertEquals("token-123", test.cache("token", String.class));
		assertEquals(String.class, test.cache("type"));
		assertTrue(context.containsCacheKey("optional"));
	}

	@Test
	public void exactCacheKeyWinsBeforeImplicitPathResolution() {
		FakeContext context = new FakeContext();
		context.cache("token", "token-123");
		context.cache(JPostmanTestProxy.cacheAliasKey("Ref1"), "token");
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		try (JPostmanTestProxy.CacheScope ignored = JPostmanTestProxy
				.openCacheScope(List.of(new JPostmanTestProxy.CacheDependency("#Ref1", "token")))) {
			assertEquals("token-123", test.cache("token"));
			assertEquals("token-123", test.cache("#Ref1"));
		}
	}

	@Test
	public void implicitPathUsesOnlyCachedDirectDependency() {
		FakeContext context = new FakeContext();
		context.cache("__loginPrimary__", new FakeResponse(Map.of("accessToken", "primary")));
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		try (JPostmanTestProxy.CacheScope ignored = JPostmanTestProxy
				.openCacheScope(List.of(new JPostmanTestProxy.CacheDependency("loginPrimary", "__loginPrimary__"),
						new JPostmanTestProxy.CacheDependency("#Ref2", "Ref2")))) {
			assertEquals("primary", test.cache("accessToken"));
		}
	}

	@Test
	public void unresolvedOrdinaryCacheKeyRemainsNull() {
		FakeContext context = new FakeContext();
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		try (JPostmanTestProxy.CacheScope ignored = JPostmanTestProxy
				.openCacheScope(List.of(new JPostmanTestProxy.CacheDependency("#Ref1", "Ref1")))) {
			assertEquals(null, (Object) test.cache("missing"));
		}
	}

	@Test
	public void implicitPathRejectsMultipleCachedDirectDependencies() {
		FakeContext context = new FakeContext();
		context.cache("Ref1", new FakeResponse(Map.of("accessToken", "one")));
		context.cache("Ref2", new FakeResponse(Map.of("accessToken", "two")));
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		try (JPostmanTestProxy.CacheScope ignored = JPostmanTestProxy
				.openCacheScope(List.of(new JPostmanTestProxy.CacheDependency("#Ref1", "Ref1"),
						new JPostmanTestProxy.CacheDependency("#Ref2", "Ref2")))) {
			IllegalStateException error = assertThrows(IllegalStateException.class, () -> test.cache("accessToken"));
			assertTrue(error.getMessage().contains("#Ref1"));
			assertTrue(error.getMessage().contains("#Ref2"));
		}
	}

	@Test
	public void getReadsUncachedResponseByIdAndRefWithoutPlainWrites() {
		Object owner = new Object();
		FakeContext context = new FakeContext();
		JPostman.Test test = JPostmanTestProxy.wrap(context, null, null, owner);
		try {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("accountId", "account-1");
			JPostmanTestProxy.recordResponse(owner, "#Ref", new SnapshotInput(body));
			body.put("accountId", "mutated");
			for (String expression : List.of("#Ref/accountId", "#Ref:/accountId", "Ref:/accountId", "#Ref:accountId")) {
				assertEquals("account-1", test.get(expression));
			}
			assertTrue(context.configured.isEmpty());
			assertTrue(context.values.isEmpty());
			assertThrows(IllegalStateException.class, () -> test.cache("Ref:/accountId"));
			assertThrows(IllegalStateException.class, () -> test.get("Ref:/missing"));
			JPostman.Test other = JPostmanTestProxy.wrap(new FakeContext(), null, null, new Object());
			assertThrows(IllegalStateException.class, () -> other.get("Ref:/accountId"));
			JPostmanTestProxy.recordResponse(owner, "Ref", new SnapshotInput(Map.of("accountId", "account-2")));
			assertEquals("account-2", test.get("Ref:/accountId"));
			JPostmanTestProxy.clearResponse(owner, "#Ref");
			assertThrows(IllegalStateException.class, () -> test.get("Ref:/accountId"));
		} finally {
			JPostmanTestProxy.clearRuntimeValues(owner);
		}
	}

	@Test
	public void responseReferenceChecksAnnotationCacheAndExplicitValuesFirst() {
		Object owner = new Object();
		FakeContext context = new FakeContext();
		String key = "Ref:/accountId";
		JPostman.Test test = JPostmanTestProxy.wrap(context, null, null, owner);
		try {
			JPostmanTestProxy.recordResponse(owner, "Ref", new SnapshotInput(Map.of("accountId", "captured")));
			JPostmanTestProxy.registerEnvironmentValues(context, Map.of(key, "env"));
			assertEquals("env", test.get(key));
			context.cache(JPostmanTestProxy.cacheAliasKey("Ref"), "CUSTOM_CACHE");
			context.cache("CUSTOM_CACHE", new FakeResponse(Map.of("accountId", "cached")));
			assertEquals("cached", test.get(key));
			assertEquals("cached", test.cache(key));
			assertEquals("cached", test.cache("#Ref/accountId"));
			context.cache(key, "exact-cache");
			assertEquals("exact-cache", test.get(key));
			test.plain(key, "plain");
			assertEquals("plain", test.get(key));
			test.secret(key, "secret");
			assertEquals("secret", test.get(key));
		} finally {
			JPostmanTestProxy.clearRuntimeValues(owner);
		}
	}

	@Test
	public void scalarCacheDoesNotHideFullResponseFallback() {
		Object owner = new Object();
		FakeContext context = new FakeContext();
		JPostman.Test test = JPostmanTestProxy.wrap(context, null, null, owner);
		try {
			context.cache(JPostmanTestProxy.cacheAliasKey("Ref"), "TOKEN");
			context.cache("TOKEN", "scalar-access-token");
			JPostmanTestProxy.recordResponse(owner, "Ref", new SnapshotInput(Map.of("refreshToken", "refresh")));
			assertEquals("refresh", test.get("#Ref/refreshToken"));
			JPostmanTestProxy.clearRuntimeValues(owner);
			assertThrows(IllegalStateException.class, () -> test.get("#Ref/refreshToken"));
		} finally {
			JPostmanTestProxy.clearRuntimeValues(owner);
		}
	}

	public static final class SnapshotInput {
		private final Object root;

		public SnapshotInput(Object root) {
			this.root = root;
		}

		public Object path(String path) {
			return root;
		}
	}

	private static final class FakeContext {
		private final Map<String, Object> values = new LinkedHashMap<>();
		private final Map<String, Object> configured = new LinkedHashMap<>();

		@SuppressWarnings("unused")
		public Object cache(String key) {
			return values.get(key);
		}

		public void cache(String key, Object value) {
			values.put(key, value);
		}

		@SuppressWarnings("unused")
		public FakeContext cache(String key, Object value, Object... ignored) {
			values.put(key, value);
			return this;
		}

		@SuppressWarnings("unused")
		public FakeContext plain(Object... pairs) {
			putPairs(pairs);
			return this;
		}

		@SuppressWarnings("unused")
		public FakeContext secret(Object... pairs) {
			putPairs(pairs);
			return this;
		}

		@SuppressWarnings("unused")
		public FakeContext unsecret(String... names) {
			return this;
		}

		private void putPairs(Object[] pairs) {
			if (pairs == null) {
				return;
			}
			for (int index = 0; index + 1 < pairs.length; index += 2) {
				configured.put(String.valueOf(pairs[index]), pairs[index + 1]);
			}
		}

		private boolean containsCacheKey(String key) {
			return values.containsKey(key);
		}

		@SuppressWarnings("unused")
		public Object get(String key) {
			return configured.get(key);
		}
	}

	private static final class FakeResponse {
		private final Object root;

		private FakeResponse(Object root) {
			this.root = root;
		}

		@SuppressWarnings("unused")
		public Object path(String expression) {
			Object current = root;
			for (String part : expression.split("[/.]")) {
				if (part.isBlank()) {
					continue;
				}
				if (!(current instanceof Map<?, ?>)) {
					return null;
				}
				current = ((Map<?, ?>) current).get(part);
			}
			return current;
		}
	}
}
