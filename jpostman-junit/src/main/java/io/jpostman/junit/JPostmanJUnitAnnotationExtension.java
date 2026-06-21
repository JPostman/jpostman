package io.jpostman.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Thin JUnit adapter for optional JPostman annotation support.
 *
 * <p>
 * This class intentionally does not import {@code io.jpostman.annotations} so
 * {@code jpostman-junit} can compile and run without the annotation module.
 * When {@code jpostman-annotations} is present on the test classpath, the
 * annotation engine is loaded by class name and executes the shared annotation
 * runtime.
 * </p>
 */
public final class JPostmanJUnitAnnotationExtension
		implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

	private static final String ENGINE_CLASS = "io.jpostman.annotations.JPostmanAnnotationEngine";

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		Object testInstance = context.getRequiredTestInstance();
		Method testMethod = context.getRequiredTestMethod();

		try {
			runAnnotationEngine(testInstance, testMethod);
		} catch (Exception error) {
			JPostmanJUnit.FailurePrinter.printFailure(context, error);
			throw error;
		} catch (Error error) {
			JPostmanJUnit.FailurePrinter.printFailure(context, error);
			throw error;
		}
	}

	@Override
	public void afterEach(ExtensionContext context) {
		JUnitContext.clearCurrent();
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
		return JUnitContext.class.isAssignableFrom(parameterContext.getParameter().getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
		return JUnitContext.current();
	}

	private void runAnnotationEngine(Object testInstance, Method testMethod) throws Exception {
		try {
			Class<?> engine = Class.forName(ENGINE_CLASS);
			Method run = engine.getMethod("runJUnit", Object.class, Method.class);
			run.invoke(null, testInstance, testMethod);
		} catch (ClassNotFoundException e) {
			// Annotation module is optional. Without it, @JPostmanJUnit still supports
			// the regular JUnit context/fluent API features.
			JUnitContext.clearCurrent();
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw e;
		}
	}
}
