package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.loadProperties;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import io.jpostman.annotations.JPostmanContext;

/** Runtime options resolved from @JPostmanContext and jpostman.properties. */
final class JPostmanRuntimeOptions {

	enum LogOutput {
		NONE, REQUEST, RESPONSE, INFO, ALL;

		static EnumSet<LogOutput> from(String... values) {
			EnumSet<LogOutput> result = EnumSet.noneOf(LogOutput.class);
			if (values != null) {
				for (String value : values) {
					parseValue(result, value);
				}
			}
			if (result.isEmpty()) {
				result.add(NONE);
			}
			validate(result, values);
			return result;
		}

		private static void parseValue(EnumSet<LogOutput> result, String value) {
			if (value == null || value.isBlank()) {
				return;
			}
			for (String part : value.split(",")) {
				String item = part.trim();
				if (item.isEmpty()) {
					continue;
				}
				try {
					result.add(LogOutput.valueOf(item.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Unsupported JPostman debug: " + item
							+ ". Supported values: none, request, response, info, all.", e);
				}
			}
		}

		private static void validate(EnumSet<LogOutput> result, String... rawValues) {
			if (result.size() > 1 && result.contains(NONE)) {
				throw new IllegalArgumentException(
						"JPostman debug=none must be used alone: " + Arrays.toString(rawValues));
			}
			if (result.size() > 1 && result.contains(ALL)) {
				throw new IllegalArgumentException(
						"JPostman debug=all must be used alone: " + Arrays.toString(rawValues));
			}
		}
	}

	enum LogMode {
		NONE, DEBUG, ERROR;

		static LogMode from(String... values) {
			LogMode result = null;
			if (values != null) {
				for (String value : values) {
					result = parseValue(result, value, values);
				}
			}
			return result == null ? DEBUG : result;
		}

		static LogMode from(String value, LogMode fallback) {
			if (value == null || value.isBlank()) {
				return fallback == null ? DEBUG : fallback;
			}
			return from(value);
		}

		static void validateLocal(String value) {
			from(value, DEBUG);
		}

		private static LogMode parseValue(LogMode current, String value, String... rawValues) {
			if (value == null || value.isBlank()) {
				return current;
			}
			for (String part : value.split(",")) {
				String item = part.trim();
				if (item.isEmpty()) {
					continue;
				}
				if (current != null) {
					throw new IllegalArgumentException(
							"JPostman logs/log must use one value only: " + Arrays.toString(rawValues));
				}
				try {
					current = LogMode.valueOf(item.toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(
							"Unsupported JPostman logs/log: " + item + ". Supported values: none, debug, error.", e);
				}
			}
			return current;
		}
	}

	private static final class LogModeMarker extends Exception {
		private static final long serialVersionUID = 1L;
		private final LogMode mode;

		private LogModeMarker(LogMode mode) {
			this.mode = mode;
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}

	private final LogMode logs;
	private final EnumSet<LogOutput> logOutput;
	private final int defaultStatusCode;
	private final Class<?> executorClass;
	private final boolean session;

	private JPostmanRuntimeOptions(LogMode logs, EnumSet<LogOutput> logOutput, int defaultStatusCode,
			Class<?> executorClass, boolean session) {
		this.logs = logs == null ? LogMode.DEBUG : logs;
		this.logOutput = logOutput == null || logOutput.isEmpty() ? EnumSet.of(LogOutput.NONE)
				: EnumSet.copyOf(logOutput);
		this.defaultStatusCode = defaultStatusCode;
		this.executorClass = executorClass == Void.class ? null : executorClass;
		this.session = session;
	}

