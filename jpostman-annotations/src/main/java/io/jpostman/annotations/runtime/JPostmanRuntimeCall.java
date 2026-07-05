package io.jpostman.annotations.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Thread-local registry for the active runtime @JPostman.Call execution. */
final class JPostmanRuntimeCall {

	private static final ThreadLocal<Map<Key, Entry<?>>> CALLS = ThreadLocal.withInitial(LinkedHashMap::new);
	private static final ThreadLocal<Function<Throwable, Throwable>> FAILURE_CLEANER = new ThreadLocal<>();
	private static final ThreadLocal<Throwable> FAILURE_SOURCE = new ThreadLocal<>();

	private JPostmanRuntimeCall() {
	}

	static <C> void register(Object owner, Class<?> contextType, JPostmanRuntimeRequest<C> request,
			Function<Throwable, Throwable> failureCleaner) {
		if (owner != null && contextType != null && request != null) {
			CALLS.get().put(new Key(owner, contextType), new Entry<>(request, failureCleaner));
			FAILURE_CLEANER.set(failureCleaner);
		}
	}

	static void clear(Object owner, Class<?> contextType) {
		if (owner != null && contextType != null) {
			CALLS.get().remove(new Key(owner, contextType));
			if (CALLS.get().isEmpty()) {
				FAILURE_CLEANER.remove();
				FAILURE_SOURCE.remove();
			}
		}
	}

	@SuppressWarnings("unchecked")
	static <C> C execute(Object owner, Class<?> contextType, BiConsumer<C, JPostmanInfo> action) throws Exception {
		Entry<?> entry = CALLS.get().get(new Key(owner, contextType));
		if (entry == null) {
			throw new IllegalStateException("No active @JPostman.Call request is available for this test method.");
		}
		FAILURE_CLEANER.set(entry.failureCleaner);
		return ((JPostmanRuntimeRequest<C>) entry.request).execute(action);
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
