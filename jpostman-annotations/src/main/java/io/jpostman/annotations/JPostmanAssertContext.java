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
	 * Enables request-scoped soft assertion mode. Pending failures are verified
	 * automatically after an eligible response or runner request.
	 *
	 * @return {@code true} to collect failures until manual or automatic
	 *         verification
	 */
	boolean soft() default false;
}
