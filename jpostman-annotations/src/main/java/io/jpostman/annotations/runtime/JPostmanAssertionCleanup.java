package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Thread-local cleanup hook for assertion facade calls executed inside a test
 * body.
 */
final class JPostmanAssertionCleanup {

	private static final ThreadLocal<Function<Throwable, Throwable>> CLEANER = new ThreadLocal<>();

	private JPostmanAssertionCleanup() {
	}

	static void register(Object testInstance, Method testMethod) {
		if (testInstance == null || testMethod == null) {
			return;
		}
		CLEANER.set(error -> clean(testInstance, testMethod, error));
	}

	static void clear() {
		CLEANER.remove();
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
