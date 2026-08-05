package io.jpostman.annotations.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Thread-local registry for manual @JPostman.Call execution and temporary
 * void-executor proceed scopes.
 */
final class JPostmanRuntimeCall {

	private static final ThreadLocal<Map<Key, Entry<?>>> CALLS = ThreadLocal.withInitial(LinkedHashMap::new);
	private static final ThreadLocal<Map<Key, Deque<Entry<?>>>> ACTIVE = ThreadLocal.withInitial(LinkedHashMap::new);
	private static final ThreadLocal<Function<Throwable, Throwable>> FAILURE_CLEANER = new ThreadLocal<>();
	private static final ThreadLocal<Throwable> FAILURE_SOURCE = new ThreadLocal<>();

	private JPostmanRuntimeCall() {
	}

	static <C> void register(Object owner, Class<?> contextType, JPostmanRuntimeRequest<C> request,
			Function<Throwable, Throwable> failureCleaner) {
		if (owner != null && contextType != null && request != null) {
			CALLS.get().put(new Key(owner, contextType), new Entry<>(request, failureCleaner));
			setFailureCleaner(failureCleaner);
		}
	}

	static <C> void activate(Object owner, Class<?> contextType, JPostmanRuntimeRequest<C> request) {
		if (owner == null || contextType == null || request == null) {
			return;
		}
		Key key = new Key(owner, contextType);
		Entry<?> current = current(key);
		Function<Throwable, Throwable> cleaner = current == null ? null : current.failureCleaner;
		ACTIVE.get().computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(new Entry<>(request, cleaner));
		restoreFailureCleaner(key);
	}

	static void deactivate(Object owner, Class<?> contextType) {
		if (owner == null || contextType == null) {
			return;
		}
		Key key = new Key(owner, contextType);
		Deque<Entry<?>> entries = ACTIVE.get().get(key);
		if (entries != null) {
			if (!entries.isEmpty()) {
				entries.removeLast();
			}
			if (entries.isEmpty()) {
				ACTIVE.get().remove(key);
			}
		}
		restoreFailureCleaner(key);
	}

	static void clear(Object owner, Class<?> contextType) {
		if (owner != null && contextType != null) {
			Key key = new Key(owner, contextType);
			CALLS.get().remove(key);
			ACTIVE.get().remove(key);
			if (CALLS.get().isEmpty()) {
				CALLS.remove();
			}
			if (ACTIVE.get().isEmpty()) {
				ACTIVE.remove();
			}
			FAILURE_CLEANER.remove();
			FAILURE_SOURCE.remove();
		}
	}

	@SuppressWarnings("unchecked")
	static <C> C execute(Object owner, Class<?> contextType, BiConsumer<C, JPostmanInfo> action) throws Exception {
		Key key = new Key(owner, contextType);
		Entry<?> entry = current(key);
		if (entry == null) {
			throw new IllegalStateException(
					"No active JPostman request is available. runtime.call() may be used from an @JPostman.Call test method or from a void @JPostman.Executor interceptor.");
		}
		setFailureCleaner(entry.failureCleaner);
		return ((JPostmanRuntimeRequest<C>) entry.request).execute(action);
	}

	private static Entry<?> current(Key key) {
		Deque<Entry<?>> active = ACTIVE.get().get(key);
		if (active != null && !active.isEmpty()) {
			return active.peekLast();
		}
		return CALLS.get().get(key);
	}

	private static void restoreFailureCleaner(Key key) {
		Entry<?> entry = current(key);
		setFailureCleaner(entry == null ? null : entry.failureCleaner);
	}

	private static void setFailureCleaner(Function<Throwable, Throwable> cleaner) {
		if (cleaner == null) {
			FAILURE_CLEANER.remove();
		} else {
			FAILURE_CLEANER.set(cleaner);
		}
	}

	static boolean hasFailureCleaner() {
		return FAILURE_CLEANER.get() != null;
	}

	static Throwable clean(Throwable failure) {
		Function<Throwable, Throwable> cleaner = FAILURE_CLEANER.get();
		if (failure != null) {
			FAILURE_SOURCE.set(failure);
		}
		if (cleaner == null || failure == null) {
			return failure;
		}
		try {
			Throwable cleaned = cleaner.apply(failure);
			return cleaned == null ? failure : cleaned;
		} catch (RuntimeException | Error e) {
			return failure;
		}
	}

	static boolean hasFailureSource() {
		return FAILURE_SOURCE.get() != null;
	}

	static Throwable failureSource(Throwable fallback) {
		Throwable source = FAILURE_SOURCE.get();
		return source == null ? fallback : source;
	}

	private static final class Entry<C> {
		private final JPostmanRuntimeRequest<C> request;
		private final Function<Throwable, Throwable> failureCleaner;

		private Entry(JPostmanRuntimeRequest<C> request, Function<Throwable, Throwable> failureCleaner) {
			this.request = request;
			this.failureCleaner = failureCleaner;
		}
	}

	private static final class Key {
		private final Object owner;
		private final Class<?> contextType;

		private Key(Object owner, Class<?> contextType) {
			this.owner = owner;
			this.contextType = contextType;
		}

		@Override
		public int hashCode() {
			return 31 * System.identityHashCode(owner) + (contextType == null ? 0 : contextType.hashCode());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Key)) {
				return false;
			}
			Key other = (Key) obj;
			return owner == other.owner && contextType == other.contextType;
		}
	}
}
