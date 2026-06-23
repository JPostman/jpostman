package io.jpostman.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Applies reusable assertion rules after annotation-based response execution.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface JPostmanAssert {

	/**
	 * Assertion namespace to use.
	 *
	 * <p>
	 * Empty means use the namespace from {@link JPostmanResponse} or
	 * {@link JPostmanRunner}.
	 * </p>
	 *
	 * @return assertion namespace
	 */
	String namespace() default "";

	/**
	 * Assertion rules file location.
	 *
	 * <p>
	 * Empty means read from {@code jpostman.properties}. Namespace-specific
	 * properties use {@code assertions.<namespace>} first and then fall back to
	 * {@code assertions}.
	 * </p>
	 *
	 * @return assertion rules file location
	 */
	String rules() default "";

	/**
	 * Assertion sections to apply.
	 *
	 * <p>
	 * Empty means use the {@code default} section. If a section matching the
	 * current Postman request name exists, it is applied last and can override
	 * parent or configured section rules.
	 * </p>
	 *
	 * @return assertion section names
	 */
	String[] sections() default {};
}
