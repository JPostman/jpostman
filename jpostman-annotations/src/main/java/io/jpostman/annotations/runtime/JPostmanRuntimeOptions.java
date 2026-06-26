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
import io.jpostman.annotations.JPostmanInfo;

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
				throw new IllegalArgumentException("Unsupported @JPostmanContext debug level: " + value
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

	private JPostmanRuntimeOptions(boolean logs, Level debug, String debugFormat, int defaultStatusCode) {
		this.logs = logs;
		this.debug = debug;
		this.debugFormat = debugFormat == null || debugFormat.isBlank() ? "=== {} ===" : debugFormat;
		this.defaultStatusCode = defaultStatusCode;
	}

	static JPostmanRuntimeOptions from(Object testInstance) {
		JPostmanContext annotation = findContextAnnotation(testInstance);
		if (annotation == null) {
			return new JPostmanRuntimeOptions(false, Level.INFO, "=== {} ===", -1);
		}

		boolean logs = annotation.logs();
		String debug = annotation.debug();
		String debugFormat = annotation.debugFormat();
		int defaultStatusCode = annotation.verifyStatusCode();

		try {
			Properties properties = loadProperties(annotation.config(), testInstance.getClass());
			logs = booleanValue(property(properties, "logs", annotation.namespace()), logs);
			debug = stringValue(property(properties, "debug", annotation.namespace()), debug);
			debugFormat = stringValue(property(properties, "debugFormat", annotation.namespace()), debugFormat);
			defaultStatusCode = intValue(property(properties, "defaultStatusCode", annotation.namespace()),
					defaultStatusCode);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load JPostman runtime options from " + annotation.config(), e);
		}

		return new JPostmanRuntimeOptions(logs, Level.from(debug), debugFormat, defaultStatusCode);
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

		Logger logger = LoggerFactory.getLogger(testInstance.getClass());
		if (debug.isDebugOrTrace()) {
			logger.debug(MessageFormat.format(debugFormat, info.callee, info.annotation));
		}
		if (debug.isTrace()) {
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
				JPostmanContext annotation = field.getAnnotation(JPostmanContext.class);
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
