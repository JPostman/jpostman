package io.jpostman.kubernetes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.kubernetes.client.openapi.ApiClient;

/**
 * Integration tests for Kubernetes ConfigMaps and Secrets.
 */
public class KubernetesServiceIT {

	private static final String DEFAULT_NAMESPACE = "default";
	private static final String DEFAULT_CONFIG_MAP_NAME = "jpostman-test-config";
	private static final String DEFAULT_SECRET_NAME = "jpostman-test-secret";

	private Properties localProperties;

	private String namespace;
	private String configMapName;
	private String secretName;
	private KubernetesConfigMapService configMaps;
	private KubernetesSecretService secrets;

	@BeforeClass
	public void setUp() throws Exception {
		localProperties = loadLocalProperties();
		copyToSystemPropertiesIfMissing(localProperties);

		namespace = optional("KUBE_NAMESPACE", DEFAULT_NAMESPACE);
		configMapName = optional("KUBE_CONFIG_MAP_NAME", DEFAULT_CONFIG_MAP_NAME);
		secretName = optional("KUBE_SECRET_NAME", DEFAULT_SECRET_NAME);

		ApiClient client = new KubernetesClientFactory().createDefaultClient();
		configMaps = new KubernetesConfigMapService(client);
		secrets = new KubernetesSecretService(client);
	}

	@AfterClass(alwaysRun = true)
	public void cleanup() throws Exception {
		if (configMaps != null) {
			deleteConfigMapIfExists();
		}
		if (secrets != null) {
			deleteSecretIfExists();
		}
	}

	private static Properties loadLocalProperties() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = KubernetesServiceIT.class.getClassLoader()
				.getResourceAsStream("kubernetes-local.properties")) {
			if (input != null) {
				properties.load(input);
			}
		}
		return properties;
	}

	private static void copyToSystemPropertiesIfMissing(Properties properties) {
		for (String name : properties.stringPropertyNames()) {
			String currentValue = System.getProperty(name);
			if (currentValue == null || currentValue.isBlank()) {
				System.setProperty(name, properties.getProperty(name));
			}
		}
	}

	@Test
	public void canCreateReadAndDeleteConfigMap() throws Exception {
		Map<String, String> values = new HashMap<>();
		values.put("version", "1.0.1");

		// Create
		configMaps.createOrUpdateConfigMap(namespace, configMapName, values);

		assertTrue(configMaps.configMapExists(namespace, configMapName));
		assertEquals(configMaps.readConfigMapValue(namespace, configMapName, "version"), "1.0.1");

		// Update
		values.put("version", "1.1.1");
		configMaps.createOrUpdateConfigMap(namespace, configMapName, values);

		Map<String, String> data = configMaps.readConfigMap(namespace, configMapName);
		assertEquals(data.get("version"), "1.1.1");

		configMaps.deleteConfigMap(namespace, configMapName);
		assertFalse(configMaps.configMapExists(namespace, configMapName));
	}

	@Test
	public void canReadConfigMapShellExports() throws Exception {
		Map<String, String> values = new HashMap<>();
		values.put("script", "export KEY1=VALUE1\nexport KEY2=VALUE2\n");

		configMaps.createOrUpdateConfigMap(namespace, configMapName, values);

		Map<String, String> shellValues = configMaps.readConfigMapShellExports(namespace, configMapName, "script");
		assertEquals(shellValues.get("KEY1"), "VALUE1");
		assertEquals(shellValues.get("KEY2"), "VALUE2");

		Map<String, String> data = configMaps.readConfigMap(namespace, configMapName);
		shellValues = ShellExportParser.parse(data.get("script"));
		assertEquals(shellValues.get("KEY1"), "VALUE1");
		assertEquals(shellValues.get("KEY2"), "VALUE2");
	}

	@Test
	public void canCreateReadAndDeleteSecret() throws Exception {
		Map<String, String> values = new HashMap<>();
		values.put("version", "1.0.1");

		// Create
		secrets.createOrUpdateSecret(namespace, secretName, values);

		assertTrue(secrets.secretExists(namespace, secretName));
		assertEquals(secrets.readSecretValue(namespace, secretName, "version"), "1.0.1");

		// Update
		values.put("version", "1.1.1");
		secrets.createOrUpdateSecret(namespace, secretName, values);

		Map<String, String> data = secrets.readSecret(namespace, secretName);
		assertEquals(data.get("version"), "1.1.1");

		secrets.deleteSecret(namespace, secretName);

		assertFalse(secrets.secretExists(namespace, secretName));
	}

	@Test
	public void canReadSecretShellExports() throws Exception {
		Map<String, String> values = new HashMap<>();
		values.put("script", "export KEY1=VALUE1\nexport KEY2=VALUE2\n");

		secrets.createOrUpdateSecret(namespace, secretName, values);

		Map<String, String> shellValues = secrets.readSecretShellExports(namespace, secretName, "script");
		assertEquals(shellValues.get("KEY1"), "VALUE1");
		assertEquals(shellValues.get("KEY2"), "VALUE2");

		Map<String, String> data = secrets.readSecret(namespace, secretName);
		shellValues = ShellExportParser.parse(data.get("script"));
		assertEquals(shellValues.get("KEY1"), "VALUE1");
		assertEquals(shellValues.get("KEY2"), "VALUE2");
	}

	private void deleteConfigMapIfExists() {
		try {
			if (configMaps.configMapExists(namespace, configMapName)) {
				configMaps.deleteConfigMap(namespace, configMapName);
			}
		} catch (Exception e) {
			System.out.println("Cleanup skipped for ConfigMap " + configMapName + ": " + e.getMessage());
		}
	}

	private void deleteSecretIfExists() {
		try {
			if (secrets.secretExists(namespace, secretName)) {
				secrets.deleteSecret(namespace, secretName);
			}
		} catch (Exception e) {
			System.out.println("Cleanup skipped for Secret " + secretName + ": " + e.getMessage());
		}
	}

	private static String optional(String name, String defaultValue) {
		return KubernetesClientFactory.optional(name, defaultValue);
	}
}
