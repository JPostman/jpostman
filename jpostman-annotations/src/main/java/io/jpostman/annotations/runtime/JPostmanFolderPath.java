package io.jpostman.annotations.runtime;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.jpostman.Collection;
import io.jpostman.Request;

/** Resolves root and nested Postman collection folders. */
final class JPostmanFolderPath {

	private JPostmanFolderPath() {
	}

	static boolean isEmpty(String[] levels) {
		return normalize(levels).length == 0;
	}

	static String value(String[] levels) {
		return String.join("/", normalize(levels));
	}

	static String[] levels(String value) {
		if (value == null || value.isBlank()) {
			return new String[0];
		}
		return normalize(value.split("/"));
	}

	static List<Request> requests(Collection collection, String folder) {
		return requests(collection, levels(folder));
	}

	static List<Request> requests(Collection collection, String[] levels) {
		String[] path = normalize(levels);
		if (path.length == 0) {
			return collection.getRequests();
		}

		JsonArray items = folderItems(collection, path);
		List<Request> result = new ArrayList<>();
		String folderName = value(path);
		for (JsonElement element : items) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject item = element.getAsJsonObject();
			if (!item.has("request") || !item.get("request").isJsonObject()) {
				continue;
			}
			String name = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString()
					: "Unnamed";
			result.add(Request.from(name, folderName, item.getAsJsonObject("request")));
		}
		return result;
	}

	static Request request(Collection collection, String folder, String requestName) {
		for (Request request : requests(collection, folder)) {
			if (request.getName().equalsIgnoreCase(requestName)) {
				return request;
			}
		}
		throw new IllegalArgumentException("Request not found: " + requestName);
	}

	private static JsonArray folderItems(Collection collection, String[] path) {
		JsonObject root = collection.getRoot();
		JsonArray current = root != null && root.has("item") && root.get("item").isJsonArray()
				? root.getAsJsonArray("item")
				: new JsonArray();
		StringBuilder resolved = new StringBuilder();
		for (String level : path) {
			JsonObject folder = null;
			for (JsonElement element : current) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject item = element.getAsJsonObject();
				String name = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : "";
				if (name.equalsIgnoreCase(level) && item.has("item") && item.get("item").isJsonArray()) {
					folder = item;
					break;
				}
			}
			if (folder == null) {
				if (resolved.length() > 0) {
					resolved.append('/');
				}
				resolved.append(level);
				throw new IllegalArgumentException("Folder not found: " + resolved);
			}
			if (resolved.length() > 0) {
				resolved.append('/');
			}
			resolved.append(level);
			current = folder.getAsJsonArray("item");
		}
		return current;
	}

	private static String[] normalize(String[] levels) {
		if (levels == null || levels.length == 0) {
			return new String[0];
		}
		List<String> result = new ArrayList<>();
		for (int i = 0; i < levels.length; i++) {
			String value = levels[i] == null ? "" : levels[i].trim();
			if (value.isBlank()) {
				if (levels.length == 1) {
					return new String[0];
				}
				throw new IllegalArgumentException("Folder path contains an empty level at index " + i + ".");
			}
			result.add(value);
		}
		return result.toArray(new String[0]);
	}
}
