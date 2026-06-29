package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.jpostman.Collection;
import io.jpostman.Request;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;

/**
 * Discovers request names for {@code @JPostmanRunner} execution.
 */
final class JPostmanRequestDiscovery {

	List<String> runnerRequestNames(Collection collection, String folder) {
		Object container = collection;
		if (folder != null && !folder.isBlank()) {
			try {
				container = collection.getFolder(folder);
			} catch (AssertionError | RuntimeException e) {
				return new ArrayList<>();
			}
		}

		List<String> names = new ArrayList<>();
		collectRequestNames(container, names);
		return names;
	}

	boolean hasExplicitResponse(Class<?> type, String namespace, String folder, String requestName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanResponse response = JPostmanAnnotations.response(method);
				if (response == null) {
					continue;
				}

				if (same(response.namespace(), namespace) && same(response.folder(), folder)
						&& same(response.request(), requestName)) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
	}

	boolean hasSkippedRequest(Class<?> type, String namespace, String folder, String requestName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanRequest request = JPostmanAnnotations.request(method);
				if (request == null || !skipRequest(request)) {
					continue;
				}
				if (same(request.namespace(), namespace) && same(request.folder(), folder)
						&& same(request.request(), requestName)) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
	}

	private boolean skipRequest(JPostmanRequest request) {
		return request != null
				&& (request.skip() || request.skipReason() != null && !request.skipReason().trim().isBlank());
	}

	Set<String> normalizeNames(String[] values) {
		Set<String> result = new LinkedHashSet<>();
		if (values == null) {
			return result;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				result.add(value.trim());
			}
		}
		return result;
	}

	private boolean same(String first, String second) {
		String a = first == null ? "" : first;
		String b = second == null ? "" : second;
		return a.equals(b);
	}

	private void collectRequestNames(Object container, List<String> names) {
		if (container == null) {
			return;
		}

		for (String methodName : Arrays.asList("getRequestNames", "requestNames", "getRequests", "requests")) {
			Object value = invokeNoArgIfExists(container, methodName);
			if (appendRequestNames(value, names)) {
				return;
			}
		}

		Class<?> current = container.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				field.setAccessible(true);
				try {
					appendRequestNames(field.get(container), names);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Unable to inspect Postman collection field: " + field.getName(),
							e);
				}
			}
			current = current.getSuperclass();
		}
	}

	private Object invokeNoArgIfExists(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			method.setAccessible(true);
			return method.invoke(target);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException("Unable to inspect Postman collection method: " + methodName, cause);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to inspect Postman collection method: " + methodName, e);
		}
	}

	private boolean appendRequestNames(Object value, List<String> names) {
		if (value == null) {
			return false;
		}

		int before = names.size();

		if (value instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) value;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() instanceof String && isRequestLike(entry.getValue())) {
					addName(names, (String) entry.getKey());
				} else {
					appendRequestNames(entry.getValue(), names);
				}
			}
			return names.size() > before;
		}

		if (value instanceof Iterable<?>) {
			for (Object item : (Iterable<?>) value) {
				appendRequestNames(item, names);
			}
			return names.size() > before;
		}

		if (value.getClass().isArray()) {
			int length = java.lang.reflect.Array.getLength(value);
			for (int i = 0; i < length; i++) {
				appendRequestNames(java.lang.reflect.Array.get(value, i), names);
			}
			return names.size() > before;
		}

		if (isRequestLike(value)) {
			String name = requestName(value);
			if (name != null) {
				addName(names, name);
			}
		}

		return names.size() > before;
	}

	private boolean isRequestLike(Object value) {
		if (value instanceof Request) {
			return true;
		}

		if (value == null) {
			return false;
		}

		String simpleName = value.getClass().getSimpleName();
		String className = value.getClass().getName();
		return "Request".equals(simpleName) || className.endsWith(".Request");
	}

	private String requestName(Object value) {
		if (value instanceof String) {
			return (String) value;
		}

		if (value == null) {
			return null;
		}

		for (String methodName : Arrays.asList("getName", "name")) {
			Object result = invokeNoArgIfExists(value, methodName);
			if (result instanceof String && !((String) result).isBlank()) {
				return ((String) result).trim();
			}
		}

		Class<?> current = value.getClass();
		while (current != null && current != Object.class) {
			try {
				Field field = current.getDeclaredField("name");
				field.setAccessible(true);
				Object result = field.get(value);
				if (result instanceof String && !((String) result).isBlank()) {
					return ((String) result).trim();
				}
			} catch (NoSuchFieldException e) {
				// Try superclass.
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Unable to inspect Postman request name.", e);
			}
			current = current.getSuperclass();
		}

		return null;
	}

	private void addName(List<String> names, String name) {
		if (name != null && !name.isBlank() && !names.contains(name.trim())) {
			names.add(name.trim());
		}
	}
}
