package io.jpostman.kubernetes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;

/**
 * Service for reading and writing Kubernetes Secrets.
 */
public class KubernetesSecretService {

	private final CoreV1Api api;

	/**
	 * Creates a Secret service.
	 *
	 * @param client configured Kubernetes API client
	 */
	public KubernetesSecretService(ApiClient client) {
		this.api = new CoreV1Api(client);
	}

	/**
	 * Creates a Secret service.
	 *
	 * @param api Kubernetes Core V1 API
	 */
	public KubernetesSecretService(CoreV1Api api) {
		this.api = api;
	}

	/**
	 * Creates or updates a Secret.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @param values    key/value pairs to store
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void createOrUpdateSecret(String namespace, String name, Map<String, String> values) throws ApiException {
		if (secretExists(namespace, name)) {
			updateSecret(namespace, name, values);
		} else {
			createSecret(namespace, name, values);
		}
	}

	/**
	 * Creates a Secret using string data.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @param values    key/value pairs to store
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void createSecret(String namespace, String name, Map<String, String> values) throws ApiException {
		V1Secret secret = new V1Secret().metadata(new V1ObjectMeta().name(name).namespace(namespace)).type("Opaque")
				.stringData(new HashMap<>(values));

		api.createNamespacedSecret(namespace, secret).execute();
	}

	/**
	 * Updates an existing Secret using string data.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @param values    key/value pairs to store
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void updateSecret(String namespace, String name, Map<String, String> values) throws ApiException {
		V1Secret secret = readSecretObject(namespace, name);
		secret.setData(null);
		secret.setStringData(new HashMap<>(values));

		api.replaceNamespacedSecret(name, namespace, secret).execute();
	}

	/**
	 * Reads a Secret as UTF-8 key/value pairs.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @return decoded Secret data
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public Map<String, String> readSecret(String namespace, String name) throws ApiException {
		V1Secret secret = readSecretObject(namespace, name);
		Map<String, byte[]> data = secret.getData();
		Map<String, String> values = new HashMap<>();

		if (data == null) {
			return values;
		}

		for (Map.Entry<String, byte[]> entry : data.entrySet()) {
			values.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
		}

		return values;
	}

	/**
	 * Reads one Secret value.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @param key       key to read
	 * @return value for the key, or {@code null} when missing
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public String readSecretValue(String namespace, String name, String key) throws ApiException {
		return readSecret(namespace, name).get(key);
	}

	/**
	 * Reads one Secret value and parses shell export lines from it.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @param key       key containing shell export lines
	 * @return parsed shell export values
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public Map<String, String> readSecretShellExports(String namespace, String name, String key) throws ApiException {
		return ShellExportParser.parse(readSecretValue(namespace, name, key));
	}

	/**
	 * Checks whether a Secret exists.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @return true if the Secret exists
	 * @throws ApiException if the Kubernetes API request fails for a reason other
	 *                      than not found
	 */
	public boolean secretExists(String namespace, String name) throws ApiException {
		try {
			readSecretObject(namespace, name);
			return true;
		} catch (ApiException e) {
			if (e.getCode() == 404) {
				return false;
			}
			throw e;
		}
	}

	/**
	 * Deletes a Secret.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      Secret name
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void deleteSecret(String namespace, String name) throws ApiException {
		api.deleteNamespacedSecret(name, namespace).execute();
	}

	private V1Secret readSecretObject(String namespace, String name) throws ApiException {
		return api.readNamespacedSecret(name, namespace).execute();
	}
}
