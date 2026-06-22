package io.jpostman.annotations.runtime;

import java.util.HashMap;
import java.util.Map;

import io.jpostman.Collection;

final class PreparedContexts<C> {
	private final Map<String, PreparedContext<C>> values = new HashMap<>();

	boolean isEmpty() {
		return values.isEmpty();
	}

	void put(String namespace, PreparedContext<C> context) {
		String key = normalize(namespace);
		if (values.containsKey(key)) {
			throw new IllegalStateException("Duplicate @JPostmanTestContext namespace: " + key);
		}
		values.put(key, context);
	}

	boolean contains(String namespace) {
		return values.containsKey(normalize(namespace));
	}

	PreparedContext<C> resolve(String namespace) {
		String key = normalize(namespace);
		PreparedContext<C> context = values.get(key);
		if (context == null) {
			throw new IllegalStateException("No @JPostmanTestContext found for namespace: " + key);
		}
		return context;
	}

	C context(String namespace) {
		return resolve(namespace).context;
	}

	C firstContext() {
		if (values.isEmpty()) {
			throw new IllegalStateException("No @JPostmanTestContext found.");
		}
		return values.values().iterator().next().context;
	}

	Collection collection(String namespace) {
		return resolve(namespace).collection;
	}

	void update(String namespace, C context) {
		resolve(namespace).update(context);
	}

	private String normalize(String namespace) {
		return namespace == null ? "" : namespace;
	}
}
