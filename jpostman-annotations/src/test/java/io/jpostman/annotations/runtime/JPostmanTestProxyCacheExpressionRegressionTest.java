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
	public void getRemainsSecureValueLookupInsteadOfCacheLookup() {
		FakeContext context = new FakeContext();
		context.cache("token", "cached-token");
		JPostman.Test test = JPostmanTestProxy.wrap(context);

		assertEquals("secure-token", test.get("token"));
		assertEquals("cached-token", test.cache("token"));
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

	private static final class FakeContext {
		private final Map<String, Object> values = new LinkedHashMap<>();

		@SuppressWarnings("unused")
		public Object cache(String key) {
			return values.get(key);
		}

		public void cache(String key, Object value) {
			values.put(key, value);
		}

		private boolean containsCacheKey(String key) {
			return values.containsKey(key);
		}

		@SuppressWarnings("unused")
		public Object get(String key) {
			return "secure-" + key;
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
