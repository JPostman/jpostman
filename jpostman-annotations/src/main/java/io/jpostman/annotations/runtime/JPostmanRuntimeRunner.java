package io.jpostman.annotations.runtime;

import java.util.ArrayList;
import java.util.List;

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

	private static final class State {
		private final List<String> requestNames;
		private int index = -1;
		private String requestName = "";

		private State(List<String> requestNames) {
			this.requestNames = requestNames == null ? List.of() : new ArrayList<>(requestNames);
		}
	}
}