	static JPostmanRuntimeOptions from(Object testInstance) {
		JPostmanContext annotation = findContextAnnotation(testInstance);
		if (annotation == null) {
			return new JPostmanRuntimeOptions(LogMode.DEBUG, EnumSet.of(LogOutput.NONE), -1, null, false);
		}

		String[] logs = annotation.logs();
		String[] output = annotation.debug();
		int defaultStatusCode = annotation.verifyStatusCode();
		Class<?> executorClass = annotation.executor();
		boolean session = annotation.session();

		try {
			Properties properties = loadProperties(annotation.config(), testInstance.getClass());
			String namespace = "";
			logs = stringValues(property(properties, "logs", namespace), logs);
			output = stringValues(property(properties, "debug", namespace),
					stringValues(property(properties, "logOutput", namespace), output));
			defaultStatusCode = intValue(property(properties, "defaultStatusCode", namespace), defaultStatusCode);
			executorClass = classValue(property(properties, "executor", namespace), executorClass,
					testInstance.getClass().getClassLoader(), annotation);
			session = booleanValue(property(properties, "session", namespace), session);
			session = booleanValue(property(properties, "cookie", namespace), session);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load JPostman runtime options from " + annotation.config(), e);
		}

		return new JPostmanRuntimeOptions(LogMode.from(logs), LogOutput.from(output), defaultStatusCode, executorClass,
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
		if (annotationVerify > 0) {
			return annotationVerify;
		}
		if (annotationVerify == 0) {
			return 0;
		}
		return defaultStatusCode;
	}

	boolean minimumErrorOutput() {
		return logs == LogMode.NONE || logs == LogMode.DEBUG;
	}

	boolean errorStackTrace() {
		return logs == LogMode.ERROR;
	}

	boolean minimumErrorOutput(String localLog) {
		LogMode mode = logMode(localLog);
		return mode == LogMode.NONE || mode == LogMode.DEBUG;
	}

	void markFailure(Throwable error, String localLog) {
		if (error == null) {
			return;
		}
		if (findMarkedLogMode(error) != null) {
			return;
		}
		error.addSuppressed(new LogModeMarker(logMode(localLog)));
	}

	boolean minimumErrorOutput(Throwable error) {
		LogMode marked = findMarkedLogMode(error);
		if (marked != null) {
			return marked == LogMode.NONE || marked == LogMode.DEBUG;
		}
		return minimumErrorOutput();
	}

	boolean failureDiagnostics(Throwable error) {
		if (hasDiagnosticSuppressed(error)) {
			return true;
		}
		return failureDiagnostics();
	}

	private static LogMode findMarkedLogMode(Throwable error) {
		Throwable current = error;
		while (current != null) {
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed instanceof LogModeMarker) {
					return ((LogModeMarker) suppressed).mode;
				}
			}
			current = current.getCause();
		}
		return null;
	}

	private static boolean hasDiagnosticSuppressed(Throwable error) {
		Throwable current = error;
		while (current != null) {
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed instanceof LogModeMarker) {
					continue;
				}
				String message = suppressed == null ? null : suppressed.getMessage();
				if (message != null && !message.isBlank()) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}

	boolean failureRequest() {
		return failureRequest(null, null);
	}

	boolean failureRequest(String localLog, JPostmanInfo info) {
		EnumSet<LogOutput> outputs = failureDebugOutput(localLog, info);
		return outputs.contains(LogOutput.REQUEST) || outputs.contains(LogOutput.ALL);
	}

	boolean failureResponse() {
		return failureResponse(null, null);
	}

	boolean failureResponse(String localLog, JPostmanInfo info) {
		EnumSet<LogOutput> outputs = failureDebugOutput(localLog, info);
		return outputs.contains(LogOutput.RESPONSE) || outputs.contains(LogOutput.ALL);
	}

	boolean failureInfo(String localLog, JPostmanInfo info) {
		EnumSet<LogOutput> outputs = failureDebugOutput(localLog, info);
		return outputs.contains(LogOutput.INFO) || outputs.contains(LogOutput.ALL);
	}

	boolean failureDiagnostics() {
		return failureDiagnostics(null, null);
	}

	boolean failureDiagnostics(String localLog, JPostmanInfo info) {
		return !failureDebugOutput(localLog, info).contains(LogOutput.NONE);
	}

	boolean runtimeTraceDebugInfoWarn() {
		return true;
	}

	boolean runtimeError() {
		return true;
	}

	private EnumSet<LogOutput> failureDebugOutput(String localLog, JPostmanInfo info) {
		return logOutput(localLog, info);
	}

	private LogMode logMode(String localLog) {
		return LogMode.from(localLog, logs);
	}

	EnumSet<LogOutput> logOutput(String localLog, JPostmanInfo info) {
		LogMode mode = logMode(localLog);
		if (mode != LogMode.DEBUG) {
			return EnumSet.of(LogOutput.NONE);
		}
		return debugOutput(info);
	}

	EnumSet<LogOutput> debugOutput(JPostmanInfo info) {
		String local = info == null ? "" : info.debug;
		return local == null || local.isBlank() ? EnumSet.copyOf(logOutput) : LogOutput.from(local);
	}

	void debug(Object testInstance, JPostmanInfo info) {
		if (testInstance == null || info == null) {
			return;
		}

		if (debugOutput(info).contains(LogOutput.INFO)) {
			printMethodHeader(testInstance, info);
			info.print(false);
		}
	}

	static void printMethodHeader(Object testInstance, JPostmanInfo info) {
		if (testInstance == null || info == null) {
			return;
		}
		LoggerFactory.getLogger(testInstance.getClass()).debug(" === {} === ", info.method);
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

	private static String[] stringValues(String value, String[] fallback) {
		return value == null || value.isBlank() ? fallback : new String[] { value };
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
			throw new IllegalArgumentException("Unable to load @JPostmanContext executor class: " + name, e);
		}
	}

	private static int intValue(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return Integer.parseInt(value.trim());
	}
}
