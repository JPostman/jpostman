package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;

import io.jpostman.annotations.JPostman;

class JPostmanResponseSnapshotRegressionTest {

	@Test
	void snapshotKeepsResponseAfterOriginalContextChanges() {
		MutableContext original = new MutableContext(Map.of("accessToken", "token-1"));

		JPostman.Test snapshot = JPostmanResponseSnapshot.create(original);
		original.response = Map.of("username", "emilys");

		assertEquals("token-1", snapshot.path("accessToken"));
		assertEquals("token-1", snapshot.path("$/accessToken"));
		assertNotSame(original, JPostmanTestProxy.unwrap(snapshot));
	}

	@Test
	void cacheSlashPathResolvesCachedSnapshot() {
		MutableContext login = new MutableContext(Map.of("accessToken", "token-2", "user", Map.of("id", 7)));
		CacheContext active = new CacheContext();
		active.values.put("token", JPostmanResponseSnapshot.create(login));

		JPostman.Test test = JPostmanTestProxy.wrap(active);

		assertEquals("token-2", test.cache("token/accessToken"));
		assertEquals(7, test.cache("token/user/id", Integer.class));
	}

	@Test
	void cacheSlashPathUnwrapsJsonPrimitiveAndSupportsTypedConversion() {
		CacheContext active = new CacheContext();
		active.values.put("token", new JsonPathContext(Map.of("accessToken", new JsonPrimitive("token-json"), "userId",
				new JsonPrimitive(17), "active", new JsonPrimitive(true))));

		JPostman.Test test = JPostmanTestProxy.wrap(active);

		String token = test.cache("token/accessToken");
		assertEquals("token-json", token);
		assertEquals("token-json", test.cache("token/accessToken", String.class));
		assertEquals(17, test.cache("token/userId", Integer.class));
		assertEquals(true, test.cache("token/active", Boolean.class));
	}

	public static final class MutableContext {
		private Object response;

		MutableContext(Object response) {
			this.response = response;
		}

		public Object path(String path) {
			if (path == null || path.isBlank() || "$".equals(path)) {
				return response;
			}
			if (response instanceof Map<?, ?>) {
				return ((Map<?, ?>) response).get(path);
			}
			return null;
		}
	}

	public static final class JsonPathContext {
		private final Map<String, Object> response;

		JsonPathContext(Map<String, Object> response) {
			this.response = response;
		}

		public Object path(String path) {
			return response.get(path);
		}
	}

	public static final class CacheContext {
		private final Map<String, Object> values = new LinkedHashMap<>();

		public Object cache(String key) {
			return values.get(key);
		}
	}
}
