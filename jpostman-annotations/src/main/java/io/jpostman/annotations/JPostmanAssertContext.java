package io.jpostman.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Injects a framework-neutral assertion facade backed by the latest active
 * JPostman test context.
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface JPostmanAssertContext {
	/**
	 * Enables class-scoped soft assertion mode. The collector is verified after all
	 * tests in the class complete.
	 *
	 * @return {@code true} to collect failures and verify them after class teardown
	 */
	boolean soft() default false;
}
