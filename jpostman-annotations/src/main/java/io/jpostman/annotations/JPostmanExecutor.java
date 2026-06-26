package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method that provides an {@code ApiExecutor} for annotation-based
 * request execution.
 *
 * <p>
 * Executor methods are used by {@link JPostmanResponse#executor()} and can also
 * declare dependencies that must run before the executor is created. Supported
 * method signatures are: context only, or context and {@link JPostmanInfo}.
 * </p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanExecutor {

	/**
	 * Executor name used by {@link JPostmanResponse#executor()}.
	 *
	 * @return executor name, or empty string for the default executor
	 */
	String id() default "";

	/**
	 * Dependency method names to run before creating this executor.
	 *
	 * @return dependency method names
	 */
	String[] dependsOn() default {};
}
