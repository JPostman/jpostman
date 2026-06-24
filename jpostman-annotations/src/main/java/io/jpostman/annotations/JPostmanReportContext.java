package io.jpostman.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a {@link JPostmanReport} into a test class field.
 *
 * <p>
 * The annotated field must be of type {@link JPostmanReport}. The report stores
 * the latest {@link JPostmanInfo}, all created {@link JPostmanInfo} objects,
 * total execution time, and execution status counters.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JPostmanReportContext {
}
