package io.jpostman.kubernetes;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

/**
 * Creates Kubernetes API clients from kubeconfig or default configuration.
 */
public class KubernetesClientFactory {

	/**
	 * Creates a Kubernetes API client.
	 *
	 * <p>
	 * If {@code KUBECONFIG} is provided as a Java system property or environment
	 * variable, that kubeconfig file is used. If the value is a relative path, this
	 * method first checks the working directory and then the test classpath.
	 * Otherwise, the default Kubernetes client configuration is used.
	 * </p>
	 *
	 * @return configured Kubernetes API client
	 * @throws IOException if Kubernetes configuration cannot be loaded
	 */
	public ApiClient createDefaultClient() throws IOException {
		String kubeconfig = optional("KUBECONFIG", null);

		ApiClient client;

		if (kubeconfig != null && !kubeconfig.isBlank()) {
			client = createClientFromKubeconfig(kubeconfig);
		} else {
			client = ClientBuilder.defaultClient();
		}

		Configuration.setDefaultApiClient(client);
		return client;
	}

	private ApiClient createClientFromKubeconfig(String kubeconfig) throws IOException {
		File kubeconfigFile = resolveKubeconfigFile(kubeconfig);

		try (FileReader reader = new FileReader(kubeconfigFile)) {
			return ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build();
		}
	}

	private File resolveKubeconfigFile(String kubeconfig) throws IOException {
		File file = new File(kubeconfig);

		if (file.isFile()) {
			return file;
		}

		URL resource = Thread.currentThread().getContextClassLoader().getResource(kubeconfig);
		if (resource != null && "file".equals(resource.getProtocol())) {
			File resourceFile = new File(resource.getFile());
			if (resourceFile.isFile()) {
				return resourceFile;
			}
		}

		File testResourcesFile = new File("src/test/resources", kubeconfig);
		if (testResourcesFile.isFile()) {
			return testResourcesFile;
		}

		throw new IOException("Kubeconfig file not found: " + kubeconfig);
	}

	public static String optional(String name, String defaultValue) {
		String value = System.getProperty(name);

		if (value == null || value.isBlank()) {
			value = System.getenv(name);
		}

		return value == null || value.isBlank() ? defaultValue : value;
	}
}
