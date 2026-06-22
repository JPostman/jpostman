package io.jpostman.annotations.testng;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.testng.annotations.Listeners;

/**
 * Enables JPostman annotation-based execution for TestNG tests.
 *
 * <p>
 * This annotation lives in {@code jpostman-annotations}, so annotation runtime
 * behavior can evolve without changing the {@code jpostman-testng} module. Use
 * {@code @JPostmanTestNG} from {@code jpostman-testng} only for TestNG
 * framework context support.
 * </p>
 */
@Inherited
@Target(TYPE)
@Retention(RUNTIME)
@Listeners(JPostmanTestNgAnnotationListener.class)
public @interface JPostmanTestNgAnnotations {
}
