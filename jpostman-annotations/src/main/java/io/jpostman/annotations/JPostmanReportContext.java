package io.jpostman.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.jpostman.annotations.runtime.JPostmanInfo;
import io.jpostman.annotations.runtime.JPostmanReport;

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

	/**
	 * Includes compact execution details after the class summary.
	 *
	 * <p>
	 * Each recorded request is displayed on one line with its method, HTTP status
	 * code, duration or skip state, resolved namespace, folder, request name, and
	 * dependency chain when available. Request and response bodies remain
	 * controlled by {@code debug} and {@code fail}.
	 * </p>
	 *
	 * @return {@code true} to include compact execution details
	 */
	boolean details() default false;

	/**
	 * Controls report behavior after a failed execution. Combine at most one action
	 * with optional failure diagnostics.
	 *
	 * <ul>
	 * <li>{@code ignore} - continue after the failure without adding automatic
	 * failure details to the report. This is the default.</li>
	 * <li>{@code skipAll} - after the first failure, skip and report all remaining
	 * JPostman-managed tests.</li>
	 * <li>{@code terminate} - print the report summary and failure details, then
	 * terminate the process.</li>
	 * <li>{@code error} - include the cleaned failure error and stack trace.</li>
	 * <li>{@code request} - include the prepared request.</li>
	 * <li>{@code response} - include the received response.</li>
	 * <li>{@code info} - include runtime annotation information.</li>
	 * <li>{@code all} - include request, response, and info.</li>
	 * </ul>
	 *
	 * <p>
	 * A failure section is printed after the JPostman report summary only when an
	 * output value is configured. It starts with the current short report line and
	 * then appends the selected diagnostics. Examples: {@code fail = "request"},
	 * {@code fail = { "request", "response" }},
	 * {@code fail = { "response", "error" }}, {@code fail = { "skipAll", "all" }},
	 * or {@code fail = { "terminate", "request" }}.
	 * </p>
	 *
	 * @return failure action and optional diagnostics
	 */
	String[] fail() default { "ignore" };
}
