package io.jpostman.github;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.utils.Key;

/**
 * Service for GitHub Actions repository variables and secret metadata.
 *
 * <p>
 * GitHub Actions variables can be created, updated, and read back through the
 * GitHub REST API. GitHub Actions secrets can be created or updated only with an
 * encrypted value. Secret plaintext values cannot be read back after they are
 * stored.
 * </p>
 */
public class GitHubService {

	private static final String DEFAULT_API_URL = "https://api.github.com";

	private final HttpClient httpClient;
	private final String apiUrl;
	private final String token;

	/**
	 * Creates a service using the default GitHub REST API URL.
	 *
	 * @param token GitHub token with the required repository permissions
	 */
	public GitHubService(String token) {
		this(DEFAULT_API_URL, token);
	}

	/**
	 * Creates a service using a custom GitHub REST API URL.
	 *
	 * @param apiUrl GitHub REST API base URL
	 * @param token  GitHub token with the required repository permissions
	 */
	public GitHubService(String apiUrl, String token) {
		this.httpClient = HttpClient.newHttpClient();
		this.apiUrl = trimTrailingSlash(Objects.requireNonNull(apiUrl, "apiUrl"));
		this.token = Objects.requireNonNull(token, "token");
	}

	/**
	 * Creates or updates one repository Actions variable.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name
	 * @param value variable value
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public void createOrUpdateRepositoryVariable(String owner, String repo, String name, String value)
			throws IOException, InterruptedException {
		if (repositoryVariableExists(owner, repo, name)) {
			updateRepositoryVariable(owner, repo, name, value);
		} else {
			createRepositoryVariable(owner, repo, name, value);
		}
	}

	/**
	 * Creates one repository Actions variable.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name
	 * @param value variable value
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public void createRepositoryVariable(String owner, String repo, String name, String value)
			throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/variables";
		String body = "{\"name\":\"" + escapeJson(name) + "\",\"value\":\"" + escapeJson(value) + "\"}";

		send("POST", path, body, 201);
	}

	/**
	 * Updates one repository Actions variable.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name
	 * @param value new variable value
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public void updateRepositoryVariable(String owner, String repo, String name, String value)
			throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/variables/" + encode(name);
		String body = "{\"name\":\"" + escapeJson(name) + "\",\"value\":\"" + escapeJson(value) + "\"}";

		send("PATCH", path, body, 204);
	}

	/**
	 * Checks whether one repository Actions variable exists.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name
	 * @return true if the variable exists
	 * @throws IOException          if the API request fails for a reason other than
	 *                              not found
	 * @throws InterruptedException if the request is interrupted
	 */
	public boolean repositoryVariableExists(String owner, String repo, String name)
			throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/variables/" + encode(name);
		HttpResponse<String> response = sendRaw("GET", path, null);

