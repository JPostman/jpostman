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
}
