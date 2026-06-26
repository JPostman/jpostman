package io.jpostman;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience entry point for loading a Postman collection and, optionally, a
 * Postman environment.
 *
 * <p>
 * This class provides a small fluent-style API for loading exported Postman
 * resources and retrieving resolved {@link Request} objects by name.
 * </p>
 */
public final class JPostman {

	private JPostman() {
		// Utility class
	}

	/**
	 * Loads a Postman collection from an input stream without an environment.
	 *
	 * <p>
	 * Requests returned from the created {@link Context} are not resolved against
	 * environment variables unless values are supplied later through request
	 * builders or other APIs.
	 * </p>
	 *
	 * @param col input stream containing the exported Postman collection JSON
	 * @return a context containing the loaded collection
	 * @throws IOException          if the collection stream cannot be read or
	 *                              parsed
	 * @throws NullPointerException if {@code col} is {@code null}
	 */
	public static Context load(InputStream col) throws IOException {
		return load(col, null);
	}

	/**
	 * Loads a Postman collection and an optional Postman environment from input
	 * streams.
	 *
	 * <p>
	 * If {@code env} is {@code null}, only the collection is loaded. If an
	 * environment stream is provided, requests returned from the created
	 * {@link Context} are resolved against that environment.
	 * </p>
	 *
	 * @param col input stream containing the exported Postman collection JSON
	 * @param env input stream containing the exported Postman environment JSON; may
	 *            be {@code null}
	 * @return a context containing the loaded collection and optional environment
	 * @throws IOException          if the collection or environment stream cannot
	 *                              be read or parsed
	 * @throws NullPointerException if {@code col} is {@code null}
	 */
	public static Context load(InputStream col, InputStream env) throws IOException {
		Objects.requireNonNull(col, "collection stream must not be null");

		Collection collection = Collection.load(col);

		Environment environment = null;

		if (env != null) {
			environment = Environment.load(env);
		}

		return new Context(collection, environment);
	}

	/**
	 * Represents a loaded Postman collection with an optional environment.
	 *
	 * <p>
	 * A context is used to retrieve folders and requests from the loaded
	 * collection. When an environment is available, returned requests are resolved
	 * against that environment before being returned.
	 * </p>
	 */
	public static final class Context {

		private final Logger log;
		private final Collection collection;
		private final Environment environment;

		/**
		 * Creates a context for a loaded collection and optional environment.
		 *
		 * <p>
		 * The default logger owner is {@link JPostman}. Annotation integrations may
		 * create a copy of this context with a test-class logger by using
		 * {@link #logger(Class)}.
		 * </p>
		 *
		 * @param collection  loaded Postman collection
		 * @param environment loaded Postman environment, or {@code null}
		 * @throws NullPointerException if {@code collection} is {@code null}
		 */
		private Context(Collection collection, Environment environment) {
			this(collection, environment, JPostman.class);
		}

		/**
		 * Creates a context with a specific logger owner class.
		 *
		 * <p>
		 * This constructor is used internally so framework integrations can make
		 * context logging appear under the user's test class, for example
		 * {@code DemoTest}, instead of {@code io.jpostman.JPostman$Context}.
		 * </p>
		 *
		 * @param collection  loaded Postman collection
		 * @param environment loaded Postman environment, or {@code null}
		 * @param logClass    class used as the SLF4J logger name; when {@code null},
		 *                    {@link JPostman} is used
		 * @throws NullPointerException if {@code collection} is {@code null}
		 */
		private Context(Collection collection, Environment environment, Class<?> logClass) {
			this.collection = Objects.requireNonNull(collection, "collection must not be null");
			this.environment = environment;
			this.log = LoggerFactory.getLogger(Objects.requireNonNull(logClass, "logClass must not be null"));
		}

		/**
		 * Returns a copy of this context that uses the supplied class as its logger
		 * owner.
		 *
		 * <p>
		 * The loaded collection and environment are reused. Only the logger name
		 * changes. This is useful for annotation runners that want
		 * {@code jctx.debug(...)} to log under the user's test class.
		 * </p>
		 *
		 * @param logClass class used as the SLF4J logger name
		 * @return context copy with the same collection/environment and a different
		 *         logger
		 */
		public Context logger(Class<?> logClass) {
			return new Context(collection, environment, logClass);
		}

		/**
		 * Returns the SLF4J logger used by this context.
		 *
		 * @return logger used for context-level log messages
		 */
		public Logger log() {
			return log;
		}

		/**
		 * Logs a TRACE message using the context logger.
		 *
		 * <p>
		 * Supports both plain messages and SLF4J-style formatted messages.
		 * </p>
		 *
		 * @param message log message
		 * @param args    optional message arguments
		 */
		public void trace(String message, Object... args) {
			log.trace(message, args);
		}

		/**
		 * Logs a DEBUG message using the context logger.
		 *
		 * <p>
		 * Supports both plain messages and SLF4J-style formatted messages.
		 * </p>
		 *
		 * @param message log message
		 * @param args    optional message arguments
		 */
		public void debug(String message, Object... args) {
			log.debug(message, args);
		}

		/**
		 * Logs an INFO message using the context logger.
		 *
		 * <p>
		 * Supports both plain messages and SLF4J-style formatted messages.
		 * </p>
		 *
		 * @param message log message
		 * @param args    optional message arguments
		 */
		public void info(String message, Object... args) {
			log.info(message, args);
		}

		/**
		 * Logs a WARN message using the context logger.
		 *
		 * <p>
		 * Supports both plain messages and SLF4J-style formatted messages.
		 * </p>
		 *
		 * @param message log message
		 * @param args    optional message arguments
		 */
		public void warn(String message, Object... args) {
			log.warn(message, args);
		}

		/**
		 * Logs an ERROR message using the context logger.
		 *
		 * <p>
		 * Supports both plain messages and SLF4J-style formatted messages.
		 * </p>
		 *
		 * @param message log message
		 * @param args    optional message arguments
		 */
		public void error(String message, Object... args) {
			log.error(message, args);
		}

		/**
		 * Looks up a folder by name.
		 *
		 * @param folderName folder name from the Postman collection
		 * @return matching folder
		 * @throws NullPointerException     if {@code folderName} is {@code null}
		 * @throws IllegalArgumentException if the folder cannot be found by name
		 */
		public Folder folder(String folderName) {
			Objects.requireNonNull(folderName, "folderName must not be null");
			return collection.getFolder(folderName);
		}

		/**
		 * Finds a request by name from the loaded collection.
		 *
		 * <p>
		 * If this context has an environment, the request is resolved against that
		 * environment before it is returned.
		 * </p>
		 *
		 * @param requestName request name from the Postman collection
		 * @return matching request, resolved if an environment is loaded
		 * @throws NullPointerException     if {@code requestName} is {@code null}
		 * @throws IllegalArgumentException if the request cannot be found by name
		 */
		public Request request(String requestName) {
			Objects.requireNonNull(requestName, "requestName must not be null");

			Request request = collection.getRequest(requestName);

			if (environment != null) {
				return request.builder().build(environment);
			}

			return request;
		}

		/**
		 * Returns the loaded Postman collection.
		 *
		 * @return loaded collection
		 */
		public Collection getCollection() {
			return collection;
		}

		/**
		 * Returns the loaded Postman environment.
		 *
		 * @return loaded environment, or {@code null} if no environment was loaded
		 */
		public Environment getEnvironment() {
			return environment;
		}
	}
}
