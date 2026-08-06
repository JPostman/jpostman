package io.jpostman.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testng.annotations.Listeners;

import io.jpostman.Collection;
import io.jpostman.Environment;
import io.jpostman.annotations.runtime.JPostmanCacheValueConverter;
import io.jpostman.annotations.runtime.JPostmanDataLoader;
import io.jpostman.annotations.runtime.JPostmanInfo;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotationListener;
import io.jpostman.junit.JPostmanJUnitExtension;
import io.jpostman.secure.JPostmanAssertions;
import io.jpostman.secure.JPostmanTestContext;

/**
 * Compact JPostman annotation facade.
 *
 * <p>
 * Provides a single import for JPostman test annotations and runtime facade
 * types.
 * </p>
 */
public final class JPostman {

	private JPostman() {
	}

	/**
	 * Enables JPostman annotation support for TestNG.
	 */
	@Inherited
	@Target(TYPE)
	@Retention(RUNTIME)
	@Listeners(JPostmanTestNgAnnotationListener.class)
	public @interface TestNG {
	}

	/**
	 * Enables JPostman annotation support for JUnit 5.
	 *
	 * <p>
	 * Uses {@link Lifecycle#PER_CLASS} so injected fields can be used from
	 * non-static {@code @BeforeAll} and {@code @AfterAll} methods.
	 * </p>
	 */
	@Inherited
	@Target(TYPE)
	@Retention(RUNTIME)
	@TestInstance(Lifecycle.PER_CLASS)
	@ExtendWith(JPostmanJUnitExtension.class)
	public @interface JUnit {

		/**
		 * Prints cleaned failure stack traces for JUnit execution when no
		 * {@link ReportContext} is declared. When a report context is present, its
		 * {@code details} and {@code fail} values exclusively control automatic output.
		 *
		 * @return {@code true} to print cleaned failures when reporting does not own
		 *         output
		 */
		boolean printFailures() default false;
	}

	/**
	 * Loads and injects the main JPostman runtime context.
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface Context {

		/**
		 * JPostman properties file location.
		 *
		 * @return config file location
		 */
		String config() default JPostmanDataLoader.DEFAULT_CONFIG;

		/**
		 * Postman collection location.
		 *
		 * @return collection location
		 */
		String collection() default "";

		/**
		 * Postman environment location.
		 *
		 * @return environment location
		 */
		String environment() default "";

		/**
		 * Rules file location.
		 *
		 * @return rules location
		 */
		String rules() default "";

		/**
		 * Data files to load.
		 *
		 * @return data file locations
		 */
		String[] dataload() default {};

		/**
		 * Assertion files to load.
		 *
		 * @return assertion file locations
		 */
		String[] assertions() default {};

		/**
		 * Default expected HTTP status code.
		 *
		 * @return expected HTTP status code, {@code 0} to pass without status
		 *         verification, or {@code 1} to mark a completed test skipped
		 */
		int verifyStatusCode() default 200;

		/**
		 * Default executor class name. Use this when avoiding an executor import.
		 *
		 * @return fully qualified executor class name, or empty string when not
		 *         configured
		 */
		String executor() default "";

		/**
		 * Default executor class. Use this when the executor class is already imported.
		 *
		 * @return executor class, or {@link Void} when not configured
		 */
		Class<?> executorClass() default Void.class;

		/**
		 * Reuses executor state when supported by the configured executor.
		 *
		 * @return {@code true} to reuse executor state
		 */
		boolean session() default false;

		/**
		 * Skips all JPostman response and runner test executions by default. Individual
		 * response or runner methods can opt in with {@code enabled = true}.
		 *
		 * @return {@code true} to skip all JPostman test executions by default
		 */
		boolean skipAll() default false;

