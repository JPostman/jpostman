package io.jpostman.junit;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import io.jpostman.secure.JPostmanAssertionError;

/**
 * Enables JPostman JUnit test defaults.
 *
 * <p>
 * This enables per-class test lifecycle. Failure printing can be enabled for
 * IDE runners that show assertion details only in the JUnit failure trace
 * panel.
 * </p>
 *
 * <p>
 * Optional annotation support is loaded reflectively from
 * {@code jpostman-annotations} when that module is present on the test
 * classpath. This keeps annotation behavior in the annotations module while
 * keeping the user-facing JUnit annotation as {@code @JPostmanJUnit}.
 * </p>
 */
@Target(TYPE)
@Retention(RUNTIME)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(JPostmanJUnit.JPostmanJUnitExtension.class)
public @interface JPostmanJUnit {
	
	public static final int DEFAULT_MAX_STACK_TRACE = 6;

	/**
	 * Enables printing test failures to the console.
	 *
	 * @return {@code true} to print failures to the console
	 */
	boolean printFailures() default true;

	/**
	 * JUnit bridge for JPostman defaults, optional annotation runtime, current
	 * context cleanup, parameter resolution, and failure printing.
	 */
	final class JPostmanJUnitExtension
			implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler,
			TestInstancePostProcessor, InvocationInterceptor, AfterEachCallback, ParameterResolver {

		private static final String ANNOTATION_ENGINE = "io.jpostman.annotations.JPostmanAnnotationEngine";

		@Override
		public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
			setupAnnotations(testInstance);
		}

		@Override
		public void interceptTestMethod(Invocation<Void> invocation,
		        ReflectiveInvocationContext<Method> invocationContext,
		        ExtensionContext extensionContext) throws Throwable {

		    Object testInstance = extensionContext.getRequiredTestInstance();
		    Method testMethod = invocationContext.getExecutable();

		    try {
		        runAnnotations(testInstance, testMethod);
		        invocation.proceed();
		    } catch (Throwable e) {
		        throw cleanJUnitFailure(testInstance, testMethod, e);
		    }
		}
		
		private static Throwable cleanJUnitFailure(Object testInstance, Method testMethod, Throwable error) throws Throwable {
		    try {
		        Class<?> engine = Class.forName(ANNOTATION_ENGINE);
		        Method clean = engine.getMethod("cleanJUnitFailure", Object.class, Method.class, Throwable.class);

		        Object result = clean.invoke(null, testInstance, testMethod, error);

		        if (result instanceof Throwable) {
		            return (Throwable) result;
		        }
		    } catch (ClassNotFoundException e) {
		        // jpostman-annotations is optional.
		    } catch (InvocationTargetException e) {
		        throw e.getCause();
		    } catch (ReflectiveOperationException e) {
		        // Fall back to original error.
		    }

		    return error;
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

		@Override
		public void handleTestExecutionException(ExtensionContext context, Throwable error) throws Throwable {
			printFailure(context, error);
			throw error;
		}

		@Override
		public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable error)
				throws Throwable {
			printFailure(context, error);
			throw error;
		}

		@Override
		public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable error)
				throws Throwable {
			printFailure(context, error);
			throw error;
		}

		static void printFailure(ExtensionContext context, Throwable error) {
			JPostmanJUnit annotation = context.getRequiredTestClass().getAnnotation(JPostmanJUnit.class);

			boolean skipPrintFailures = Boolean.parseBoolean(System.getProperty("skipPrintFailures", "false"));

			if (skipPrintFailures || annotation == null || !annotation.printFailures()) {
				return;
			}

			System.err.println();
			System.err.println("********** JUnit Failure **********");
			System.err.println(context.getDisplayName());

			Throwable root = rootCause(error);

			System.err.println(root.getClass().getName() + ": " + nullToEmpty(root.getMessage()));

			printStackTrace(root);

			JPostmanAssertionError jpostmanError = findJPostmanAssertionError(error);
			if (jpostmanError != null) {
			    String secureLog = jpostmanError.secureLog();
			    if (secureLog != null && !secureLog.isBlank()) {
			        System.err.println();
			        System.err.println(secureLog.trim());
			    }
			}

			System.err.println();
		}
		
		private static void printStackTrace(Throwable error) {
		    if (error == null) {
		        return;
		    }
		    StackTraceElement[] stack = error.getStackTrace();
		    for (int i = 0; i < stack.length && i < DEFAULT_MAX_STACK_TRACE; i++) {
		        StackTraceElement element = stack[i];
		        System.err.println("\tat " + element);
		    }
		}

		private static void setupAnnotations(Object testInstance) throws Exception {
			try {
				Class<?> engine = Class.forName(ANNOTATION_ENGINE);
				engine.getMethod("setupJUnit", Object.class).invoke(null, testInstance);
			} catch (ClassNotFoundException e) {
				// jpostman-annotations is optional. Without it, @JPostmanJUnit still
				// provides per-class lifecycle, parameter resolution, current cleanup,
				// and failure printing for plain JUnitContext tests.
			} catch (InvocationTargetException e) {
				rethrow(e);
			}
		}

		private static void runAnnotations(Object testInstance, Method testMethod) throws Exception {
			try {
				Class<?> engine = Class.forName(ANNOTATION_ENGINE);
				engine.getMethod("runJUnit", Object.class, Method.class).invoke(null, testInstance, testMethod);
			} catch (ClassNotFoundException e) {
				// jpostman-annotations is optional.
			} catch (InvocationTargetException e) {
				rethrow(e);
			}
		}

		static void rethrow(InvocationTargetException error) throws Exception {
			Throwable cause = error.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw error;
		}

		private static JPostmanAssertionError findJPostmanAssertionError(Throwable error) {
			Throwable current = error;

			while (current != null) {
				if (current instanceof JPostmanAssertionError) {
					return (JPostmanAssertionError) current;
				}

				for (Throwable suppressed : current.getSuppressed()) {
					JPostmanAssertionError found = findJPostmanAssertionError(suppressed);
					if (found != null) {
						return found;
					}
				}

				current = current.getCause();
			}

			return null;
		}

		private static Throwable rootCause(Throwable error) {
			Throwable current = error;
			while (current.getCause() != null) {
				current = current.getCause();
			}
			return current;
		}

		private static String nullToEmpty(String value) {
			return value == null ? "" : value;
		}
	}
}
