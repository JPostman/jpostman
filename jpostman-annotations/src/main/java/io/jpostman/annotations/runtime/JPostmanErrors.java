package io.jpostman.annotations.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import io.jpostman.annotations.JPostmanContext;

/**
 * Centralized JPostman annotation runtime message formatting.
 */
final class JPostmanErrors {

	public static final String ENDL = "\n";

	private JPostmanErrors() {
	}

	static AssertionError usage(JPostmanInfo info, String... lines) {
		return new AssertionError(message(info, lines));
	}

	static AssertionError execution(JPostmanInfo info, Throwable cause, String... lines) {
		AssertionError error = new AssertionError(message(info, lines));
		if (cause != null) {
			error.initCause(cause);
		}
		return error;
	}

	static RuntimeException skip(JPostmanFramework<?> framework, JPostmanInfo info, String... lines) {
		return framework.skipException(info, lines);
	}

	static String message(Class<?> clazz, String... lines) {
		return message(info(clazz), lines);
	}

	static String message(Annotation annotation, String... lines) {
		return message(info(annotation), lines);
	}

	static JPostmanInfo info(Class<?> clazz) {
		return new JPostmanInfo(clazz != null ? "@" + clazz.getSimpleName() : "@JPostman", "", "", "", "");
	}

	static JPostmanInfo info(Annotation annotation) {
		if (annotation == null) {
			return info((Class<?>) null);
		}

		Class<? extends Annotation> type = annotation.annotationType();
		String namespace = annotationValue(annotation, "namespace");
		String folder = annotationValue(annotation, "folder");
		String request = annotationValue(annotation, "request");
		String executor = annotation instanceof JPostmanContext ? "" : annotationValue(annotation, "executor");
		String[] tags = annotationArrayValue(annotation, "tags");

		JPostmanInfo info = new JPostmanInfo(tags, executor, "", namespace, folder, request)
				.annotation("@" + type.getSimpleName());
		if (annotation instanceof JPostmanContext) {
			info = info.context((JPostmanContext) annotation);
		}
		return info;
	}

	static String message(JPostmanInfo info, String... lines) {
		StringBuilder message = new StringBuilder();
		if (lines != null) {
			for (String line : lines) {
				String value = stripSuffix(line).trim();
				if (value.isBlank()) {
					continue;
				}
				if (message.length() > 0) {
					message.append(ENDL);
				}
				message.append(value);
			}
		}
		String suffix = suffix(info);
		if (!suffix.isBlank()) {
			if (message.length() > 0) {
				message.append(ENDL);
			}
			message.append(suffix);
		}
		message.append(ENDL);
		return message.toString();
	}

	static String stripSuffix(String message) {
		if (message == null || message.isBlank()) {
			return "";
		}
		String result = message.stripTrailing();
		while (true) {
			int index = lastSuffixIndex(result);
			if (index < 0) {
				return result;
			}
			result = result.substring(0, index).stripTrailing();
		}
	}

	private static int lastSuffixIndex(String message) {
		int index = message.lastIndexOf(ENDL + "(@JPostman");
		if (index >= 0 && message.substring(index + ENDL.length()).trim().endsWith(")")) {
			return index;
		}
		if (message.startsWith("(@JPostman") && message.trim().endsWith(")")) {
			return 0;
		}
		return -1;
	}

	static String suffix(JPostmanInfo info) {
		if (info == null) {
			return "";
		}
		StringBuilder message = new StringBuilder();
		message.append("(");
		message.append(blank(info.annotation) ? "JPostman" : info.annotation);
		if (info.context != null && "@JPostmanContext".equals(info.annotation)) {
			message.append(": config=").append(defaultValue(info.context.config()));
			message.append(", namespace=").append(defaultValue(info.context.namespace()));
			message.append(", collection=").append(defaultValue(info.context.collection()));
			message.append(", environment=").append(defaultValue(info.context.environment()));
		} else {
			message.append(": tags=").append(String.join(", ", info.tags));
			message.append(", namespace=").append(defaultValue(info.namespace));
			message.append(", folder=").append(defaultValue(info.folder));
			message.append(", request=").append(defaultValue(info.request));
			message.append(", executor=").append(defaultValue(info.executor));
		}
		message.append(")");
		return message.toString();
	}

	static String signature(Method method) {
		StringBuilder result = new StringBuilder();
		result.append(method.getDeclaringClass().getSimpleName()).append(".").append(method.getName()).append("(");
		Class<?>[] types = method.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			result.append(types[i].getSimpleName());
		}
		result.append(")");
		return result.toString();
	}

	private static String annotationValue(Annotation annotation, String elementName) {
		Object value = annotationElementValue(annotation, elementName);
		return value == null ? "" : String.valueOf(value);
	}

	private static String[] annotationArrayValue(Annotation annotation, String elementName) {
		Object value = annotationElementValue(annotation, elementName);
		if (value == null || !value.getClass().isArray()) {
			return new String[0];
		}
		int length = Array.getLength(value);
		String[] result = new String[length];
		for (int i = 0; i < length; i++) {
			Object item = Array.get(value, i);
			result[i] = item == null ? "" : String.valueOf(item);
		}
		return result;
	}

	private static Object annotationElementValue(Annotation annotation, String elementName) {
		if (annotation == null || elementName == null || elementName.isBlank()) {
			return null;
		}
		try {
			Method method = annotation.annotationType().getDeclaredMethod(elementName);
			return method.invoke(annotation);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to read annotation element: " + elementName, e);
		}
	}

	private static String defaultValue(String value) {
		return blank(value) ? "<default>" : value;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
