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
		 * Enables annotation logging for this context.
		 *
		 * @return {@code true} to enable context logging
		 */
		boolean logs() default false;

		/**
		 * Annotation log level.
		 *
		 * @return log level, or empty string to use defaults
		 */
		String logLevel() default "";

		/**
		 * Annotation log message format.
		 *
		 * @return log format
		 */
		String debugFormat() default "=== {1}: {0} ===";
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
		 * Executor log level.
		 *
		 * @return log level, or empty string to use defaults
		 */
		String logLevel() default "";
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
		 * Enables logging for this request.
		 *
		 * @return {@code true} to log this request
		 */
		boolean log() default false;

		/**
		 * Data section name.
		 *
		 * @return data section
		 */
		String data() default "";

		/**
		 * Request log level.
		 *
		 * @return log level, or empty string to use defaults
		 */
		String logLevel() default "";

		/** @return {@code true} to skip this request helper or runner request */
		boolean skip() default false;

		/** @return optional skip reason shown in framework skip output */
		String skipReason() default "";
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
		 * Enables logging for this response.
		 *
		 * @return {@code true} to log this response
		 */
		boolean log() default false;

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
		 * Response log level.
		 *
		 * @return log level, or empty string to use defaults
		 */
		String logLevel() default "";

		/**
		 * @return {@code true} to run this response even when context skipAll is
		 *         enabled
		 */
		boolean enabled() default false;

		/** @return {@code true} to skip this response/test execution */
		boolean skip() default false;

		/** @return optional skip reason shown in framework skip output */
		String skipReason() default "";
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
		 * Enables logging for this runner.
		 *
		 * @return {@code true} to log runner execution
		 */
		boolean log() default false;

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
		 * Runner log level.
		 *
		 * @return log level, or empty string to use defaults
		 */
		String logLevel() default "";

		/**
		 * @return {@code true} to run this runner even when context skipAll is enabled
		 */
		boolean enabled() default false;
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
		 * Logs a trace message.
		 *
		 * @param message message to log
		 */
		void logTrace(String message);

		/**
		 * Logs a debug message.
		 *
		 * @param message message to log
		 */
		void logDebug(String message);

		/**
		 * Logs an info message.
		 *
		 * @param message message to log
		 */
		void logInfo(String message);

		/**
		 * Logs a warning message.
		 *
		 * @param message message to log
		 */
		void logWarn(String message);

		/**
		 * Logs an error message.
		 *
		 * @param message message to log
		 */
		void logError(String message);

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
		 * Prints execution info.
		 */
		void print();
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
	public interface Test extends JPostmanTestContext<Test, JPostmanTestAssertions, JPostmanTestSoftAssertions> {
	}
}