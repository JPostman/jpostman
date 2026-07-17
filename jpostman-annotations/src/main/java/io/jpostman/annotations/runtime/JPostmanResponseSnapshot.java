package io.jpostman.annotations.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.jpostman.annotations.JPostman;

/**
 * Creates a detached, read-only {@link JPostman.Test} view of a completed
 * response. The snapshot captures the response root while the dependency method
 * is still active, so later requests cannot replace the cached response state.
 */
final class JPostmanResponseSnapshot {

	private JPostmanResponseSnapshot() {
	}

	static JPostman.Test create(Object context) {
		if (context == null) {
			return null;
		}
		Object target = JPostmanTestProxy.unwrap(context);
		Object root = responseRoot(target);
		Object frozen = freeze(root);
		return (JPostman.Test) Proxy.newProxyInstance(JPostman.Test.class.getClassLoader(),
				new Class<?>[] { JPostman.Test.class }, (proxy, method, args) -> invoke(proxy, method, args, frozen));
	}

	private static Object invoke(Object proxy, Method method, Object[] args, Object root) throws Throwable {
		String name = method.getName();
		if ("toString".equals(name) && method.getParameterCount() == 0) {
			return String.valueOf(root);
		}
		if ("hashCode".equals(name) && method.getParameterCount() == 0) {
			return root == null ? 0 : root.hashCode();
		}
		if ("equals".equals(name) && method.getParameterCount() == 1) {
			return proxy == (args == null ? null : args[0]);
		}
		if ("ctx".equals(name) && method.getParameterCount() == 0) {
			return proxy;
		}
		if ("path".equals(name) && method.getParameterCount() == 1) {
			return path(root, args == null ? null : String.valueOf(args[0]));
		}
		if ("cache".equals(name)) {
			throw new UnsupportedOperationException("A cached response snapshot is read-only.");
		}
		throw new UnsupportedOperationException(
				"JPostman cached response snapshot does not support method: " + method.getName());
	}

	private static Object responseRoot(Object target) {
		for (String path : new String[] { "", "$" }) {
			Object value = invokePath(target, path);
			if (value != null) {
				return value;
			}
		}
		for (String methodName : new String[] { "body", "responseBody", "json", "response" }) {
			Object value = invokeNoArg(target, methodName);
			if (value != null && value != target) {
				Object nested = invokePath(value, "");
				return nested == null ? value : nested;
			}
		}
		throw new IllegalStateException(
				"Unable to snapshot JPostman response. The context must expose path(\"\"), path(\"$\"), body(), json(), or response().");
	}

	private static Object invokePath(Object target, String path) {
		if (target == null) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod("path", String.class);
			return method.invoke(target, path);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (InvocationTargetException e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object invokeNoArg(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			if (method.getReturnType() == Void.TYPE) {
				return null;
			}
			return method.invoke(target);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object freeze(Object value) {
		Object unwrapped = JPostmanCacheValueConverter.unwrap(value);
		if (unwrapped != value) {
			return freeze(unwrapped);
		}
		if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
				|| value instanceof Character || value.getClass().isEnum()) {
			return value;
		}
		if (value instanceof Map<?, ?>) {
			Map<String, Object> copy = new LinkedHashMap<>();
			((Map<?, ?>) value).forEach((key, item) -> copy.put(String.valueOf(key), freeze(item)));
			return Collections.unmodifiableMap(copy);
		}
		if (value instanceof Iterable<?>) {
			List<Object> copy = new ArrayList<>();
			for (Object item : (Iterable<?>) value) {
				copy.add(freeze(item));
			}
			return Collections.unmodifiableList(copy);
		}
		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			List<Object> copy = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				copy.add(freeze(Array.get(value, i)));
			}
			return Collections.unmodifiableList(copy);
		}
		Object converted = invokeNoArg(value, "asMap");
		if (converted instanceof Map<?, ?>) {
			return freeze(converted);
		}
		converted = invokeNoArg(value, "toMap");
		if (converted instanceof Map<?, ?>) {
			return freeze(converted);
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	private static <T> T path(Object root, String expression) {
		if (expression == null || expression.isBlank() || "$".equals(expression)) {
			return (T) root;
		}
		String normalized = expression.startsWith("$/") ? expression.substring(2)
				: expression.startsWith("$.") ? expression.substring(2) : expression;
		String[] parts = normalized.replace('[', '.').replace("]", "").split("[/.]");
		Object current = root;
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (current instanceof Map<?, ?>) {
				current = ((Map<?, ?>) current).get(part);
			} else if (current instanceof List<?>) {
				try {
					current = ((List<?>) current).get(Integer.parseInt(part));
				} catch (NumberFormatException | IndexOutOfBoundsException e) {
					return null;
				}
			} else {
				return null;
			}
			if (current == null) {
				return null;
			}
		}
		return (T) JPostmanCacheValueConverter.unwrap(current);
	}
}
