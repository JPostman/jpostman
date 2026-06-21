
package io.jpostman.testng;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.testng.annotations.Listeners;

/**
 * Enables JPostman TestNG annotation support.
 */
@Target(TYPE)
@Retention(RUNTIME)
@Listeners(JPostmanTestNgAnnotationListener.class)
public @interface JPostmanTestNG {
}
