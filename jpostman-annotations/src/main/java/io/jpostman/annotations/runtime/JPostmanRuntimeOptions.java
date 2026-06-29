package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.loadProperties;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jpostman.annotations.JPostmanContext;

/** Runtime options resolved from @JPostmanContext and jpostman.properties. */
final class JPostmanRuntimeOptions {

	private enum Level {
		TRACE, DEBUG, INFO, WARN, ERROR;

		static Level from(String value) {
			if (value == null || value.isBlank()) {
				return INFO;
			}
			try {
				return Level.valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Unsupported @JPostmanContext logLevel: " + value
						+ ". Supported values: TRACE, DEBUG, INFO, WARN, ERROR.", e);
			}
		}

		boolean isTrace() {
			return this == TRACE;
		}

		boolean isDebugOrTrace() {
			return this == TRACE || this == DEBUG;
		}
	}

	private final boolean logs;
	private final Level debug;
	private final String debugFormat;
	private final int defaultStatusCode;
	private final Class<?> executorClass;
	private final boolean session;

	private JPostmanRuntimeOptions(boolean logs, Level debug, String debugFormat, int defaultStatusCode,
			Class<?> executorClass, boolean session) {
		this.logs = logs;
		this.debug = debug;
		this.debugFormat = debugFormat == null || debugFormat.isBlank() ? "=== {} ===" : debugFormat;
		this.defaultStatusCode = defaultStatusCode;
		this.executorClass = executorClass == Void.class ? null : executorClass;
		this.session = session;
	}

	static JPostmanRuntimeOptions from(Object testInstance) {
		JPostmanContext annotation = findContextAnnotation(testInstance);
		if (annotation == null) {
			return new JPostmanRuntimeOptions(false, Level.INFO, "=== {} ===", -1, null, false);
		}

		boolean logs = annotation.logs();
		String debug = annotation.logLevel();
		String debugFormat = annotation.debugFormat();
		int defaultStatusCode = annotation.verifyStatusCode();
		Class<?> executorClass = annotation.executor();
		boolean session = annotation.session();

		try {
			Properties properties = loadProperties(annotation.config(), testInstance.getClass());
			logs = booleanValue(property(properties, "logs", annotation.namespace()), logs);
			debug = stringValue(property(properties, "logLevel", annotation.namespace()), debug);
			debugFormat = stringValue(property(properties, "debugFormat", annotation.namespace()), debugFormat);
			defaultStatusCode = intValue(property(properties, "defaultStatusCode", annotation.namespace()),
					defaultStatusCode);
			executorClass = classValue(property(properties, "executor", annotation.namespace()), executorClass,
					testInstance.getClass().getClassLoader(), annotation);
			session = booleanValue(property(properties, "session", annotation.namespace()), session);
			session = booleanValue(property(properties, "cookie", annotation.namespace()), session);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load JPostman runtime options from " + annotation.config(), e);
		}

		return new JPostmanRuntimeOptions(logs, Level.from(debug), debugFormat, defaultStatusCode, executorClass,
				session);
	}

	boolean hasDefaultExecutor() {
		return executorClass != null && executorClass != Void.class;
	}

	Class<?> executorClass() {
		return executorClass;
	}

	boolean session() {
		return session;
	}

	int statusCode(int annotationVerify) {
		return annotationVerify >= 0 ? annotationVerify : defaultStatusCode;
	}

	boolean log(boolean annotationLog) {
		return annotationLog || logs;
	}

	void debug(Object testInstance, JPostmanInfo info) {
		if (testInstance == null || info == null) {
			return;
		}

		Level level = info.debug == null || info.debug.isBlank() ? debug : Level.from(info.debug);
		Logger logger = LoggerFactory.getLogger(testInstance.getClass());
		if (level.isDebugOrTrace()) {
			logger.debug(MessageFormat.format(debugFormat, info.callee, info.annotation));
		}
		if (level.isTrace()) {
			info.print();
		}
	}

	private static JPostmanContext findContextAnnotation(Object testInstance) {
		if (testInstance == null) {
			return null;
		}

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				JPostmanContext annotation = JPostmanAnnotations.context(field);
				if (annotation != null) {
					return annotation;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private static String stringValue(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static boolean booleanValue(String value, boolean fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return Boolean.parseBoolean(value.trim());
	}

	private static Class<?> classValue(String value, Class<?> fallback, ClassLoader loader,
			JPostmanContext annotation) {
		if (value == null || value.isBlank()) {
			return fallback == Void.class ? null : fallback;
		}

		String name = value.trim();
		if (name.endsWith(".class")) {
			name = name.substring(0, name.length() - ".class".length()).trim();
		}

		try {
			return Class.forName(name, true, loader);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(JPostmanErrors.message(annotation,
					"Invalid JPostman executor class: " + name, "The executor class could not be loaded.",
					"Use a fully qualified class name available on the test classpath.",
					"Example: executor = \"io.jpostman.restassured.RestAssuredExecutor\"."));
		}
	}

	private static int intValue(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid @JPostmanContext defaultStatusCode: " + value, e);
		}
	}
}
