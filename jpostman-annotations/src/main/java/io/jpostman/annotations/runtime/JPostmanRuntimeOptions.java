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

	private final boolean logs;
	private final EnumSet<LogOutput> logOutput;
	private final int defaultStatusCode;
	private final Class<?> executorClass;
	private final boolean session;

	private JPostmanRuntimeOptions(boolean logs, EnumSet<LogOutput> logOutput, int defaultStatusCode,
			Class<?> executorClass, boolean session) {
		this.logs = logs;
		this.logOutput = logOutput == null || logOutput.isEmpty() ? EnumSet.of(LogOutput.NONE)
				: EnumSet.copyOf(logOutput);
		this.defaultStatusCode = defaultStatusCode;
		this.executorClass = executorClass == Void.class ? null : executorClass;
		this.session = session;
	}

	static JPostmanRuntimeOptions from(Object testInstance) {
		JPostmanContext annotation = findContextAnnotation(testInstance);
		if (annotation == null) {
			return new JPostmanRuntimeOptions(false, EnumSet.of(LogOutput.NONE), -1, null, false);
		}

		boolean logs = annotation.logs();
		String[] output = annotation.debug();
		int defaultStatusCode = annotation.verifyStatusCode();
		Class<?> executorClass = annotation.executor();
		boolean session = annotation.session();

		try {
			Properties properties = loadProperties(annotation.config(), testInstance.getClass());
			String namespace = "";
			logs = booleanValue(property(properties, "logs", namespace), logs);
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

		return new JPostmanRuntimeOptions(logs, LogOutput.from(output), defaultStatusCode, executorClass, session);
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

	boolean log(boolean annotationLog) {
		return annotationLog || logs;
	}

	EnumSet<LogOutput> logOutput(JPostmanInfo info) {
		String local = info == null ? "" : info.debug;
		return local == null || local.isBlank() ? EnumSet.copyOf(logOutput) : LogOutput.from(local);
	}

	void debug(Object testInstance, JPostmanInfo info) {
		if (testInstance == null || info == null) {
			return;
		}

		if (logOutput(info).contains(LogOutput.INFO)) {
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
