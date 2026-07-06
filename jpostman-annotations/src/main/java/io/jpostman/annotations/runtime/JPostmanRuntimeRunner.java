package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opentest4j.MultipleFailuresError;

/**
 * Thread-local state for the active runtime @JPostmanRunner request callback.
 */
final class JPostmanRuntimeRunner {

	private static final ThreadLocal<State> STATE = new ThreadLocal<>();

	private JPostmanRuntimeRunner() {
	}

	static void begin(List<String> requestNames) {
		STATE.set(new State(requestNames));
	}

	static void request(int index, String requestName) {
		State state = STATE.get();
		if (state != null) {
			state.index = index;
			state.requestName = requestName == null ? "" : requestName;
		}
	}

	static void clear() {
		STATE.remove();
	}

	static boolean active() {
		return STATE.get() != null;
	}

	static boolean hasSoftFailure() {
		State state = STATE.get();
		return state != null && !state.softFailures.isEmpty();
	}

	static boolean hasCollectedSoftFailure() {
		State state = STATE.get();
		return state != null && (state.collectedSoftFailure || !state.softFailures.isEmpty());
	}

	static boolean isLast(JPostmanInfo info) {
		State state = STATE.get();
		if (state == null || state.requestNames.isEmpty()) {
			return true;
		}

		if (state.index >= 0) {
			return state.index == state.requestNames.size() - 1;
		}

		String request = info == null || info.request == null ? state.requestName : info.request;
		return !request.isBlank() && request.equals(state.requestNames.get(state.requestNames.size() - 1));
	}

	static void recordSoftFailure(AssertionError failure) {
		State state = STATE.get();
		if (state == null || failure == null) {
			return;
		}
		state.collectedSoftFailure = true;
		state.softFailures.put(state.softFailureKey(), failure);
	}

	static AssertionError softFailure(AssertionError current) {
		State state = STATE.get();
		if (state == null) {
			return current;
		}
		if (state.softFailures.isEmpty()) {
			if (current != null) {
				state.collectedSoftFailure = true;
			}
			return current == null ? null : assertionFailure(List.of(current));
		}

		LinkedHashMap<Integer, AssertionError> failures = new LinkedHashMap<>(state.softFailures);
		if (current != null && !failures.containsKey(state.softFailureKey())) {
			failures.put(state.softFailureKey(), current);
		}

		return assertionFailure(new ArrayList<>(failures.values()));
	}

	static AssertionError takeSoftFailure() {
		State state = STATE.get();
		if (state == null) {
			return null;
		}

		AssertionError failure = state.softFailures.remove(state.softFailureKey());
		if (failure != null) {
			state.collectedSoftFailure = true;
		}
		return failure == null ? null : assertionFailure(List.of(failure));
	}

	static AssertionError combineSoftFailures(AssertionError... failures) {
		if (failures == null || failures.length == 0) {
			return null;
		}

		List<AssertionError> values = new ArrayList<>();
		for (AssertionError failure : failures) {
			if (failure != null) {
				values.add(failure);
			}
		}
		return values.isEmpty() ? null : assertionFailure(values);
	}

	static boolean isSoftFailure(Throwable failure) {
		Throwable current = failure;
		for (int depth = 0; current != null && depth < 20; depth++) {
			if (current instanceof RunnerSoftAssertionError) {
				return true;
			}
			Throwable next = current instanceof InvocationTargetException
					? ((InvocationTargetException) current).getCause()
					: current.getCause();
			if (next == current) {
				break;
			}
			current = next;
		}
		return false;
	}

	static String failureMessage(Throwable failure) {
		return joinFailureMessages(failureMessages(failure));
	}

	private static String joinFailureMessages(List<String> messages) {
		return String.join(System.lineSeparator(), messages);
	}

	private static AssertionError assertionFailure(List<AssertionError> failures) {
		List<String> messages = new ArrayList<>();
		for (AssertionError failure : failures) {
			messages.addAll(failureMessages(failure));
		}

		AssertionError result = new RunnerSoftAssertionError(joinFailureMessages(messages));
		for (AssertionError failure : failures) {
			if (failure != null && failure.getStackTrace() != null && failure.getStackTrace().length > 0) {
				result.setStackTrace(failure.getStackTrace());
				break;
			}
		}
		return result;
	}

	private static List<String> failureMessages(Throwable failure) {
		List<String> messages = new ArrayList<>();
		appendFailureMessages(messages, failure);
		return messages;
	}

	private static void appendFailureMessages(List<String> messages, Throwable failure) {
		if (failure == null) {
			return;
		}
		if (failure instanceof MultipleFailuresError) {
			for (Throwable nested : ((MultipleFailuresError) failure).getFailures()) {
				appendFailureMessages(messages, nested);
			}
			return;
		}

		String rawMessage = failure.getMessage();
		String message = rawMessage == null ? "" : rawMessage.trim();
		if (!message.isBlank()) {
			messages.addAll(flattenFailureMessage(message));
		}
	}

	private static List<String> flattenFailureMessage(String message) {
		List<String> result = new ArrayList<>();
		boolean wrapped = false;
		for (String line : message.split("\\R")) {
			String value = line == null ? "" : line.trim();
			if (value.isBlank()) {
				continue;
			}
			if (value.startsWith("Multiple Failures (")) {
				wrapped = true;
				continue;
			}
			if ("The following asserts failed:".equals(value)) {
				wrapped = true;
				continue;
			}
			if (value.startsWith("org.opentest4j.AssertionFailedError:")) {
				wrapped = true;
				result.add(value.substring("org.opentest4j.AssertionFailedError:".length()).trim());
				continue;
			}
			if (value.startsWith("java.lang.AssertionError:")) {
				wrapped = true;
				result.add(value.substring("java.lang.AssertionError:".length()).trim());
				continue;
			}
			result.add(value);
		}
		if (wrapped) {
			return result;
		}
		return List.of(message);
	}

	private static final class RunnerSoftAssertionError extends AssertionError {
		private static final long serialVersionUID = 1L;

		private RunnerSoftAssertionError(String message) {
			super(message);
		}
	}

	private static final class State {
		private final List<String> requestNames;
		private final Map<Integer, AssertionError> softFailures = new LinkedHashMap<>();
		private boolean collectedSoftFailure;
		private int index = -1;
		private String requestName = "";

		private State(List<String> requestNames) {
			this.requestNames = requestNames == null ? List.of() : new ArrayList<>(requestNames);
		}

		private int softFailureKey() {
			if (index >= 0) {
				return index;
			}
			return -1 - softFailures.size();
		}
	}
}