		/**
		 * Controls automatic JPostman debug output.
		 *
		 * <ul>
		 * <li>{@code none} - disable automatic output and keep the minimum failure
		 * stack.</li>
		 * <li>{@code request} - include the prepared request.</li>
		 * <li>{@code response} - include the received response.</li>
		 * <li>{@code info} - include runtime annotation information.</li>
		 * <li>{@code all} - include request, response, and info.</li>
		 * </ul>
		 *
		 * Request, response, and info may be combined. {@code none} and {@code all}
		 * must each be used alone. Selected output is printed after each annotation
		 * execution whether that execution passes or fails.
		 *
		 * @return automatic debug output settings
		 */
		String[] debug() default { "none" };
	}

	/**
	 * Injects a framework-specific test context.
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface TestContext {

		/**
		 * Context namespace.
		 *
		 * @return namespace, or empty string for the default namespace
		 */
		String namespace() default "";

		/**
		 * JPostman properties file location.
		 *
		 * @return config file location
		 */
		String config() default JPostmanDataLoader.DEFAULT_CONFIG;

		/**
		 * Postman collection location.
		 *
		 * @return collection location
		 */
		String collection() default "";

		/**
		 * Postman environment location.
		 *
		 * @return environment location
		 */
		String environment() default "";

		/**
		 * Rules file location.
		 *
		 * @return rules location
		 */
		String rules() default "";

		/**
		 * Controls whether this field follows the active context.
		 *
		 * @return {@code true} to follow the active context
		 */
		boolean active() default false;
	}

	/**
	 * Injects an assertion facade backed by the latest active JPostman test
	 * context.
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface AssertContext {
		/**
		 * Enables request-scoped soft assertion mode. Pending failures are verified
		 * automatically after an eligible response or runner request.
		 *
		 * @return {@code true} to collect failures until manual or automatic
		 *         verification
		 */
		boolean soft() default false;
	}

	/**
	 * Injects the JPostman report facade.
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface ReportContext {
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
		 * <li>{@code skipAll} - skip all remaining JPostman-managed tests.</li>
		 * <li>{@code terminate} - print the report summary and failure details, then
		 * terminate the process.</li>
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
		 * {@code fail = { "skipAll", "all" }}, or
		 * {@code fail = { "terminate", "request" }}.
		 * </p>
		 *
		 * @return failure action and optional diagnostics
		 */
		String[] fail() default { "ignore" };
	}

	/**
	 * Marks a method as a JPostman executor provider or pre-execution interceptor.
	 * A single method of each role is selected automatically. With multiple
	 * methods, the method without an id is the default and named methods are
	 * selected with an annotation {@code executor} value such as {@code "#audit"}.
	 * Request-execution exceptions are represented as synthetic responses so a void
	 * interceptor can continue after {@code runtime.call()}. Common mappings
	 * include connection failures to 503, timeouts to 504, gateway/DNS/SSL failures
	 * to 502, invalid arguments to 400, security failures to 403, unsupported
	 * operations to 501, and other state/runtime failures to 500.
	 */
	@Target(METHOD)
	@Retention(RUNTIME)
	public @interface Executor {

		/**
		 * Optional executor id. A single executor is selected automatically even when
		 * this id is set. With multiple executors of the same role, an empty id marks
		 * the default and a non-empty id is selected with {@code executor = "#id"}.
		 *
		 * @return executor id, or empty string for the default executor
		 */
		String id() default "";

		/**
		 * Dependency method names or annotation ids. Use plain values for Java method
		 * names, or prefix ids with "#", such as dependsOn = "#login".
		 *
		 * @return dependency method names or "#id" references
		 */
		String[] dependsOn() default {};

		/**
		 * Namespace where this void executor interceptor applies. Empty is the global
		 * fallback. An exact namespace match takes precedence over a global
		 * interceptor. When the selected/default interceptor is the only source of a
		 * namespace, its namespace becomes the effective request namespace before
		 * collection lookup. A namespace explicitly declared on Request, Response,
		 * Call, or Runner always takes precedence.
		 *
		 * @return namespace, or empty string
		 */
		String namespace() default "";

		/**
		 * Reuses executor state when supported.
		 *
		 * @return {@code true} to reuse executor state
		 */
		boolean session() default false;

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, {@code request}
		 * for prepared-request diagnostics, {@code response} for received-response
		 * diagnostics, {@code info} for runtime annotation information, or {@code all}
		 * for all local diagnostics. Request, response, info, and all are printed after
		 * annotation execution for both passing and failing executions. The local
		 * {@code error} mode is different: it is printed only for failures and is
		 * deferred until after the JPostman report execution details.
		 * </p>
		 *
		 * @return local debug setting
		 */
		String debug() default "debug";

	}

	/**
	 * Marks a method that prepares or modifies a request.
	 */
	@Target(METHOD)
	@Retention(RUNTIME)
	public @interface Request {

		/**
		 * Tags used to select this method.
		 *
		 * @return tags
		 */
		String[] tags() default {};

		/**
		 * Optional annotation id used by dependsOn = "#id".
		 *
		 * @return unique annotation id, or empty string when not used
		 */
		String id() default "";

		/**
		 * Request namespace.
		 *
		 * @return namespace
		 */
		String namespace() default "";

		/**
		 * Collection folder path from the collection root to the target folder.
		 *
		 * <p>
		 * Each value represents one folder level. An empty value selects requests from
		 * the collection root.
		 * </p>
		 *
		 * <pre>
		 * folder = "Products"
		 * folder = { "level1", "level2", "level3" }
		 * </pre>
		 *
		 * @return folder path levels from parent to child
		 */
		String[] folder() default "";

		/**
		 * Collection request name.
		 *
		 * @return request name
		 */
		String request() default "";

		/**
		 * Rules section name.
		 *
		 * @return rule name
		 */
		String rule() default "";

		/**
		 * Fields to keep in the context.
		 *
		 * @return filter fields
		 */
		String[] filter() default {};

		/**
		 * Dependency method names or annotation ids. Use plain values for Java method
		 * names, or prefix ids with "#", such as dependsOn = "#login".
		 *
		 * @return dependency method names or "#id" references
		 */
		String[] dependsOn() default {};

		/**
		 * Selects a named {@link Executor} by method name or {@code "#id"}. Leave empty
		 * to use the single executor or the executor without an id.
		 *
		 * @return executor selector, or empty string for automatic/default selection
		 */
		String executor() default "";

		/**
		 * Cache key for the method return value. An explicit non-empty value is used
		 * directly. An explicit empty value uses the annotation id when one is defined,
		 * otherwise the Java method-name fallback.
		 *
		 * @return cache key, empty string to use the annotation id or method fallback,
		 *         or {@link JPostmanRequest#NO_CACHE} when caching is disabled
		 */
		String cache() default JPostmanRequest.NO_CACHE;

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, {@code request}
		 * for prepared-request diagnostics, {@code response} for received-response
		 * diagnostics, {@code info} for runtime annotation information, or {@code all}
		 * for all local diagnostics. Request, response, info, and all are printed after
		 * annotation execution for both passing and failing executions. The local
		 * {@code error} mode is different: it is printed only for failures and is
		 * deferred until after the JPostman report execution details.
		 * </p>
		 *
		 * @return local debug setting
		 */
		String debug() default "debug";

		/**
		 * Data section name.
		 *
		 * @return data section
		 */
		String data() default "";

	}

	/**
	 * Marks a method that executes and handles a response.
	 */
	@Target(METHOD)
	@Retention(RUNTIME)
	public @interface Response {

		/**
		 * Tags used to select this method.
		 *
		 * @return tags
		 */
		String[] tags() default {};

		/**
		 * Optional annotation id used by dependsOn = "#id".
		 *
		 * @return unique annotation id, or empty string when not used
		 */
		String id() default "";

		/**
		 * Response namespace.
		 *
		 * @return namespace
		 */
		String namespace() default "";

		/**
		 * Collection folder path from the collection root to the target folder.
		 *
		 * <p>
		 * Each value represents one folder level. An empty value selects requests from
		 * the collection root.
		 * </p>
		 *
		 * <pre>
		 * folder = "Products"
		 * folder = { "level1", "level2", "level3" }
		 * </pre>
		 *
		 * @return folder path levels from parent to child
		 */
		String[] folder() default "";

		/**
		 * Collection request name.
		 *
		 * @return request name
		 */
		String request() default "";

		/**
		 * Rules section name.
		 *
		 * @return rule name
		 */
		String rule() default "";

		/**
		 * Fields to keep in the context.
		 *
		 * @return filter fields
		 */
		String[] filter() default {};

		/**
		 * Dependency method names.
		 *
		 * @return dependency method names
		 */
		String[] dependsOn() default {};

		/**
		 * Expected HTTP status code.
		 *
		 * @return expected HTTP status code, {@code -1} to use the context default,
		 *         {@code 0} to pass without status verification, or {@code 1} to mark
		 *         the completed test skipped
		 */
		int verify() default -1;

		/**
		 * Selects a named {@link Executor} by method name or {@code "#id"}. Leave empty
		 * to use the single executor or the executor without an id.
		 *
		 * @return executor selector, or empty string for automatic/default selection
		 */
		String executor() default "";

		/**
		 * Cache key for the method return value. An explicit non-empty value is used
		 * directly. An explicit empty value uses the annotation id when one is defined,
		 * otherwise the Java method-name fallback.
		 *
		 * @return cache key, empty string to use the annotation id or method fallback,
		 *         or {@link JPostmanResponse#NO_CACHE} when caching is disabled
		 */
		String cache() default JPostmanResponse.NO_CACHE;

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, {@code request}
		 * for prepared-request diagnostics, {@code response} for received-response
		 * diagnostics, {@code info} for runtime annotation information, or {@code all}
		 * for all local diagnostics. Request, response, info, and all are printed after
		 * annotation execution for both passing and failing executions. The local
		 * {@code error} mode is different: it is printed only for failures and is
		 * deferred until after the JPostman report execution details.
		 * </p>
		 *
		 * @return local debug setting
		 */
		String debug() default "debug";

		/**
		 * Data section name.
		 *
		 * @return data section
		 */
		String data() default "";

		/**
		 * Assertion section names.
		 *
		 * @return assertion sections
		 */
		String[] asserts() default {};

		/**
		 * @return {@code true} to run this response even when context skipAll is
		 *         enabled
		 */
		boolean enabled() default false;

		/** @return {@code true} to skip this response/test execution */
		boolean skip() default false;

	}

	/**
	 * Marks a test method that executes one request manually through
	 * {@link Runtime#call()} or {@link Runtime#call(BiConsumer)}.
	 */
	@Target(METHOD)
	@Retention(RUNTIME)
	public @interface Call {

		/**
		 * Tags used by this manual call.
		 *
		 * @return tags
		 */
		String[] tags() default {};

		/**
		 * Optional annotation id used by dependsOn = "#id".
		 *
		 * @return unique annotation id, or empty string when not used
		 */
		String id() default "";

		/**
		 * Request namespace.
		 *
		 * @return namespace
		 */
		String namespace() default "";

		/**
		 * Collection folder path from the collection root to the target folder.
		 *
		 * <p>
		 * Each value represents one folder level. An empty value selects requests from
		 * the collection root.
		 * </p>
		 *
		 * <pre>
		 * folder = "Products"
		 * folder = { "level1", "level2", "level3" }
		 * </pre>
		 *
		 * @return folder path levels from parent to child
		 */
		String[] folder() default "";

		/**
		 * Collection request name.
		 *
		 * @return request name
		 */
		String request() default "";

		/**
		 * Rules section name.
		 *
		 * @return rule name
		 */
		String rule() default "";

		/**
		 * Fields to keep in the context.
		 *
		 * @return filter fields
		 */
		String[] filter() default {};

		/**
		 * Dependency method names or annotation ids. Use plain values for Java method
		 * names, or prefix ids with "#", such as dependsOn = "#login".
		 *
		 * @return dependency method names or "#id" references
		 */
		String[] dependsOn() default {};

		/**
		 * Expected HTTP status code. Verification runs after {@link Runtime#call()} or
		 * {@link Runtime#call(BiConsumer)} completes the request.
		 *
		 * @return expected HTTP status code, {@code -1} to use the context default,
		 *         {@code 0} to pass without status verification, or {@code 1} to mark
		 *         the completed test skipped
		 */
		int verify() default -1;

		/**
		 * Selects a named {@link Executor} by method name or {@code "#id"}. Leave empty
		 * to use the single executor or the executor without an id.
		 *
		 * @return executor selector, or empty string for automatic/default selection
		 */
		String executor() default "";

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, {@code request}
		 * for prepared-request diagnostics, {@code response} for received-response
		 * diagnostics, {@code info} for runtime annotation information, or {@code all}
		 * for all local diagnostics. Request, response, info, and all are printed after
		 * annotation execution for both passing and failing executions. The local
		 * {@code error} mode is different: it is printed only for failures and is
		 * deferred until after the JPostman report execution details.
		 * </p>
		 *
		 * @return local debug setting
		 */
		String debug() default "debug";

		/**
		 * Data section name.
		 *
		 * @return data section
		 */
		String data() default "";

		/**
		 * @return {@code true} to run this call even when context skipAll is enabled
		 */
		boolean enabled() default false;

		/** @return {@code true} to skip this call execution */
		boolean skip() default false;

	}

	/**
	 * Marks a method that runs one or more collection requests.
	 */
	@Target(METHOD)
	@Retention(RUNTIME)
	public @interface Runner {

		/**
		 * Optional annotation id used by dependsOn = "#id".
		 *
		 * @return annotation id
		 */
		String id() default "";

		/**
		 * Tags used to select this runner.
		 *
		 * @return tags
		 */
		String[] tags() default {};

		/**
		 * Runner namespace.
		 *
		 * @return namespace
		 */
		String namespace() default "";

		/**
		 * Collection folder path from the collection root to the target folder.
		 *
		 * <p>
		 * Each value represents one folder level. An empty value selects requests from
		 * the collection root.
		 * </p>
		 *
		 * <pre>
		 * folder = "Products"
		 * folder = { "level1", "level2", "level3" }
		 * </pre>
		 *
		 * @return folder path levels from parent to child
		 */
		String[] folder() default "";

		/**
		 * Request names to include.
		 *
		 * @return included request names
		 */
		String[] include() default {};

		/**
		 * Request names to exclude.
		 *
		 * @return excluded request names
		 */
		String[] exclude() default {};

		/**
		 * Rules section name.
		 *
		 * @return rule name
		 */
		String rule() default "";

		/**
		 * Fields to keep in the context.
		 *
		 * @return filter fields
		 */
		String[] filter() default {};

		/**
		 * Dependency method names.
		 *
		 * <p>
		 * For runner launcher methods, a single runner dependency such as
		 * {@code dependsOn = "#testRunner"} can reuse the referenced runner body with
		 * this annotation's tags when this runner does not define its own folder,
		 * include/exclude, executor, rule, filter, data, asserts, verify, or lifecycle
		 * settings.
		 * </p>
		 *
		 * @return dependency method names
		 */
		String[] dependsOn() default {};

		/**
		 * Expected HTTP status code for every request executed by this runner. Response
		 * and call dependencies inherit this value only while the runner is active.
		 * Standalone response and call test methods remain separate executions.
		 *
		 * @return expected HTTP status code, {@code -1} to use the context default,
		 *         {@code 0} to pass without status verification, or {@code 1} to mark
		 *         the completed test skipped
		 */
		int verify() default -1;

		/**
		 * Selects a named {@link Executor} by method name or {@code "#id"}. Leave empty
		 * to use the single executor or the executor without an id.
		 *
		 * @return executor selector, or empty string for automatic/default selection
		 */
		String executor() default "";

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, {@code request}
		 * for prepared-request diagnostics, {@code response} for received-response
		 * diagnostics, {@code info} for runtime annotation information, or {@code all}
		 * for all local diagnostics. Request, response, info, and all are printed after
		 * annotation execution for both passing and failing executions. The local
		 * {@code error} mode is different: it is printed only for failures and is
		 * deferred until after the JPostman report execution details.
		 * </p>
		 *
		 * @return local debug setting
		 */
		String debug() default "debug";

		/**
		 * Enables request/response runner lifecycle callbacks.
		 *
		 * <p>
		 * The default {@code false} invokes blank-request {@code @JPostman.Request}
		 * dependencies once for each selected collection request after that request is
		 * prepared, then invokes the runner method body after the response. Set this to
		 * {@code true} to keep dependencies as one-time runner setup and enable the
		 * before-request/response lifecycle used by
		 * {@code jpostman.runner().start(...)}, {@code jpostman.runner().request(...)},
		 * or {@code jpostman.runner().response(...)}.
		 * </p>
		 *
		 * @return {@code true} to enable before-request and response lifecycle
		 *         callbacks
		 */
		boolean lifecycle() default false;

		/**
		 * Data section name.
		 *
		 * @return data section
		 */
		String data() default "";

		/**
		 * Assertion section names.
		 *
		 * @return assertion sections
		 */
		String[] asserts() default {};

		/**
		 * @return {@code true} to run this runner even when context skipAll is enabled
		 */
		boolean enabled() default false;

		/** @return {@code true} to skip this runner/test execution */
		boolean skip() default false;

	}

	/**
	 * Runtime facade injected by {@link Context}.
	 *
	 * @param <C> framework context type
	 */
	public interface Runtime<C> {

		/**
		 * Returns the low-level JPostman context.
		 *
		 * @return JPostman context
		 */
		io.jpostman.JPostman.Context context();

		/**
		 * Returns the latest active framework context.
		 *
		 * @return active framework context
		 */
		C ctx();

		/**
		 * Returns the framework context for a namespace. Use an empty namespace to
		 * explicitly resolve the default context.
		 *
		 * @param namespace namespace to resolve
		 * @return namespace context
		 */
		C ctx(String namespace);

		/**
		 * Alias for {@link #ctx()}. Use this from an ordinary framework {@code @Test}
		 * method instead of declaring {@code JPostman.Test} as a method parameter.
		 *
		 * <pre>{@code @Test
		 * public void example() {
		 * 	JPostman.Test test = runtime.test();
		 * }
		 * }</pre>
		 *
		 * @return active framework context
		 */
		C test();

		/**
		 * Alias for {@link #ctx(String)}. Use an empty namespace to explicitly resolve
		 * the default context.
		 *
		 * @param namespace namespace to resolve
		 * @return namespace context
		 */
		C test(String namespace);

		/**
		 * Returns the current execution info. Use this from an ordinary framework
		 * {@code @Test} method instead of declaring {@code JPostman.Info} as a method
		 * parameter.
		 *
		 * <pre>{@code @Test
		 * public void example() {
		 * 	JPostman.Info info = runtime.info();
		 * }
		 * }</pre>
		 *
		 * @return execution info
		 */
		Info info();

		/**
		 * Returns the current request log after resolving values collected by the
		 * active annotation helper. Equivalent to {@code log(true)}.
		 *
		 * @return resolved request log
		 */
		default String log() {
			return log(true);
		}

		/**
		 * Returns the current request log.
		 *
		 * @param resolve {@code true} to apply the current annotation info before
		 *                logging, or {@code false} to use the stored unresolved request
		 * @return request log
		 */
		String log(boolean resolve);

		/**
		 * Prints the current request after resolving values collected by the active
		 * annotation helper. Equivalent to {@code print(true)}.
		 */
		default void print() {
			print(true);
		}

		/**
		 * Prints the current request.
		 *
		 * @param resolve {@code true} to apply the current annotation info before
		 *                printing, or {@code false} to print the stored unresolved
		 *                request
		 */
		void print(boolean resolve);

		/**
		 * Returns fluent request-name rules for a {@link Runner} test body.
		 *
		 * @return runner request rules
		 */
		io.jpostman.annotations.runtime.JPostmanRuntime.RunnerRules<C> runner();

		/**
		 * Executes the active request. From a {@link Call} test method, this executes
		 * that method's manual call. From a void {@link Executor} interceptor, this
		 * proceeds with the current response execution and returns before the
		 * interceptor continues.
		 *
		 * @return framework-neutral test context for assertions
		 */
		Test call();

		/**
		 * Executes the active request after applying an optional request callback. From
		 * a void {@link Executor} interceptor, the callback runs immediately before the
		 * response is executed. The interceptor then continues with the latest response
		 * context. Standard request-execution exceptions are converted to synthetic
		 * HTTP-style responses so code after this call can inspect the mapped status,
		 * response body, and original exception.
		 *
		 * @param action request callback receiving the framework context and execution
		 *               info
		 * @return framework-neutral test context for assertions
		 */
		Test call(BiConsumer<C, Info> action);

		/**
		 * Logs a trace message.
		 *
		 * @param args message and optional format arguments to log
		 */
		void logTrace(Object... args);

		/**
		 * Logs a debug message.
		 *
		 * @param args message and optional format arguments to log
		 */
		void logDebug(Object... args);

		/**
		 * Logs an info message.
		 *
		 * @param args message and optional format arguments to log
		 */
		void logInfo(Object... args);

		/**
		 * Logs a warning message.
		 *
		 * @param args message and optional format arguments to log
		 */
		void logWarn(Object... args);

		/**
		 * Logs an error message.
		 *
		 * @param args message and optional format arguments to log
		 */
		void logError(Object... args);

		/**
		 * Returns the collection loaded for the default namespace.
		 *
		 * @return default namespace collection
		 */
		Collection getCollection();

		/**
		 * Returns the collection loaded for a namespace.
		 *
		 * <p>
		 * For example, {@code getCollection("product")} resolves
		 * {@code collection.product} from the configured properties file.
		 * </p>
		 *
		 * @param namespace namespace to resolve, or blank for the default namespace
		 * @return namespace collection
		 */
		default Collection getCollection(String namespace) {
			String key = namespace == null ? "" : namespace.trim();
			if (key.isEmpty()) {
				return getCollection();
			}
			throw new UnsupportedOperationException(
					"Namespace collection access is not supported by this runtime: " + key);
		}

		/**
		 * Returns the environment loaded for the default namespace.
		 *
		 * @return default namespace environment
		 */
		Environment getEnvironment();

		/**
		 * Returns the environment loaded for a namespace.
		 *
		 * <p>
		 * For example, {@code getEnvironment("product")} resolves
		 * {@code environment.product} from the configured properties file.
		 * </p>
		 *
		 * @param namespace namespace to resolve, or blank for the default namespace
		 * @return namespace environment, or {@code null} when none is configured
		 */
		default Environment getEnvironment(String namespace) {
			String key = namespace == null ? "" : namespace.trim();
			if (key.isEmpty()) {
				return getEnvironment();
			}
			throw new UnsupportedOperationException(
					"Namespace environment access is not supported by this runtime: " + key);
		}
	}

	/**
	 * Small mutable reference for values that need to be updated inside Java
	 * lambdas and read after the fluent chain finishes.
	 *
	 * @param <T> referenced value type
	 */
	public static final class Ref<T> {
		private T value;

		/**
		 * Creates an empty reference.
		 */
		public Ref() {
		}

		/**
		 * Creates a reference with an initial value.
		 *
		 * @param value initial value
		 */
		public Ref(T value) {
			this.value = value;
		}

		/**
		 * Returns the current value.
		 *
		 * @return current value
		 */
		public T get() {
			return value;
		}

		/**
		 * Updates the current value and returns this reference for fluent use.
		 *
		 * @param value new value
		 * @return this reference
		 */
		public Ref<T> set(T value) {
			this.value = value;
			return this;
		}

		/**
		 * Adds the supplied value to the current reference value.
		 *
		 * <p>
		 * Strings are concatenated and numbers are added while preserving the current
		 * numeric type when possible. When the current value is null, the supplied
		 * value becomes the reference value. Other value types should use
		 * {@link #set(Object)}.
		 * </p>
		 *
		 * @param value value to add
		 * @return this reference
		 */
		@SuppressWarnings("unchecked")
		public Ref<T> add(T value) {
			if (this.value == null) {
				this.value = value;
				return this;
			}

			if (this.value instanceof CharSequence || value instanceof CharSequence) {
				this.value = (T) (String.valueOf(this.value) + String.valueOf(value));
				return this;
			}

			if (this.value instanceof Number && value instanceof Number) {
				this.value = (T) addNumbers((Number) this.value, (Number) value);
				return this;
			}

			throw new UnsupportedOperationException(
					"JPostman.Ref.add(...) supports String and Number values. Use set(...) for this value type.");
		}

		private static Number addNumbers(Number current, Number value) {
			if (current instanceof BigDecimal || value instanceof BigDecimal) {
				return toBigDecimal(current).add(toBigDecimal(value));
			}
			if (current instanceof BigInteger || value instanceof BigInteger) {
				return toBigInteger(current).add(toBigInteger(value));
			}
			if (current instanceof Double || value instanceof Double) {
				return current.doubleValue() + value.doubleValue();
			}
			if (current instanceof Float || value instanceof Float) {
				return current.floatValue() + value.floatValue();
			}
			if (current instanceof Long || value instanceof Long) {
				return current.longValue() + value.longValue();
			}
			if (current instanceof Short || value instanceof Short) {
				return (short) (current.shortValue() + value.shortValue());
			}
			if (current instanceof Byte || value instanceof Byte) {
				return (byte) (current.byteValue() + value.byteValue());
			}
			return current.intValue() + value.intValue();
		}

		private static BigDecimal toBigDecimal(Number value) {
			return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
		}

		private static BigInteger toBigInteger(Number value) {
			return value instanceof BigInteger ? (BigInteger) value : BigInteger.valueOf(value.longValue());
		}

		/**
		 * Returns true when the reference has no usable value.
		 *
		 * <p>
		 * Null values are empty. Empty strings, empty collections, empty maps, and
		 * empty arrays are also treated as empty. Other non-null values are treated as
		 * not empty.
		 * </p>
		 *
		 * @return true when value is null or empty
		 */
		public boolean isEmpty() {
			if (value == null) {
				return true;
			}
			if (value instanceof CharSequence) {
				return ((CharSequence) value).length() == 0;
			}
			if (value instanceof java.util.Collection<?>) {
				return ((java.util.Collection<?>) value).isEmpty();
			}
			if (value instanceof Map<?, ?>) {
				return ((Map<?, ?>) value).isEmpty();
			}
			if (value.getClass().isArray()) {
				return java.lang.reflect.Array.getLength(value) == 0;
			}
			return false;
		}

		/**
		 * Returns true when the reference value is null.
		 *
		 * @return true when value is null
		 */
		public boolean isNull() {
			return value == null;
		}
	}

	/** Compact facade for execution info. */
	public interface Info {

		/**
		 * Returns the full runtime info object for direct access to execution
		 * attributes such as method, methodIndex, request, namespace, cache, and id.
		 *
		 * @return runtime execution info
		 */
		JPostmanInfo attr();

		/**
		 * Returns the Java test/helper/executor method represented by this info object.
		 *
		 * @return current method name
		 */
		default String method() {
			return attr().method;
		}

		/**
		 * Returns an entry from the current execution method chain relative to the
		 * current invocation.
		 *
		 * <p>
		 * A value of {@code 0} returns the current method-chain entry, {@code 1}
		 * returns the immediately preceding entry, and larger values walk farther back.
		 * When the requested entry is outside the available chain, this method falls
		 * back to {@link #method()}.
		 * </p>
		 *
		 * @param stepsBack number of method-chain entries to move backward; must be
		 *                  zero or greater
		 * @return selected method-chain entry, or the current method name when the
		 *         requested entry is unavailable
		 * @throws IllegalArgumentException when {@code stepsBack} is negative
		 */
		default String method(int stepsBack) {
			if (stepsBack < 0) {
				throw new IllegalArgumentException("stepsBack must be zero or greater.");
			}

			JPostmanInfo info = attr();
			int index = info.methodIndex - stepsBack;
			if (index >= 0 && index < info.methods.size()) {
				return info.methods.get(index);
			}
			return method();
		}

		/**
		 * Returns the current Postman folder name.
		 *
		 * @return current folder name
		 */
		default String folder() {
			return attr().folder;
		}

		/**
		 * Returns the current Postman request name.
		 *
		 * @return current request name
		 */
		default String request() {
			return attr().request;
		}

		/** Returns the latest HTTP or synthetic status code, when available. */
		default Integer statusCode() {
			return attr().statusCode();
		}

		/**
		 * Returns true when JPostman generated the response from an execution
		 * exception.
		 */
		default boolean syntheticResponse() {
			return attr().syntheticResponse();
		}

		/**
		 * Returns the original request-execution exception, or null for a real
		 * response.
		 */
		default Throwable error() {
			return attr().error();
		}

		/** Returns the nested cause used to select the synthetic status code. */
		default Throwable errorCause() {
			return attr().errorCause();
		}

		/** Returns the reason phrase assigned to the synthetic response. */
		default String errorReason() {
			return attr().errorReason();
		}

		/**
		 * Creates an empty mutable reference that can be updated inside Java lambdas.
		 *
		 * @param <T> referenced value type
		 * @return empty reference
		 */
		<T> JPostman.Ref<T> ref();

		/**
		 * Creates a mutable reference with an initial value.
		 *
		 * @param value initial value
		 * @param <T>   referenced value type
		 * @return initialized reference
		 */
		<T> JPostman.Ref<T> ref(T value);

		/**
		 * Returns tag-based rules for the current execution.
		 *
		 * @return tag rules
		 */
		JPostmanInfo.TagRules tags();

		/**
		 * Converts values in the last body/query/header/path/auth group to JSON literal
		 * strings.
		 *
		 * @return updated info
		 */
		JPostmanInfo toJson();

		/**
		 * Adds body values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo body(Object... values);

		/**
		 * Adds secure body values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo sbody(Object... values);

		/**
		 * Adds body values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo body(Map<String, ?> values);

		/**
		 * Adds secure body values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo sbody(Map<String, ?> values);

		/**
		 * Adds global build-time template parameters. These values resolve existing
		 * placeholders across auth, headers, URL/path, query, and body without adding
		 * new component fields.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo params(Object... values);

		/**
		 * Adds secret global build-time template parameters. These values resolve
		 * existing placeholders across auth, headers, URL/path, query, and body without
		 * adding new component fields. Secret values remain masked in diagnostic
		 * output.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo sparams(Object... values);

		/**
		 * Adds global build-time template parameters from an existing map.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo params(Map<String, ?> values);

		/**
		 * Adds secret global build-time template parameters from an existing map.
		 * Secret values remain masked in diagnostic output.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo sparams(Map<String, ?> values);

		/**
		 * Adds query values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo query(Object... values);

		/**
		 * Adds secure query values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo squery(Object... values);

		/**
		 * Adds query values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo query(Map<String, ?> values);

		/**
		 * Adds secure query values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo squery(Map<String, ?> values);

		/**
		 * Adds header values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo headers(Object... values);

		/**
		 * Adds secure header values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo sheaders(Object... values);

		/**
		 * Adds header values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo headers(Map<String, ?> values);

		/**
		 * Adds secure header values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo sheaders(Map<String, ?> values);

		/**
		 * Adds path values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo path(Object... values);

		/**
		 * Adds secure path values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo spath(Object... values);

		/**
		 * Adds path values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo path(Map<String, ?> values);

		/**
		 * Adds secure path values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo spath(Map<String, ?> values);

		/**
		 * Adds auth values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo auth(Object... values);

		/**
		 * Adds secure auth values.
		 *
		 * @param values key/value entries
		 * @return updated info
		 */
		JPostmanInfo sauth(Object... values);

		/**
		 * Adds auth values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo auth(Map<String, ?> values);

		/**
		 * Adds secure auth values.
		 *
		 * @param values value map
		 * @return updated info
		 */
		JPostmanInfo sauth(Map<String, ?> values);

		/**
		 * Builds a readable multi-line log message with full execution info.
		 *
		 * @return formatted runtime info
		 */
		String log();

		/**
		 * Builds a readable multi-line log message with optional full details.
		 *
		 * @param includeAll {@code true} to include method chain and timestamps
		 * @return formatted runtime info
		 */
		String log(boolean includeAll);

		/**
		 * Prints full execution info.
		 */
		void print();

		/**
		 * Prints execution info with optional full details.
		 *
		 * @param includeAll {@code true} to include method chain and timestamps
		 */
		void print(boolean includeAll);
	}

	/**
	 * Compact framework-neutral assertion facade backed by the latest active
	 * JPostman test context.
	 */
	public interface Assert extends JPostmanAssertions<Test, Assert> {

		/**
		 * Verifies that every numeric item at {@code path} satisfies the predicate. The
		 * failure message is optional; the underlying assertion implementation supplies
		 * its normal allMatch diagnostic when this overload is used.
		 *
		 * @param path      response path containing numeric values
		 * @param predicate condition evaluated for every numeric value
		 * @return this fluent assertion facade
		 */
		Assert allMatch(String path, Predicate<Number> predicate);

		/**
		 * Verifies that every item at {@code path} satisfies an index-aware predicate.
		 * Values are exposed as {@link Object}.
		 *
		 * @param path      response path containing values
		 * @param predicate condition receiving the item and its zero-based index
		 * @return this fluent assertion facade
		 */
		Assert allMatch(String path, BiPredicate<Object, Integer> predicate);

		/**
		 * Verifies that every item at {@code path} satisfies a typed, index-aware
		 * predicate.
		 *
		 * @param <V>       item type
		 * @param path      response path containing values
		 * @param type      expected item type
		 * @param predicate condition receiving the typed item and its zero-based index
		 * @return this fluent assertion facade
		 */
		<V> Assert allMatch(String path, Class<V> type, BiPredicate<V, Integer> predicate);

		/**
		 * Creates a temporary soft assertion facade for the current annotated test
		 * method.
		 *
		 * <p>
		 * The returned facade uses the same request-scoped soft collector as
		 * {@code jpostman.ctx().soft()}. Failures are collected while the method body
		 * continues and are verified automatically after an eligible {@link Response},
		 * {@link Call}, or {@link Runner} method exits. The injected
		 * {@code AssertContext} facade itself remains hard, so assertions in the next
		 * test fail immediately unless {@code soft()} is called again.
		 * </p>
		 *
		 * <pre>
		 * asserts.soft().statusCode(200).exists("accessToken");
		 * </pre>
		 *
		 * <p>
		 * Calling {@link #verify()} manually is optional and consumes the same
		 * collector before automatic method-exit verification.
		 * </p>
		 *
		 * @return temporary method-scoped soft assertion facade
		 */
		Assert soft();

		/**
		 * Immediately fails with the supplied custom message.
		 *
		 * <p>
		 * This is always a hard failure. The failure is not added to a soft assertion
		 * collector.
		 * </p>
		 *
		 * @param message custom failure message; blank values use
		 *                {@code "Assertion failed"}
		 * @return never returns normally
		 * @throws AssertionError always, using the supplied message
		 */
		Assert fail(String message);

		/**
		 * Verifies and clears pending assertions for this facade.
		 *
		 * @return active JPostman test context
		 */
		@Override
		Test verify();
	}

	/**
	 * Compact facade for the JPostman report.
	 */
	public interface Report {

		/**
		 * Prints the report summary.
		 */
		void summary();
	}

	/**
	 * Compact framework-neutral test context facade.
	 *
	 * <p>
	 * Cache values are both read and written through {@code cache(...)}. Supported
	 * read expressions are:
	 * </p>
	 * <ul>
	 * <li>{@code cache("token")} &mdash; exact cache key. Exact keys always win
	 * before dependency-path inference.</li>
	 * <li>{@code cache("login/accessToken")} &mdash; exact cache key followed by a
	 * cached-response path.</li>
	 * <li>{@code cache("#login")} &mdash; complete value cached by the annotation
	 * whose {@code id} is {@code login}.</li>
	 * <li>{@code cache("#login:accessToken")} &mdash; response path from the value
	 * cached by annotation id {@code login}.</li>
	 * <li>{@code cache("accessToken")} &mdash; when no exact key exists, treats the
	 * expression as a response path if the current annotated method has exactly one
	 * cached direct dependency.</li>
	 * </ul>
	 *
	 * <p>
	 * Typed reads use {@code cache(expression, Type.class)}. Existing cache writes
	 * remain supported through {@code cache(key, value)}. The inherited
	 * {@code get(String)} method provides a convenient combined lookup using this
	 * precedence: secret, plain, cache expression, then Postman environment.
	 * </p>
	 *
	 * <pre>
	 * test.cache("token", accessToken);
	 * String token = test.cache("token", String.class);
	 * String nested = test.cache("#login:accessToken", String.class);
	 * </pre>
	 *
	 * <p>
	 * Because the typed-read overload accepts {@link Class}, storing a
	 * {@code Class} value or {@code null} requires an explicit {@code Object} cast
	 * so Java selects the write overload:
	 * </p>
	 *
	 * <pre>
	 * test.cache("type", (Object) String.class);
	 * test.cache("optional", (Object) null);
	 * </pre>
	 */
	public interface Test extends JPostmanTestContext<Test, Assert, Assert> {

		/**
		 * Resolves a value from the active test context.
		 *
		 * <p>
		 * Sources are checked in this order:
		 * </p>
		 * <ol>
		 * <li>Protected value registered through {@code secret(...)}.</li>
		 * <li>Plain value registered through {@code plain(...)}.</li>
		 * <li>Cache value using the same expression rules as
		 * {@link #cache(String)}.</li>
		 * <li>Original Postman environment value.</li>
		 * </ol>
		 *
		 * <p>
		 * A secret remains higher priority than a later plain value. Call
		 * {@code unsecret(key)} before {@code plain(key, value)} when intentionally
		 * replacing a secret with a plain value.
		 * </p>
		 *
		 * <pre>
		 * // In a parent Response callback:
		 * test.secret("refreshToken", test.<String>path("refreshToken"));
		 *
		 * // In a dependent Request or Call callback:
		 * String token = test.get("refreshToken");
		 * String explicit = test.get("#login:accessToken");
		 * Long attempts = test.get("attempts", Long.class);
		 * </pre>
		 *
		 * @param key secure/plain key or cache expression
		 * @param <T> resolved value type inferred by the caller
		 * @return first resolved value, or {@code null} when no source contains it
		 * @throws IllegalStateException when cache-path resolution is ambiguous or an
		 *                               explicit cache dependency is unavailable
		 */
		@Override
		default <T> T get(String key) {
			throw new UnsupportedOperationException(
					"JPostman.Test.get(...) is available only through an injected JPostman runtime context.");
		}

		/**
		 * Resolves a value using {@link #get(String)} and converts it to the requested
		 * Java type. This is useful when the stored representation differs from the
		 * desired result type, such as converting a JSON number or numeric string to
		 * {@link Long}.
		 *
		 * <pre>
		 * String token = test.get("token", String.class);
		 * Long attempts = test.get("attempts", Long.class);
		 * </pre>
		 *
		 * @param key  secure/plain key or cache expression
		 * @param type requested Java type
		 * @param <T>  result type
		 * @return converted resolved value
		 */
		default <T> T get(String key, Class<T> type) {
			return JPostmanCacheValueConverter.convert(get(key), type);
		}

		/**
		 * Resolves a value and wraps it in a mutable {@link JPostman.Ref}.
		 *
		 * @param key secure/plain key, cache expression, or environment key
		 * @param <T> resolved value type inferred by the caller
		 * @return mutable reference containing the resolved value
		 */
		default <T> JPostman.Ref<T> getRef(String key) {
			return new JPostman.Ref<>(this.<T>get(key));
		}

		/**
		 * Resolves and converts a value, then wraps it in a mutable
		 * {@link JPostman.Ref}.
		 *
		 * @param key  secure/plain key, cache expression, or environment key
		 * @param type requested Java type
		 * @param <T>  resolved value type
		 * @return mutable reference containing the converted value
		 */
		default <T> JPostman.Ref<T> getRef(String key, Class<T> type) {
			return new JPostman.Ref<>(get(key, type));
		}

		/**
		 * Reads a cache expression.
		 *
		 * <p>
		 * Resolution rules:
		 * </p>
		 * <ol>
		 * <li>An expression beginning with {@code #} resolves an annotation id. Use
		 * {@code #id:path} to read a nested response value.</li>
		 * <li>An expression containing {@code /} resolves the text before the first
		 * slash as an exact cache key and the remaining text as a response path.</li>
		 * <li>A bare expression first resolves as an exact cache key.</li>
		 * <li>If the exact key is absent, a bare expression resolves as a response path
		 * only when exactly one cached direct dependency is available.</li>
		 * </ol>
		 *
		 * <p>
		 * When more than one cached direct dependency is available, use an explicit
		 * annotation-id expression such as {@code cache("#login:accessToken")}.
		 * </p>
		 *
		 * @param expression exact cache key, cache-key/path expression, annotation-id
		 *                   expression, or implicit dependency response path
		 * @param <T>        cached value type inferred by the caller
		 * @return cached value or cached-response path value; an unresolved ordinary
		 *         exact key returns {@code null}
		 * @throws IllegalArgumentException if the expression is blank or an annotation
		 *                                  id is malformed
		 * @throws IllegalStateException    if an explicit annotation id is not cached
		 *                                  or an implicit path is ambiguous
		 */
		@Override
		default <T> T cache(String expression) {
			throw new UnsupportedOperationException(
					"JPostman.Test.cache(...) is available only through an injected JPostman runtime context.");
		}

		/**
		 * Reads a cache expression and converts the result to the requested Java type.
		 * This supports the same expressions as {@link #cache(String)}:
		 *
		 * <pre>
		 * String token = test.cache("token", String.class);
		 * String token = test.cache("login/accessToken", String.class);
		 * String token = test.cache("#login:accessToken", String.class);
		 * String token = test.cache("accessToken", String.class);
		 * </pre>
		 *
		 * <p>
		 * Cache writes continue to use {@code cache(key, value)}. Cast a {@code Class}
		 * value or {@code null} to {@code Object} when writing to avoid selecting this
		 * typed-read overload.
		 * </p>
		 *
		 * @param expression exact cache key, cache-key/path expression, annotation-id
		 *                   expression, or implicit dependency response path
		 * @param type       requested Java type
		 * @param <T>        result type
		 * @return converted cached value
		 */
		default <T> T cache(String expression, Class<T> type) {
			return JPostmanCacheValueConverter.convert(cache(expression), type);
		}

		/**
		 * Stores a value under an exact cache key and returns the active test context
		 * for fluent chaining.
		 *
		 * <pre>
		 * test.cache("token", accessToken);
		 * test.cache("attempts", 3);
		 * </pre>
		 *
		 * <p>
		 * Write keys are stored literally. Read-expression syntax such as
		 * {@code #login:accessToken} and {@code login/accessToken} is interpreted only
		 * by the read overloads. To store a {@link Class} value or {@code null}, cast
		 * the value to {@code Object} so Java selects this write overload:
		 * </p>
		 *
		 * <pre>
		 * test.cache("type", (Object) String.class);
		 * test.cache("optional", (Object) null);
		 * </pre>
		 *
		 * @param key   exact cache key
		 * @param value value to cache; may be {@code null}
		 * @return active test context
		 */
		@Override
		default Test cache(String key, Object value) {
			throw new UnsupportedOperationException(
					"JPostman.Test.cache(...) is available only through an injected JPostman runtime context.");
		}

		/**
		 * Runs a callback after {@link Runtime#call()} has completed and the active
		 * real or synthetic response is available.
		 *
		 * <p>
		 * This method is intended for fluent executor code:
		 * </p>
		 *
		 * <pre>
		 * runtime.call((test, info) -&gt; test.request().print()).response((test, info) -&gt; test.response().print());
		 * </pre>
		 *
		 * <p>
		 * The callback runs exactly once. The supplied test context is the context
		 * returned by the completed call, and the supplied info is the same annotation
		 * execution info associated with that call. The method returns the same test
		 * context so additional fluent operations may follow.
		 * </p>
		 *
		 * @param action response callback receiving the completed test context and
		 *               execution information; {@code null} performs no action
		 * @return the completed test context
		 */
		default Test response(BiConsumer<Test, Info> action) {
			throw new UnsupportedOperationException(
					"JPostman.Test.response(...) is available only on the result returned by runtime.call(...).");
		}
	}
}
