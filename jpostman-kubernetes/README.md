# JPostman Kubernetes

JPostman Kubernetes provides lightweight helper services for loading configuration and secret values from Kubernetes ConfigMaps and Secrets.

The module is useful when JPostman tests run in or near a Kubernetes environment and need to resolve values from Kubernetes-managed resources instead of local files, environment variables, Vault, or GitHub Actions variables.

## Features

- ConfigMap helper service for reading and writing non-sensitive Kubernetes values.
- Secret helper service for reading and writing sensitive Kubernetes values.
- Support for reading a single key from a ConfigMap or Secret.
- Support for parsing shell-style values such as:

```bash
export KEY1=VALUE1
export KEY2=VALUE2
```

- Kubernetes client factory for creating a configured Kubernetes API client.
- Integration tests for ConfigMap and Secret create, read, parse, and delete flows.
- Local kind-based Kubernetes development setup under `src/test/resources`.

## KubernetesClientFactory

`KubernetesClientFactory` creates the Kubernetes API client used by the ConfigMap and Secret services.

The factory loads Kubernetes configuration from the configured `KUBECONFIG` value or from the default Kubernetes client configuration.

Example:

```java
ApiClient client = new KubernetesClientFactory().createDefaultClient();
```

Then pass the client into the service:

```java
KubernetesConfigMapService configMaps =
        new KubernetesConfigMapService(client);

KubernetesSecretService secrets =
        new KubernetesSecretService(client);
```

## Configuration

The integration tests and local setup use these settings:

```properties
KUBECONFIG=jpostman-kind-kubeconfig.yaml
KUBE_CONFIG_MAP_NAME=jpostman-test-config
KUBE_SECRET_NAME=jpostman-test-secret
KUBE_CONTEXT=kind-jpostman-dev
KUBE_NAMESPACE=jpostman-dev
KUBE_POD_NAME=jpostman-test-pod
KUBE_SERVICE_ACCOUNT_NAME=jpostman-test-runner
```

Values can be provided as Java system properties or environment variables.

## Local development setup

Local Kubernetes integration testing is documented in:

```text
src/test/resources/README.md
```

The setup creates a local kind cluster, namespace, service account, RBAC role, ConfigMap, Secret, and test Pod. It also generates local test configuration files such as:

```text
kubernetes-local.properties
jpostman-kind-kubeconfig.yaml
```

These files are for local development and should not be published as production configuration.
