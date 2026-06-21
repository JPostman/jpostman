package io.jpostman.testng;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a TestNG test class as a JPostman annotation-based test.
 *
 * <p>
 * The actual TestNG listener is registered through Java ServiceLoader. This
 * keeps user code clean:
 * </p>
 *
 * <pre>
 * &#64;JPostmanTestNG
 * public class DemoTest {
 * }
 * </pre>
 */
@Inherited
@Target(TYPE)
@Retention(RUNTIME)
public @interface JPostmanTestNG {
}