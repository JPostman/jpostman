package io.jpostman.testng;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a TestNG test class as a JPostman TestNG test.
 *
 * <p>
 * This module provides the TestNG context and TestNG framework support.
 * Optional annotation-based request execution lives in
 * {@code jpostman-annotations}. When that module is on the TestNG classpath,
 * its listener can run annotation setup and request/response execution without
 * changing this module.
 * </p>
 */
@Inherited
@Target(TYPE)
@Retention(RUNTIME)
public @interface JPostmanTestNG {
}
