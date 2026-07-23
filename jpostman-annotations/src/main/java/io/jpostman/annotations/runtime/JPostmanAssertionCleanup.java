package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Thread-local cleanup hook for assertion facade calls executed inside a test
 * body.
 */
final class JPostmanAssertionCleanup {

	private static final ThreadLocal<Function<Throwable, Throwable>> CLEANER = new ThreadLocal<>();
	private static final ThreadLocal<Object> CURRENT_INSTANCE = new ThreadLocal<>();
	private static final ThreadLocal<Method> CURRENT_METHOD = new ThreadLocal<>();
	private static final Map<Object, Method> LAST_ASSERTION_METHOD = Collections
			.synchronizedMap(new IdentityHashMap<>());

	private JPostmanAssertionCleanup() {
	}

	static void register(Object testInstance, Method testMethod) {
		if (testInstance == null || testMethod == null) {
			return;
		}
		CURRENT_INSTANCE.set(testInstance);
		CURRENT_METHOD.set(testMethod);
		CLEANER.set(error -> clean(testInstance, testMethod, error));
	}

	static void clear() {
		CLEANER.remove();
		CURRENT_INSTANCE.remove();
		CURRENT_METHOD.remove();
	}

	static void markCurrentMethod() {
		Object instance = CURRENT_INSTANCE.get();
		Method method = CURRENT_METHOD.get();
		if (instance != null && method != null) {
			LAST_ASSERTION_METHOD.put(instance, method);
		}
	}

	static Method lastMethod(Object testInstance) {
		return testInstance == null ? null : LAST_ASSERTION_METHOD.get(testInstance);
	}

	static void clear(Object testInstance) {
		if (testInstance != null) {
			LAST_ASSERTION_METHOD.remove(testInstance);
		}
	}

	static Throwable clean(Throwable failure) {
		if (failure == null) {
			return null;
		}

		if (JPostmanRuntimeCall.hasFailureCleaner()) {
			return JPostmanRuntimeCall.clean(failure);
		}

		Function<Throwable, Throwable> cleaner = CLEANER.get();
		if (cleaner == null) {
			return failure;
		}

		try {
			Throwable cleaned = cleaner.apply(failure);
			return cleaned == null ? failure : cleaned;
		} catch (RuntimeException | Error e) {
			return failure;
		}
	}

	private static Throwable clean(Object testInstance, Method testMethod, Throwable error) {
		Throwable root = JPostmanStackTraceCleaner.rootCause(error);
		if (root instanceof AssertionError) {
			return JPostmanAnnotationEngine.cleanRuntimeFailure(testInstance, testMethod, error, "");
		}
		return JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, error);
	}
}
