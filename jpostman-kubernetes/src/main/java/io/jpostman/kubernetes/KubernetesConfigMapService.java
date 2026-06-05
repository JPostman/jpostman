package io.jpostman.kubernetes;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

/**
 * Service for reading and writing Kubernetes ConfigMaps.
 */
public class KubernetesConfigMapService {

	private final CoreV1Api api;

	/**
	 * Creates a ConfigMap service.
	 *
	 * @param client configured Kubernetes API client
	 */
	public KubernetesConfigMapService(ApiClient client) {
		this.api = new CoreV1Api(client);
	}

	/**
	 * Creates a ConfigMap service.
	 *
	 * @param api Kubernetes Core V1 API
	 */
	public KubernetesConfigMapService(CoreV1Api api) {
		this.api = api;
	}

	/**
	 * Creates or updates a ConfigMap.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @param values    key/value pairs to store
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void createOrUpdateConfigMap(String namespace, String name, Map<String, String> values) throws ApiException {
		if (configMapExists(namespace, name)) {
			updateConfigMap(namespace, name, values);
		} else {
			createConfigMap(namespace, name, values);
		}
	}

	/**
	 * Creates a ConfigMap.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @param values    key/value pairs to store
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void createConfigMap(String namespace, String name, Map<String, String> values) throws ApiException {
		V1ConfigMap configMap = new V1ConfigMap().metadata(new V1ObjectMeta().name(name).namespace(namespace))
				.data(new HashMap<>(values));

		api.createNamespacedConfigMap(namespace, configMap).execute();
	}

	/**
	 * Updates an existing ConfigMap.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @param values    key/value pairs to store
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void updateConfigMap(String namespace, String name, Map<String, String> values) throws ApiException {
		V1ConfigMap configMap = readConfigMapObject(namespace, name);
		configMap.setData(new HashMap<>(values));

		api.replaceNamespacedConfigMap(name, namespace, configMap).execute();
	}

	/**
	 * Reads a ConfigMap as key/value pairs.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @return ConfigMap data
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public Map<String, String> readConfigMap(String namespace, String name) throws ApiException {
		V1ConfigMap configMap = readConfigMapObject(namespace, name);
		Map<String, String> data = configMap.getData();

		return data == null ? new HashMap<>() : new HashMap<>(data);
	}

	/**
	 * Reads one ConfigMap value.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @param key       key to read
	 * @return value for the key, or {@code null} when missing
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public String readConfigMapValue(String namespace, String name, String key) throws ApiException {
		return readConfigMap(namespace, name).get(key);
	}

	/**
	 * Reads one ConfigMap value and parses shell export lines from it.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @param key       key containing shell export lines
	 * @return parsed shell export values
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public Map<String, String> readConfigMapShellExports(String namespace, String name, String key)
			throws ApiException {
		return ShellExportParser.parse(readConfigMapValue(namespace, name, key));
	}

	/**
	 * Checks whether a ConfigMap exists.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @return true if the ConfigMap exists
	 * @throws ApiException if the Kubernetes API request fails for a reason other
	 *                      than not found
	 */
	public boolean configMapExists(String namespace, String name) throws ApiException {
		try {
			readConfigMapObject(namespace, name);
			return true;
		} catch (ApiException e) {
			if (e.getCode() == 404) {
				return false;
			}
			throw e;
		}
	}

	/**
	 * Deletes a ConfigMap.
	 *
	 * @param namespace Kubernetes namespace
	 * @param name      ConfigMap name
	 * @throws ApiException if the Kubernetes API request fails
	 */
	public void deleteConfigMap(String namespace, String name) throws ApiException {
		api.deleteNamespacedConfigMap(name, namespace).execute();
	}

	private V1ConfigMap readConfigMapObject(String namespace, String name) throws ApiException {
		return api.readNamespacedConfigMap(name, namespace).execute();
	}
}
