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
import io.jpostman.annotations.JPostmanOutputs;

/** Runtime options resolved from @JPostmanContext and jpostman.properties. */
final class JPostmanRuntimeOptions {

	enum DebugOutput {
		NONE, REQUEST, RESPONSE, INFO, ALL;

		static EnumSet<DebugOutput> from(String... values) {
			EnumSet<DebugOutput> result = EnumSet.noneOf(DebugOutput.class);
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

		private static void parseValue(EnumSet<DebugOutput> result, String value) {
			if (value == null || value.isBlank()) {
				return;
			}
			for (String part : value.split(",")) {
				String item = part.trim();
				if (item.isEmpty() || DebugMode.DEBUG.name().equalsIgnoreCase(item)
						|| DebugMode.ERROR.name().equalsIgnoreCase(item)) {
					continue;
				}
				try {
					result.add(DebugOutput.valueOf(item.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Unsupported JPostman debug output: " + item
							+ ". Supported values: none, error, request, response, info, all, debug.", e);
				}
			}
		}

		private static void validate(EnumSet<DebugOutput> result, String... rawValues) {
			if (result.size() > 1 && result.contains(NONE)) {
				throw new IllegalArgumentException(
						"JPostman debug output none must be used alone: " + Arrays.toString(rawValues));
			}
			if (result.size() > 1 && result.contains(ALL)) {
				throw new IllegalArgumentException(
						"JPostman debug output all must be used alone: " + Arrays.toString(rawValues));
			}
		}

		static boolean isOutput(String value) {
			if (value == null || value.isBlank()) {
				return false;
			}
			try {
				DebugOutput.valueOf(value.trim().toUpperCase(Locale.ROOT));
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
	}

	enum DebugMode {
		NONE, DEBUG, ERROR;

		static DebugMode from(String... values) {
			DebugMode result = null;
			if (values != null) {
				for (String value : values) {
					result = parseValue(result, value, values);
				}
			}
			return result == null ? NONE : result;
		}

		static void validateLocal(String value) {
			from(value);
			DebugOutput.from(value);
		}

		static boolean isMode(String value) {
			if (value == null || value.isBlank()) {
				return false;
			}
			try {
				DebugMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		private static DebugMode parseValue(DebugMode current, String value, String... rawValues) {
			if (value == null || value.isBlank()) {
				return current;
			}
			for (String part : value.split(",")) {
				String item = part.trim();
				if (item.isEmpty() || (DebugOutput.isOutput(item) && !DebugOutput.NONE.name().equalsIgnoreCase(item))) {
					continue;
				}
				if (current != null) {
					throw new IllegalArgumentException(
							"JPostman debug must use one mode only: " + Arrays.toString(rawValues));
				}
				try {
					current = DebugMode.valueOf(item.toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Unsupported JPostman debug value: " + item
							+ ". Supported values: none, error, request, response, info, all, debug.", e);
				}
			}
			return current;
		}

	}

	private static final class DebugModeMarker extends Exception {
		private static final long serialVersionUID = 1L;
		private final DebugMode mode;
		private final boolean diagnostics;

		private DebugModeMarker(DebugMode mode, boolean diagnostics) {
			this.mode = mode;
			this.diagnostics = diagnostics;
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}

	private final DebugMode contextMode;
	private final EnumSet<DebugOutput> contextOutput;
	private final int defaultStatusCode;
	private final Class<?> executorClass;
	private final boolean session;

	private JPostmanRuntimeOptions(DebugMode contextMode, EnumSet<DebugOutput> contextOutput, int defaultStatusCode,
			Class<?> executorClass, boolean session) {
		this.contextMode = contextMode == null ? DebugMode.NONE : contextMode;
		this.contextOutput = contextOutput == null || contextOutput.isEmpty() ? EnumSet.of(DebugOutput.NONE)
				: EnumSet.copyOf(contextOutput);
		this.defaultStatusCode = defaultStatusCode;
		this.executorClass = executorClass == Void.class ? null : executorClass;
		this.session = session;
	}

	static JPostmanRuntimeOptions from(Object testInstance) {
		JPostmanContext annotation = findContextAnnotation(testInstance);
		if (annotation == null) {
			return new JPostmanRuntimeOptions(DebugMode.NONE, EnumSet.of(DebugOutput.NONE), -1, null, false);
		}

		String[] debug = annotation.debug();
		int defaultStatusCode = annotation.verifyStatusCode();
		Class<?> executorClass = annotation.executor();
		boolean session = annotation.session();

		try {
			Properties properties = loadProperties(annotation.config(), testInstance.getClass());
			String namespace = "";
			debug = stringValues(property(properties, "debug", namespace), debug);
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

		return new JPostmanRuntimeOptions(DebugMode.from(debug), DebugOutput.from(debug), defaultStatusCode,
				executorClass, session);
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
		return contextMode != DebugMode.ERROR;
	}

	boolean errorStackTrace() {
		return contextMode == DebugMode.ERROR;
	}

	boolean minimumErrorOutput(String localDebug) {
		return debugMode(localDebug) != DebugMode.ERROR;
	}

	void markFailure(Throwable error, String localDebug) {
		if (error == null || findMarkedDebugMode(error) != null) {
			return;
		}
		error.addSuppressed(new DebugModeMarker(debugMode(localDebug), failureDiagnostics(localDebug, null)));
	}

	boolean minimumErrorOutput(Throwable error) {
		DebugMode marked = findMarkedDebugMode(error);
		return marked == null ? minimumErrorOutput() : marked != DebugMode.ERROR;
	}

	boolean failureDiagnostics(Throwable error) {
		if (hasDiagnosticSuppressed(error)) {
			return true;
		}
		DebugModeMarker marker = findDebugMarker(error);
		return marker == null ? failureDiagnostics() : marker.diagnostics;
	}

	private static DebugMode findMarkedDebugMode(Throwable error) {
		DebugModeMarker marker = findDebugMarker(error);
		return marker == null ? null : marker.mode;
	}

	private static DebugModeMarker findDebugMarker(Throwable error) {
		Throwable current = error;
		while (current != null) {
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed instanceof DebugModeMarker) {
					return (DebugModeMarker) suppressed;
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
				if (suppressed instanceof DebugModeMarker) {
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

	boolean failureRequest(String localDebug, JPostmanInfo info) {
		EnumSet<DebugOutput> outputs = effectiveOutput(localDebug, info);
		return outputs.contains(DebugOutput.REQUEST) || outputs.contains(DebugOutput.ALL);
	}

	boolean failureResponse() {
		return failureResponse(null, null);
	}

	boolean failureResponse(String localDebug, JPostmanInfo info) {
		EnumSet<DebugOutput> outputs = effectiveOutput(localDebug, info);
		return outputs.contains(DebugOutput.RESPONSE) || outputs.contains(DebugOutput.ALL);
	}

	boolean failureInfo(String localDebug, JPostmanInfo info) {
		EnumSet<DebugOutput> outputs = effectiveOutput(localDebug, info);
		return outputs.contains(DebugOutput.INFO) || outputs.contains(DebugOutput.ALL);
	}

	/** Avoids appending an info block already emitted by the same debug setting. */
	boolean failureInfoDiagnostic(String localDebug, JPostmanInfo info) {
		if (!failureInfo(localDebug, info)) {
			return false;
		}
		EnumSet<DebugOutput> regular = automaticOutput(localDebug, info);
		return !regular.contains(DebugOutput.INFO) && !regular.contains(DebugOutput.ALL);
	}

	boolean failureDiagnostics() {
		return failureDiagnostics(null, null);
	}

	boolean failureDiagnostics(String localDebug, JPostmanInfo info) {
		return !effectiveOutput(localDebug, info).contains(DebugOutput.NONE);
	}

	boolean runtimeTraceDebugInfoWarn() {
		return true;
	}

	boolean runtimeError() {
		return true;
	}

	private DebugMode debugMode(String localDebug) {
		if (inheritsContextDebug(localDebug)) {
			return contextMode;
		}
		return DebugMode.from(localDebug);
	}

	EnumSet<DebugOutput> automaticOutput(String localDebug, JPostmanInfo info) {
		return effectiveOutput(localDebug, info);
	}

	private EnumSet<DebugOutput> effectiveOutput(String localDebug, JPostmanInfo info) {
		if (inheritsContextDebug(localDebug)) {
			return contextDebugOutput(info);
		}
		EnumSet<DebugOutput> localOutput = DebugOutput.from(localDebug);
		return localOutput.contains(DebugOutput.NONE) ? EnumSet.of(DebugOutput.NONE) : localOutput;
	}

	EnumSet<DebugOutput> contextDebugOutput(JPostmanInfo info) {
		String local = info == null ? "" : info.debug;
		return local == null || local.isBlank() ? EnumSet.copyOf(contextOutput) : DebugOutput.from(local);
	}

	private static boolean inheritsContextDebug(String localDebug) {
		if (localDebug == null || localDebug.isBlank()) {
			return true;
		}
		return "debug".equalsIgnoreCase(localDebug.trim());
	}

	void debug(Object testInstance, JPostmanInfo info) {
		debug(testInstance, info, "debug");
	}

	void debug(Object testInstance, JPostmanInfo info, String localDebug) {
		if (testInstance == null || info == null) {
			return;
		}

		EnumSet<DebugOutput> outputs = automaticOutput(localDebug, info);
		if (outputs.contains(DebugOutput.INFO) || outputs.contains(DebugOutput.ALL)) {
			printMethodHeader(testInstance, info);
			info.print(false);
		}
	}

	static void printMethodHeader(Object testInstance, JPostmanInfo info) {
		if (testInstance == null || info == null) {
			return;
		}

		String text = "DEBUG " + testInstance.getClass().getName() + ":   === " + info.method + " ===";
		if (!JPostmanOutputs.write(text)) {
			LoggerFactory.getLogger(testInstance.getClass()).debug("  === {} ===", info.method);
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
