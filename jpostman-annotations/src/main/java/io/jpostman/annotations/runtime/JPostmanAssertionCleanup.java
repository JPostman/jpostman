package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.jpostman.annotations.JPostman;

/**
 * Thread-local cleanup hook for assertion facade calls executed inside a test
 * body.
 */
final class JPostmanAssertionCleanup {

	private static final ThreadLocal<Function<Throwable, Throwable>> CLEANER = new ThreadLocal<>();
	private static final ThreadLocal<Object> CURRENT_INSTANCE = new ThreadLocal<>();
	private static final ThreadLocal<Method> CURRENT_METHOD = new ThreadLocal<>();
	private static final ThreadLocal<AssertionError> IMMEDIATE_FAILURE = new ThreadLocal<>();
	private static final ThreadLocal<Set<JPostman.Assert>> EXPLICIT_SOFT_ASSERTS = new ThreadLocal<>();
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
		EXPLICIT_SOFT_ASSERTS.set(Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	static void clear() {
		CLEANER.remove();
		CURRENT_INSTANCE.remove();
		CURRENT_METHOD.remove();
		IMMEDIATE_FAILURE.remove();
		EXPLICIT_SOFT_ASSERTS.remove();
	}

	static boolean hasCurrentMethod() {
		return CURRENT_INSTANCE.get() != null && CURRENT_METHOD.get() != null;
	}

	static Method currentMethod() {
		return CURRENT_METHOD.get();
	}

	static void registerExplicitSoft(JPostman.Assert assertions) {
		if (assertions == null || !hasCurrentMethod()) {
			return;
		}
		Set<JPostman.Assert> values = EXPLICIT_SOFT_ASSERTS.get();
		if (values == null) {
			values = Collections.newSetFromMap(new IdentityHashMap<>());
			EXPLICIT_SOFT_ASSERTS.set(values);
		}
		values.add(assertions);
	}

	static void verifyExplicitSoft() {
		Set<JPostman.Assert> values = EXPLICIT_SOFT_ASSERTS.get();
		if (values == null || values.isEmpty()) {
			return;
		}

		AssertionError failure = null;
		for (JPostman.Assert assertions : values.toArray(new JPostman.Assert[0])) {
			try {
				assertions.verify();
			} catch (AssertionError error) {
				if (failure == null) {
					failure = error;
				} else {
					failure.addSuppressed(error);
				}
			}
		}
		values.clear();
		if (failure != null) {
			throw failure;
		}
	}

	static void recordImmediateFailure(AssertionError failure) {
		if (failure != null && IMMEDIATE_FAILURE.get() == null) {
			IMMEDIATE_FAILURE.set(failure);
			markCurrentMethod();
		}
	}

	static AssertionError takeImmediateFailure() {
		AssertionError failure = IMMEDIATE_FAILURE.get();
		IMMEDIATE_FAILURE.remove();
		return failure;
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
