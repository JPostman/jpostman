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
import io.jpostman.secure.JPostmanSoftAssertions;
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
		 * Prints cleaned failure stack traces for JUnit execution.
		 *
		 * @return {@code true} to print cleaned failures
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
		 * Controls automatic JPostman failure output. Values may combine one stack mode
		 * with optional failure diagnostics.
		 *
		 * <ul>
		 * <li>{@code none} - print only the minimum failure message and the first
		 * useful user-code stack frame. This is the default.</li>
		 * <li>{@code error} - print the failure message and include the trace.</li>
		 * <li>{@code request} - include the prepared request when a failure
		 * occurs.</li>
		 * <li>{@code response} - include the received response when a failure
		 * occurs.</li>
		 * <li>{@code info} - include runtime annotation info when a failure
		 * occurs.</li>
		 * <li>{@code all} - include request, response, and info when a failure
		 * occurs.</li>
		 * </ul>
		 *
		 * Examples: {@code logs = "request"}, {@code logs = { "request", "response" }},
		 * or {@code logs = { "error", "response" }}.
		 *
		 * @return automatic failure output mode and diagnostics
		 */
		String[] logs() default { "none" };

		/**
		 * Controls automatic annotation output.
		 *
		 * <ul>
		 * <li>{@code none} - do not print automatic annotation output.</li>
		 * <li>{@code request} - print the prepared request.</li>
		 * <li>{@code response} - print the received response.</li>
		 * <li>{@code info} - print runtime annotation information.</li>
		 * <li>{@code all} - print request, response, and info output.</li>
		 * </ul>
		 *
		 * {@code request}, {@code response}, and {@code info} may be combined.
		 * {@code none} and {@code all} must be used alone.
		 *
		 * @return debug output mode values
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
	}

	/**
	 * Short alias for {@link AssertContext}.
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface Asserts {
	}

	/**
	 * Injects the JPostman report facade.
	 */
	@Target(FIELD)
	@Retention(RUNTIME)
	public @interface ReportContext {
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
		 * Local automatic JPostman failure output mode. Values are single-choice; use
		 * one value only.
		 *
		 * <ul>
		 * <li>{@code none} - print only the minimum failure message and the first
		 * useful user-code stack frame.</li>
		 * <li>{@code debug} - print the configured debug output and use minimum failure
		 * output when debug is {@code none}.</li>
		 * <li>{@code error} - print the failure message and include the trace.</li>
		 * </ul>
		 *
		 * @return local automatic failure output mode
		 */
		String log() default "debug";

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
		 * Collection folder name.
		 *
		 * @return folder name
		 */
		String folder() default "";

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
		 * Local automatic JPostman failure output mode. Values are single-choice; use
		 * one value only.
		 *
		 * <ul>
		 * <li>{@code none} - print only the minimum failure message and the first
		 * useful user-code stack frame.</li>
		 * <li>{@code debug} - print the configured debug output and use minimum failure
		 * output when debug is {@code none}.</li>
		 * <li>{@code error} - print the failure message and include the trace.</li>
		 * </ul>
		 *
		 * @return local automatic failure output mode
		 */
		String log() default "debug";

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
		 * Collection folder name.
		 *
		 * @return folder name
		 */
		String folder() default "";

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
		 * Local automatic JPostman failure output mode. Values are single-choice; use
		 * one value only.
		 *
		 * <ul>
		 * <li>{@code none} - print only the minimum failure message and the first
		 * useful user-code stack frame.</li>
		 * <li>{@code debug} - print the configured debug output and use minimum failure
		 * output when debug is {@code none}.</li>
		 * <li>{@code error} - print the failure message and include the trace.</li>
		 * </ul>
		 *
		 * @return local automatic failure output mode
		 */
		String log() default "debug";

		/**
		 * Enables soft assertion mode.
		 *
		 * @return {@code true} to collect assertion failures
		 */
		boolean soft() default false;

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
	 * {@link Runtime#request()} or {@link Runtime#request(BiConsumer)}.
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
		 * Collection folder name.
		 *
		 * @return folder name
		 */
		String folder() default "";

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
		 * Local automatic JPostman failure output mode. Values are single-choice; use
		 * one value only.
		 *
		 * <ul>
		 * <li>{@code none} - print only the minimum failure message and the first
		 * useful user-code stack frame.</li>
		 * <li>{@code debug} - print the configured debug output and use minimum failure
		 * output when debug is {@code none}.</li>
		 * <li>{@code error} - print the failure message and include the trace.</li>
		 * </ul>
		 *
		 * @return local automatic failure output mode
		 */
		String log() default "debug";

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
		 * Collection folder name.
		 *
		 * @return folder name
		 */
		String folder() default "";

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
		 * Local automatic JPostman failure output mode. Values are single-choice; use
		 * one value only.
		 *
		 * <ul>
		 * <li>{@code none} - print only the minimum failure message and the first
		 * useful user-code stack frame.</li>
		 * <li>{@code debug} - print the configured debug output and use minimum failure
		 * output when debug is {@code none}.</li>
		 * <li>{@code error} - print the failure message and include the trace.</li>
		 * </ul>
		 *
		 * @return local automatic failure output mode
		 */
		String log() default "debug";

		/**
		 * Enables soft assertion mode.
		 *
		 * @return {@code true} to collect assertion failures
		 */
		boolean soft() default false;

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
		 * Returns the current execution info.
		 *
		 * @return execution info
		 */
		Info info();

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
		Test request();

		/**
		 * Executes the request described by {@link Call} after applying an optional
		 * request customization callback.
		 *
		 * @param action request customization callback receiving the framework context
		 *               and execution info
		 * @return framework-neutral test context for assertions
		 */
		Test request(BiConsumer<C, Info> action);

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
		 * Returns the loaded collection.
		 *
		 * @return collection
		 */
		Collection getCollection();

		/**
		 * Returns the loaded environment.
		 *
		 * @return environment
		 */
		Environment getEnvironment();
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
		 * Makes the next body/query/header customization add a new value instead of
		 * using the default set/resolve behavior.
		 *
		 * @return updated info
		 */
		JPostmanInfo add();

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
	public interface Assert extends JPostmanAssertions<Test, Assert>, JPostmanSoftAssertions<Test, Assert> {

		/**
		 * Switches this facade to soft assertion mode.
		 *
		 * @param log {@code true} to include assertion diagnostic logs
		 * @return soft assertion facade
		 */
		Assert soft(boolean log);
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
	}
}