package io.jpostman.vault;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.response.LogicalResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Service for reading, writing, and deleting Vault key/value secrets.
 *
 * <p>
 * Authentication is handled by {@link VaultClientFactory}. This service only
 * uses an already-authenticated {@link Vault} client to work with secret data.
 * </p>
 */
public class VaultSecretService {

	private final Vault vault;

	/**
	 * Creates a secret service using an authenticated Vault client.
	 *
	 * @param vault authenticated Vault client
	 * @throws NullPointerException if {@code vault} is {@code null}
	 */
	public VaultSecretService(Vault vault) {
		this.vault = Objects.requireNonNull(vault, "vault");
	}

	/**
	 * Gets one value from an already-loaded secret data map.
	 *
	 * @param data secret data returned from Vault
	 * @param key  key to read
	 * @return value for the key, or {@code null} when the data or key is missing
	 */
	public String getValue(Map<String, String> data, String key) throws Exception {
		if (data == null || data.isEmpty()) {
			return null;
		}
		return data.get(key);
	}

	/**
	 * Reads a Vault logical path.
	 *
	 * @param path Vault logical path, for example {@code secret/dev/myapp}
	 * @return secret data
	 * @throws Exception if the path cannot be read
	 */
	public Map<String, String> readSecret(String path) throws Exception {
		LogicalResponse response = vault.logical().read(path);
		return response.getData();
	}

	/**
	 * Reads one value from a Vault secret.
	 *
	 * @param path Vault logical path, for example {@code secret/dev/myapp}
	 * @param key  key to read
	 * @return value for the key, or {@code null} when the data or key is missing
	 * @throws Exception if the path cannot be read
	 */
	public String readSecret(String path, String key) throws Exception {
		return getValue(readSecret(path), key);
	}

	/**
	 * Reads a secret from a KV v2 mount.
	 *
	 * @param mount KV mount name, for example {@code secret}
	 * @param path  path under the mount, for example {@code dev/myapp}
	 * @return secret data
	 * @throws Exception if the path cannot be read
	 */
	public Map<String, String> readKv2Secret(String mount, String path) throws Exception {
		return readSecret(join(mount, path));
	}

	/**
	 * Reads one value from a KV v2 secret.
	 *
	 * @param mount KV mount name, for example {@code secret}
	 * @param path  path under the mount, for example {@code dev/myapp}
	 * @param key   key to read
	 * @return value for the key, or {@code null} when the data or key is missing
	 * @throws Exception if the path cannot be read
	 */
	public String readKv2Secret(String mount, String path, String key) throws Exception {
		return getValue(readKv2Secret(mount, path), key);
	}

	/**
	 * Writes key/value pairs to a Vault logical path.
	 *
	 * @param path   Vault logical path, for example {@code secret/dev/myapp}
	 * @param values key/value pairs to write
	 * @throws Exception if the path cannot be written
	 */
	public void writeSecret(String path, Map<String, String> values) throws Exception {
		Map<String, Object> objectValues = new HashMap<>();
		objectValues.putAll(values);
		vault.logical().write(path, objectValues);
	}

	/**
	 * Writes a secret to a KV v2 mount.
	 *
	 * @param mount  KV mount name, for example {@code secret}
	 * @param path   path under the mount, for example {@code dev/myapp}
	 * @param values key/value pairs to write
	 * @throws Exception if the path cannot be written
	 */
	public void writeKv2Secret(String mount, String path, Map<String, String> values) throws Exception {
		writeSecret(join(mount, path), values);
	}

	/**
	 * Deletes a Vault logical path.
	 *
	 * @param path Vault logical path, for example {@code secret/dev/myapp}
	 * @throws Exception if the path cannot be deleted
	 */
	public void deleteSecret(String path) throws Exception {
		vault.logical().delete(path);
	}

	/**
	 * Deletes a secret from a KV v2 mount.
	 *
	 * @param mount KV mount name, for example {@code secret}
	 * @param path  path under the mount, for example {@code dev/myapp}
	 * @throws Exception if the path cannot be deleted
	 */
	public void deleteKv2Secret(String mount, String path) throws Exception {
		deleteSecret(join(mount, path));
	}

	/**
	 * Deletes one key from a KV v2 secret by reading the secret, removing the key,
	 * and writing the updated data back to Vault.
	 *
	 * @param mount KV mount name, for example {@code secret}
	 * @param path  path under the mount, for example {@code dev/myapp}
	 * @param key   key to remove
	 * @throws Exception if the secret cannot be read or written
	 */
	public void deleteKv2Secret(String mount, String path, String key) throws Exception {
		Map<String, String> data = readKv2Secret(mount, path);
		if (data == null || !data.containsKey(key)) {
			return;
		}
		data.remove(key);
		writeKv2Secret(mount, path, data);
	}

	/**
	 * Reads a Vault logical path, gets a shell script value from the given key, and
	 * returns exported variables from that script.
	 *
	 * @param path Vault logical path, for example {@code secret/dev/myapp}
	 * @param key  key that contains shell export lines
	 * @return exported shell variables as key/value pairs
	 * @throws Exception if the path cannot be read
	 */
	public Map<String, String> readShellExports(String path, String key) throws Exception {
		return readShellValues(readSecret(path), key);
	}

	/**
	 * Reads a KV v2 secret, gets a shell script value from the given key, and
	 * returns exported variables from that script.
	 *
	 * @param mount KV mount name, for example {@code secret}
	 * @param path  path under the mount, for example {@code dev/myapp}
	 * @param key   key that contains shell export lines
	 * @return exported shell variables as key/value pairs
	 * @throws Exception if the secret cannot be read
	 */
	public Map<String, String> readKv2ShellExports(String mount, String path, String key) throws Exception {
		return readShellValues(readKv2Secret(mount, path), key);
	}

	/**
	 * Extracts shell export variables from one secret value.
	 *
	 * @param data secret data returned from Vault
	 * @param key  key that contains shell export lines
	 * @return exported shell variables as key/value pairs
	 * @throws Exception if the value cannot be read
	 */
	private Map<String, String> readShellValues(Map<String, String> data, String key) throws Exception {
		String value = getValue(data, key);
		if (value == null || value.trim().isEmpty()) {
			return new HashMap<>();
		}
		return parseShellExports(value);
	}

	/**
	 * Parses simple {@code export KEY=VALUE} shell lines.
	 *
	 * <p>
	 * Example script value:
	 * </p>
	 *
	 * <pre>
	 * export KEY1=VALUE1
	 * export KEY2=VALUE2
	 * </pre>
	 *
	 * @param script shell text
	 * @return exported variables as key/value pairs
	 */
	public static Map<String, String> parseShellExports(String script) {
		Map<String, String> values = new HashMap<>();
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
			values.put(key, value);
		}
		return values;
	}

	/**
	 * Joins two Vault path fragments using one slash.
	 *
	 * @param left  first path fragment
	 * @param right second path fragment
	 * @return joined path
	 */
	private static String join(String left, String right) {
		String a = trimSlashes(left);
		String b = trimSlashes(right);
		return a + "/" + b;
	}

	/**
	 * Removes leading and trailing slashes.
	 *
	 * @param value path fragment
	 * @return normalized path fragment, or an empty string when {@code value} is
	 *         {@code null}
	 */
	private static String trimSlashes(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("^/+", "").replaceAll("/+$", "");
	}
}
