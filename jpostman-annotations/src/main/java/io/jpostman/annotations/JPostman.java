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

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testng.annotations.Listeners;

import io.jpostman.Collection;
import io.jpostman.Environment;
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
		 * {@code diagnostic} and {@code fail} values exclusively control automatic
		 * output.
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
		 * @return expected HTTP status code, or {@code 0} to skip status code
		 *         verification by default
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
		 * Controls automatic JPostman debug and failure output.
		 *
		 * <ul>
		 * <li>{@code none} - disable automatic output and keep the minimum failure
		 * stack.</li>
		 * <li>{@code error} - include the full failure trace.</li>
		 * <li>{@code request} - include the prepared request.</li>
		 * <li>{@code response} - include the received response.</li>
		 * <li>{@code info} - include runtime annotation information.</li>
		 * <li>{@code all} - include request, response, and info.</li>
		 * </ul>
		 *
		 * {@code error} may be combined with request, response, info, or all. Request,
		 * response, and info may be combined. {@code none} must be used alone;
		 * {@code all} may only be combined with {@code error}.
		 *
		 * @return debug output and failure-trace settings
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
		 * Controls report diagnostics printed with the class summary.
		 *
		 * <ul>
		 * <li>{@code none} - disable general execution diagnostics. This is the
		 * default.</li>
		 * <li>{@code short} - append one compact line per recorded execution with the
		 * method, namespace, folder, request, status code, duration, and method
		 * chain.</li>
		 * <li>{@code extend} - append the short line and the prepared request output
		 * for each recorded execution.</li>
		 * </ul>
		 *
		 * @return report diagnostic detail
		 */
		String diagnostic() default "none";

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
		 * <li>{@code error} - keep the full failure stack trace.</li>
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
		 * {@code fail = { "terminate", "request", "error" }}.
		 * </p>
		 *
		 * @return failure action and optional diagnostics
		 */
		String[] fail() default { "ignore" };
	}

	/**
	 * Marks a method as a JPostman executor.
	 */
	@Target(METHOD)
	@Retention(RUNTIME)
	public @interface Executor {

		/**
		 * Executor id.
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
		 * Namespace where this executor interceptor applies. Empty means all namespaces
		 * for void interceptors and the default executor provider for
		 * ApiExecutor-returning methods.
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
		 * suppress output, {@code error} for the full failure trace, or request,
		 * response, info, and all for local diagnostics. {@code error} may be combined
		 * with one or more diagnostic values.
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
		 * Executor id.
		 *
		 * @return executor id
		 */
		String executor() default "";

		/**
		 * Cache key for the method return value.
		 *
		 * @return cache key, or {@link JPostmanRequest#NO_CACHE}
		 */
		String cache() default JPostmanRequest.NO_CACHE;

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, or request,
		 * response, info, and all for local diagnostics. {@code error} may be combined
		 * with one or more diagnostic values.
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

		/** @return {@code true} to skip this request helper or runner request */
		boolean skip() default false;

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
		 * @return expected HTTP status code, {@code -1} to use the context default, or
		 *         {@code 0} to skip status code verification for this response
		 */
		int verify() default -1;

		/**
		 * Executor id.
		 *
		 * @return executor id
		 */
		String executor() default "";

		/**
		 * Cache key for the method return value.
		 *
		 * @return cache key, or {@link JPostmanResponse#NO_CACHE}
		 */
		String cache() default JPostmanResponse.NO_CACHE;

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, or request,
		 * response, info, and all for local diagnostics. {@code error} may be combined
		 * with one or more diagnostic values.
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
		 * Executor id.
		 *
		 * @return executor id
		 */
		String executor() default "";

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, or request,
		 * response, info, and all for local diagnostics. {@code error} may be combined
		 * with one or more diagnostic values.
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
		 * Expected HTTP status code.
		 *
		 * @return expected HTTP status code, {@code -1} to use the context default, or
		 *         {@code 0} to skip status code verification for this runner
		 */
		int verify() default -1;

		/**
		 * Executor id.
		 *
		 * @return executor id
		 */
		String executor() default "";

		/**
		 * Local JPostman debug override.
		 *
		 * <p>
		 * Use {@code debug} to inherit the context debug setting. Use {@code none} to
		 * suppress output, {@code error} for the full failure trace, or request,
		 * response, info, and all for local diagnostics. {@code error} may be combined
		 * with one or more diagnostic values.
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
		 * Alias for {@link #ctx()}.
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
		 * Returns the current execution info.
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
		 * Executes the request described by {@link Call} on the current test method.
		 *
		 * @return framework-neutral test context for assertions
		 */
		Test call();

		/**
		 * Executes the request described by {@link Call} after applying an optional
		 * request customization callback.
		 *
		 * @param action request customization callback receiving the framework context
		 *               and execution info
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

	/** Compact framework-neutral test context facade. */
	public interface Test extends JPostmanTestContext<Test, Assert, Assert> {

		/**
		 * Reads a cached value or cached response path and converts it to the requested
		 * Java type.
		 *
		 * @param expression cache key, optionally followed by a response path
		 * @param type       requested Java type
		 * @param <T>        result type
		 * @return converted cached value
		 */
		default <T> T cache(String expression, Class<T> type) {
			return io.jpostman.annotations.runtime.JPostmanCacheValueConverter.convert(cache(expression), type);
		}
	}
}