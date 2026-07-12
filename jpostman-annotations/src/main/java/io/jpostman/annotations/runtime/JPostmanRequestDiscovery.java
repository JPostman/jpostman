package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.jpostman.Collection;
import io.jpostman.Request;
import io.jpostman.annotations.JPostmanCall;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;

/**
 * Discovers request names for {@code @JPostmanRunner} execution.
 */
final class JPostmanRequestDiscovery {

	List<String> runnerRequestNames(Collection collection, String folder) {
		List<String> names = new ArrayList<>();
		try {
			for (Request request : JPostmanFolderPath.requests(collection, folder)) {
				addName(names, request.getName());
			}
		} catch (AssertionError | RuntimeException e) {
			return new ArrayList<>();
		}
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

				if (sameScope(response.namespace(), namespace) && sameScope(response.folder(), folder)
						&& same(response.request(), requestName)) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
	}

	boolean hasExplicitRequest(Class<?> type, String namespace, String folder, String requestName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanRequest request = JPostmanAnnotations.request(method);
				if (request == null) {
					continue;
				}
				if (sameScope(request.namespace(), namespace) && sameScope(request.folder(), folder)
						&& same(request.request(), requestName)) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
	}

	boolean hasExplicitCall(Class<?> type, String namespace, String folder, String requestName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanCall call = JPostmanAnnotations.call(method);
				if (call == null) {
					continue;
				}
				if (sameScope(call.namespace(), namespace) && sameScope(call.folder(), folder)
						&& same(call.request(), requestName)) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
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

	private boolean sameScope(String explicit, String active) {
		String configured = explicit == null ? "" : explicit.trim();
		if (configured.isBlank()) {
			return true;
		}
		String current = active == null ? "" : active.trim();
		return configured.equals(current);
	}

	private boolean sameScope(String[] explicit, String active) {
		String configured = JPostmanFolderPath.value(explicit);
		if (configured.isBlank()) {
			return true;
		}
		String current = active == null ? "" : active.trim();
		return configured.equals(current);
	}

	private boolean same(String first, String second) {
		String a = first == null ? "" : first.trim();
		String b = second == null ? "" : second.trim();
		return a.equals(b);
	}

	private void addName(List<String> names, String name) {
		if (name != null && !name.isBlank() && !names.contains(name.trim())) {
			names.add(name.trim());
		}
	}
}
