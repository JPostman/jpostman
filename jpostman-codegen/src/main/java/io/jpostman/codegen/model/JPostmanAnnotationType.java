package io.jpostman.codegen.model;

/**
 * Supported JPostman method-level annotations for generated test methods.
 */
public enum JPostmanAnnotationType {

	RUNNER("Runner", true, false), REQUEST("Request", false, true), RESPONSE("Response", false, true);

	private final String simpleName;
	private final boolean runner;
	private final boolean requestAware;

	JPostmanAnnotationType(String simpleName, boolean runner, boolean requestAware) {
		this.simpleName = simpleName;
		this.runner = runner;
		this.requestAware = requestAware;
	}

	public String annotationName() {
		return "JPostman." + simpleName;
	}

	public String commandName() {
		return simpleName.toLowerCase();
	}

	public boolean isRunner() {
		return runner;
	}

	public boolean isRequestAware() {
		return requestAware;
	}

	public static JPostmanAnnotationType fromCommand(String command) {
		if (command == null) {
			throw new IllegalArgumentException("Command is required. Use runner, request, or response.");
		}
		for (JPostmanAnnotationType type : values()) {
			if (type.commandName().equalsIgnoreCase(command.trim())) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported command: " + command + ". Use runner, request, or response.");
	}
}