		if (response.statusCode() == 200) {
			return true;
		}
		if (response.statusCode() == 404) {
			return false;
		}
		throw error(response);
	}

	/**
	 * Reads one repository Actions variable value.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name
	 * @return variable value
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public String readRepositoryVariable(String owner, String repo, String name) throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/variables/" + encode(name);
		String responseBody = send("GET", path, null, 200);

		return extractJsonString(responseBody, "value");
	}

	/**
	 * Creates or updates a repository Actions secret using a plaintext value.
	 *
	 * <p>
	 * GitHub requires secret values to be encrypted with the repository public key.
	 * This method reads the public key, encrypts the value, and sends the encrypted
	 * value to GitHub.
	 * </p>
	 *
	 * @param owner      repository owner or organization
	 * @param repo       repository name
	 * @param secretName secret name
	 * @param secretValue plaintext secret value
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 * @throws SodiumException 
	 */
	public void createOrUpdateRepositorySecret(String owner, String repo, String secretName, String secretValue)
			throws IOException, InterruptedException, SodiumException {
		GitHubPublicKey publicKey = readRepositorySecretPublicKey(owner, repo);
		String encryptedValue = encryptSecret(secretValue, publicKey.key());

		createOrUpdateRepositorySecret(owner, repo, secretName, encryptedValue, publicKey.keyId());
	}

	/**
	 * Creates or updates a repository Actions secret using an already-encrypted
	 * value.
	 *
	 * @param owner          repository owner or organization
	 * @param repo           repository name
	 * @param secretName     secret name
	 * @param encryptedValue encrypted secret value
	 * @param keyId          GitHub repository public key id
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public void createOrUpdateRepositorySecret(String owner, String repo, String secretName, String encryptedValue,
			String keyId) throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/secrets/" + encode(secretName);
		String body = "{\"encrypted_value\":\"" + escapeJson(encryptedValue) + "\",\"key_id\":\"" + escapeJson(keyId)
				+ "\"}";

		send("PUT", path, body, 201, 204);
	}

	/**
	 * Reads the repository public key used to encrypt Actions secrets.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @return GitHub public key id and Base64 encoded public key
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public GitHubPublicKey readRepositorySecretPublicKey(String owner, String repo)
			throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/secrets/public-key";
		String responseBody = send("GET", path, null, 200);

		return new GitHubPublicKey(
				extractJsonString(responseBody, "key_id"),
				extractJsonString(responseBody, "key")
		);
	}

	/**
	 * Checks whether a repository Actions secret exists.
	 *
	 * <p>
	 * This checks secret metadata only. GitHub does not return the plaintext secret
	 * value.
	 * </p>
	 *
	 * @param owner      repository owner or organization
	 * @param repo       repository name
	 * @param secretName secret name
	 * @return true if the secret exists
	 * @throws IOException          if the API request fails for a reason other than
	 *                              not found
	 * @throws InterruptedException if the request is interrupted
	 */
	public boolean repositorySecretExists(String owner, String repo, String secretName)
			throws IOException, InterruptedException {
		String path = "/repos/" + encode(owner) + "/" + encode(repo) + "/actions/secrets/" + encode(secretName);
		HttpResponse<String> response = sendRaw("GET", path, null);

		if (response.statusCode() == 200) {
			return true;
		}
		if (response.statusCode() == 404) {
			return false;
		}
		throw error(response);
	}

	/**
	 * Reads one repository Actions variable and parses shell export lines from it.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name that contains shell export lines
	 * @return parsed shell export values
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public Map<String, String> readRepositoryShellExports(String owner, String repo, String name)
			throws IOException, InterruptedException {
		String script = readRepositoryVariable(owner, repo, name);

		return parseShellExports(script);
	}

	/**
	 * Encrypts a plaintext secret using GitHub's Base64 encoded public key.
	 *
	 * @param secretValue plaintext secret value
	 * @param publicKeyBase64 Base64 encoded GitHub public key
	 * @return Base64 encoded encrypted value accepted by GitHub
	 * @throws SodiumException 
	 */
	public String encryptSecret(String secretValue, String publicKeyBase64) throws SodiumException {
		SodiumJava sodium = new SodiumJava();
		LazySodiumJava lazySodium = new LazySodiumJava(sodium, StandardCharsets.UTF_8);
		Key publicKey = Key.fromBase64String(publicKeyBase64);

		String encryptedHex = lazySodium.cryptoBoxSealEasy(secretValue, publicKey);

		if (encryptedHex == null || encryptedHex.isBlank()) {
			throw new IllegalStateException("Failed to encrypt GitHub secret value.");
		}

		return Base64.getEncoder().encodeToString(hexToBytes(encryptedHex));
	}
	
	/**
	 * Reads repository secret metadata.
	 *
	 * GitHub does not return the plaintext secret value.
	 *
	 * @param owner repository owner or organization
	 * @param repo repository name
	 * @param secretName repository secret name
	 * @return secret metadata
	 * @throws IOException if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public GitHubSecretMetadata readRepositorySecret(String owner, String repo, String secretName)
	        throws IOException, InterruptedException {
	    String path = "/repos/" + encode(owner)
	            + "/" + encode(repo)
	            + "/actions/secrets/"
	            + encode(secretName);

	    String responseBody = send("GET", path, null, 200);

	    String name = extractJsonString(responseBody, "name");
	    String createdAt = extractJsonString(responseBody, "created_at");
	    String updatedAt = extractJsonString(responseBody, "updated_at");

	    return new GitHubSecretMetadata(name, createdAt, updatedAt);
	}
	
	/**
	 * Deletes one repository Actions variable.
	 *
	 * @param owner repository owner or organization
	 * @param repo  repository name
	 * @param name  variable name
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public void deleteRepositoryVariable(String owner, String repo, String name)
	        throws IOException, InterruptedException {
	    String path = "/repos/" + encode(owner) + "/" + encode(repo)
	            + "/actions/variables/" + encode(name);
	    send("DELETE", path, null, 204);
	}
	
	/**
	 * Deletes one repository Actions secret.
	 *
	 * <p>
	 * This deletes the secret metadata and encrypted value from the repository.
	 * GitHub does not expose the plaintext secret value before or after deletion.
	 * </p>
	 *
	 * @param owner      repository owner or organization
	 * @param repo       repository name
	 * @param secretName secret name
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	public void deleteRepositorySecret(String owner, String repo, String secretName)
	        throws IOException, InterruptedException {
	    String path = "/repos/" + encode(owner) + "/" + encode(repo)
	            + "/actions/secrets/" + encode(secretName);

	    send("DELETE", path, null, 204);
	}

	/**
	 * Sends an authenticated request to the GitHub REST API.
	 *
	 * @param method           HTTP method
	 * @param path             API path starting with slash
	 * @param body             request body, or {@code null}
	 * @param expectedStatuses allowed HTTP status codes
	 * @return response body
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	protected String send(String method, String path, String body, int... expectedStatuses)
			throws IOException, InterruptedException {
		HttpResponse<String> response = sendRaw(method, path, body);

		for (int expectedStatus : expectedStatuses) {
			if (response.statusCode() == expectedStatus) {
				return response.body();
			}
		}

		throw error(response);
	}

	/**
	 * Sends an authenticated request and returns the raw response.
	 *
	 * @param method HTTP method
	 * @param path   API path starting with slash
	 * @param body   request body, or {@code null}
	 * @return raw HTTP response
	 * @throws IOException          if the API request fails
	 * @throws InterruptedException if the request is interrupted
	 */
	protected HttpResponse<String> sendRaw(String method, String path, String body)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(apiUrl + path))
				.header("Accept", "application/vnd.github+json")
				.header("Authorization", "Bearer " + token)
				.header("X-GitHub-Api-Version", "2022-11-28");

		if (body == null) {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			builder.header("Content-Type", "application/json");
			builder.method(method, HttpRequest.BodyPublishers.ofString(body));
		}

		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * Parses simple {@code export KEY=VALUE} shell lines.
	 *
	 * @param script shell text
	 * @return parsed key/value pairs
	 */
	public static Map<String, String> parseShellExports(String script) {
		Map<String, String> values = new HashMap<>();

		if (script == null || script.isBlank()) {
			return values;
		}

		String[] lines = script.split("\\R");

		for (String line : lines) {
			String trimmedLine = line.trim();

			if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
				continue;
			}

			if (!trimmedLine.startsWith("export ")) {
				continue;
			}

			String assignment = trimmedLine.substring("export ".length()).trim();
			int equalsIndex = assignment.indexOf('=');

			if (equalsIndex <= 0) {
				continue;
			}

			String key = assignment.substring(0, equalsIndex).trim();
			String value = assignment.substring(equalsIndex + 1).trim();

			values.put(key, unquote(value));
		}

		return values;
	}

	/**
	 * Extracts a simple string field from a JSON object.
	 *
	 * <p>
	 * This avoids adding a JSON dependency for this small test package. It is not a
	 * general-purpose JSON parser.
	 * </p>
	 *
	 * @param json      JSON text
	 * @param fieldName field name
	 * @return field value
	 */
	protected static String extractJsonString(String json, String fieldName) {
		String marker = "\"" + fieldName + "\"";
		int fieldIndex = json.indexOf(marker);

		if (fieldIndex < 0) {
			return null;
		}

		int colonIndex = json.indexOf(':', fieldIndex + marker.length());

		if (colonIndex < 0) {
			return null;
		}

		int valueStart = json.indexOf('"', colonIndex + 1);

		if (valueStart < 0) {
			return null;
		}

		StringBuilder value = new StringBuilder();
		boolean escaped = false;

		for (int index = valueStart + 1; index < json.length(); index++) {
			char current = json.charAt(index);

			if (escaped) {
				value.append(unescapeJsonChar(current));
				escaped = false;
				continue;
			}

			if (current == '\\') {
				escaped = true;
				continue;
			}

			if (current == '"') {
				return value.toString();
			}

			value.append(current);
		}

		return null;
	}

	private static IOException error(HttpResponse<String> response) {
		return new IOException("GitHub API request failed. status=" + response.statusCode() + ", body=" + response.body());
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String unquote(String value) {
		String trimmedValue = value.trim();

		if (trimmedValue.length() >= 2) {
			if ((trimmedValue.startsWith("\"") && trimmedValue.endsWith("\""))
					|| (trimmedValue.startsWith("'") && trimmedValue.endsWith("'"))) {
				return trimmedValue.substring(1, trimmedValue.length() - 1);
			}
		}

		return trimmedValue;
	}

	private static String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			switch (current) {
			case '"':
				result.append("\\\"");
				break;
			case '\\':
				result.append("\\\\");
				break;
			case '\n':
				result.append("\\n");
				break;
			case '\r':
				result.append("\\r");
				break;
			case '\t':
				result.append("\\t");
				break;
			default:
				result.append(current);
				break;
			}
		}
		return result.toString();
	}

	private static byte[] hexToBytes(String value) {
		if (value.length() % 2 != 0) {
			throw new IllegalArgumentException("Hex value must have an even number of characters.");
		}

		byte[] result = new byte[value.length() / 2];

		for (int index = 0; index < value.length(); index += 2) {
			result[index / 2] = (byte) Integer.parseInt(value.substring(index, index + 2), 16);
		}

		return result;
	}

	private static char unescapeJsonChar(char value) {
		switch (value) {
		case '"':
			return '"';
		case '\\':
			return '\\';
		case '/':
			return '/';
		case 'b':
			return '\b';
		case 'f':
			return '\f';
		case 'n':
			return '\n';
		case 'r':
			return '\r';
		case 't':
			return '\t';
		default:
			return value;
		}
	}
}
