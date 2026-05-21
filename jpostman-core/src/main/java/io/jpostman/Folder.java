package io.jpostman;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Postman collection folder and its direct request children.
 */
public class Folder {

	private static final Logger log = LoggerFactory.getLogger(Folder.class);

	private final String name;
	private final List<Request> requests = new ArrayList<>();

	/**
	 * Creates a folder container.
	 *
	 * @param name folder name from the collection
	 */
	public Folder(String name) {
		this.name = name;
	}

	/** @return folder name. */
	public String getName() {
		return name;
	}

	/** @return requests directly contained in this folder. */
	public List<Request> getRequests() {
		return requests;
	}

	/** Adds a request to this folder during collection parsing. */
	public void addRequest(Request request) {
		requests.add(request);
	}

	/**
	 * Looks up a request by name, ignoring case.
	 *
	 * @param requestName request name
	 * @return matching request
	 * @throws IllegalArgumentException if request is not found
	 */
	public Request getRequest(String requestName) {
		for (Request request : requests) {
			if (request.getName().equalsIgnoreCase(requestName)) {
				return request;
			}
		}

		throw new IllegalArgumentException("Request not found: " + requestName);
	}

	/** Logs this folder and its direct requests at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("=== Folder: %s (%d request%s) ===", name, requests.size(),
				requests.size() == 1 ? "" : "s"));
		if (requests.isEmpty()) {
			sb.append("\n  (no requests)");
		} else {
			sb.append('\n' + toString());
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return requests.stream().map(e -> String.format("    %s\n", e)).collect(Collectors.joining());
	}
}
