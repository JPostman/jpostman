package io.jpostman.annotations.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.jpostman.Collection;

final class PreparedContexts<C> {

	@FunctionalInterface
	interface MissingContextFactory<C> {
		PreparedContext<C> create(String namespace) throws Exception;
	}

	private final Map<String, PreparedContext<C>> values = new ConcurrentHashMap<>();
	private final List<PreparedContext<C>> active = new ArrayList<>();
	private MissingContextFactory<C> missingContextFactory;
	private JPostmanInfo info;

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
		if (context != null) {
			return context;
		}

		if (missingContextFactory != null) {
			try {
				PreparedContext<C> created = missingContextFactory.create(key);
				if (created != null) {
					PreparedContext<C> existing = values.putIfAbsent(key, created);
					return existing == null ? created : existing;
				}
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException("Unable to create JPostman runtime context for namespace: " + key, e);
			}
		}

		throw new IllegalStateException("No JPostman runtime context found for namespace: " + key);
	}

	C context(String namespace) {
		return resolve(namespace).context;
	}

	C firstContext() {
		PreparedContext<C> context = firstPreparedContext();
		return context.context;
	}

	PreparedContext<C> firstPreparedContext() {
		if (values.isEmpty()) {
			throw new IllegalStateException("No JPostman runtime context found.");
		}
		return values.values().iterator().next();
	}

	Collection collection(String namespace) {
		return resolve(namespace).collection;
	}

	List<C> contexts() {
		List<C> contexts = new ArrayList<>();
		for (PreparedContext<C> value : values.values()) {
			contexts.add(value.context);
		}
		return contexts;
	}

	void update(String namespace, C context) {
		resolve(namespace).update(context);
		updateActive(context);
	}

	void missingContextFactory(MissingContextFactory<C> missingContextFactory) {
		this.missingContextFactory = missingContextFactory;
	}

	void info(JPostmanInfo info) {
		this.info = info;
	}

	JPostmanInfo info() {
		return info;
	}

	void addActive(PreparedContext<C> context) {
		active.add(context);
	}

	void updateActive(C context) {
		for (PreparedContext<C> value : active) {
			value.update(context);
		}
	}

	private String normalize(String namespace) {
		return namespace == null ? "" : namespace;
	}
}
