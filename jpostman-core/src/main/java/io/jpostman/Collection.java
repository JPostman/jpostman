package io.jpostman;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Loads a Postman Collection v2.1 JSON export and provides helpers to inspect
 * folders, requests, and an optionally loaded environment.
 */
public class Collection {

	private static final Logger log = LoggerFactory.getLogger(Collection.class);

	private final JsonObject root;
	private String name;
	private final List<Folder> folders = new ArrayList<>();
	private final List<Request> rootRequests = new ArrayList<>();
	private Environment environment;

	private Collection(JsonObject root) {
		this.root = root;
	}

	// -------------------------------------------------------------------------
	// Loading
	// -------------------------------------------------------------------------

	/**
	 * Load a collection from a file path. Opens and closes the file internally.
	 *
	 * @param filePath absolute or relative path to the
	 *                 {@code *.postman_collection.json} file
	 * @return populated collection
	 * @throws IOException if the file cannot be read or parsed
	 */
	public static Collection load(String filePath) throws IOException {
		return load(new FileInputStream(filePath));
	}

	/**
	 * Load a collection from an input stream. The stream is closed by this method.
	 *
	 * @param is input stream positioned at the start of the collection JSON
	 * @return populated collection
	 * @throws IOException if the stream cannot be read or parsed
	 */
	public static Collection load(InputStream is) throws IOException {
		try (Reader reader = new InputStreamReader(is)) {
			return load(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	/**
	 * Load a collection from an already parsed JSON object.
	 *
	 * @param root collection root JSON object
	 * @return populated collection
	 * @throws IOException kept for API symmetry with file and stream loaders
	 */
	public static Collection load(JsonObject root) throws IOException {
		Collection col = new Collection(root);
		col.name = "Unnamed Collection";
		if (root.has("info") && root.get("info").isJsonObject()) {
			JsonObject info = root.getAsJsonObject("info");
			col.name = info.has("name") && !info.get("name").isJsonNull() ? info.get("name").getAsString()
					: "Unnamed Collection";
		}

		if (root.has("item") && root.get("item").isJsonArray()) {
			parseItems(root.getAsJsonArray("item"), col, null);
		}
		return col;
	}

	/**
	 * Load and attach a Postman environment file to this collection.
	 *
	 * @param filePath path to the *.postman_environment.json file
	 */
	public void loadEnvironment(String filePath) throws IOException {
		this.environment = Environment.load(filePath);
	}

	/**
	 * Load and attach a Postman environment from an input stream. The stream is
	 * closed by {@link Environment#load(InputStream)}.
	 *
	 * @param is environment JSON input stream
	 */
	public void loadEnvironment(InputStream is) throws IOException {
		this.environment = Environment.load(is);
	}

	// -------------------------------------------------------------------------
	// Parsing helpers
	// -------------------------------------------------------------------------

	/**
	 * Recursively parses Postman item arrays into folders and request objects.
	 */
	private static void parseItems(JsonArray items, Collection col, Folder parentFolder) {
		for (JsonElement element : items) {
			JsonObject item = element.getAsJsonObject();
			String itemName = item.has("name") ? item.get("name").getAsString() : "Unnamed";

			if (item.has("item")) {
				// Item array present → this is a folder (may be nested)
				Folder folder = new Folder(itemName);
				col.folders.add(folder);
				parseItems(item.getAsJsonArray("item"), col, folder);
			} else if (item.has("request")) {
				// Leaf item → this is a request
				JsonObject reqObj = item.getAsJsonObject("request");
				String folder = parentFolder != null ? parentFolder.getName() : "(root)";
				Request request = Request.from(itemName, folder, reqObj);

				if (parentFolder != null) {
					parentFolder.addRequest(request);
				} else {
					col.rootRequests.add(request);
				}
			}
		}
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	/** @return original collection root JSON. */
	public JsonObject getRoot() {
		return root;
	}

	/** @return collection name. */
	public String getName() {
		return name;
	}

	/** @return parsed folders. */
	public List<Folder> getFolders() {
		return folders;
	}

	/**
	 * Looks up a folder by name, ignoring case.
	 *
	 * @param folderName folder name
	 * @return matching folder
	 * @throws IllegalArgumentException if folder is not found
	 */
	public Folder getFolder(String folderName) {
		for (Folder folder : folders) {
			if (folder.getName().equalsIgnoreCase(folderName)) {
				return folder;
			}
		}

		throw new IllegalArgumentException("Folder not found: " + folderName);
	}

	/**
	 * @return environment attached with {@link #loadEnvironment}, or {@code null}.
	 */
	public Environment getEnvironment() {
		return environment;
	}

	/** @return root-level requests, excluding requests inside folders. */
	public List<Request> getRequests() {
		return rootRequests;
	}

	/**
	 * Looks up a root-level request by name, ignoring case.
	 *
	 * @param requestName request name
	 * @return matching request
	 * @throws IllegalArgumentException if request is not found
	 */
	public Request getRequest(String requestName) {
		for (Request request : rootRequests) {
			if (request.getName().equalsIgnoreCase(requestName)) {
				return request;
			}
		}

		throw new IllegalArgumentException("Request not found: " + requestName);
	}

	// -------------------------------------------------------------------------
	// Print functions
	// -------------------------------------------------------------------------

	/** Logs collection details at TRACE level. */
	public void print() {
		log.trace(toDebugString());
	}

	/** Returns verbose diagnostic representation including details. */
	public String toDebugString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("=== Collection: %s (%d request%s) ===", name, rootRequests.size(),
				rootRequests.size() == 1 ? "" : "s"));
		if (!rootRequests.isEmpty()) {
			sb.append(rootRequests.stream().map(e -> String.format("\n    %s", e)).collect(Collectors.joining()));
		}
		if (folders.isEmpty()) {
			sb.append("\n  (no folders)");
		} else {
			sb.append(String.format("\n  (%d folder%s)", folders.size(), folders.size() == 1 ? "" : "s"));
			sb.append('\n' + toString());
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return folders.stream().map(e -> String.format("  %s\n", e.getName())).collect(Collectors.joining());

	}
}
