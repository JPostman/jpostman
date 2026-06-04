# JPostman Vault

JPostman Vault provides a small, testable wrapper around the JOpenLibs Vault Java driver. The design keeps the default Vault authentication flow simple while still allowing projects to plug in a custom authenticator when the built-in behavior is not enough.

## Features

- Simple `VaultClientFactory` API for creating authenticated Vault clients.
- Default authenticator implementation with support for:
  - Token
  - Userpass
  - AppRole
  - JWT
  - GitHub
  - LDAP
- Custom authentication extension point through `VaultAuthenticator`.
- Optional Vault Enterprise namespace support.
- Optional SSL settings:
  - `VAULT_SSL_VERIFY`
  - `VAULT_SSL_PEM_FILE`
- Secret helper service for reading and writing Vault KV data.
- Local Docker-based test setup under `src/test/resources`.

## VaultSettings

`VaultSettings` contains the main Vault connection and auth configuration:

```java
VaultSettings settings = new VaultSettings(
        vaultAddress,
        namespace,
        authMethod,
        authPath
);
```

Where:

```text
vaultAddress = Vault URL, for example http://127.0.0.1:8200
namespace    = optional Vault Enterprise namespace, or null
authMethod   = token, userpass, approle, jwt, github, or ldap
authPath     = auth mount path, for example userpass, approle, jwt, github, ldap
```

Credential values are resolved from Java system properties first, then environment variables.

## Supported auth configuration

### Token

```properties
VAULT_AUTH_METHOD=token
VAULT_TOKEN=root
```

### Userpass

```properties
VAULT_AUTH_METHOD=userpass
VAULT_USERPASS_AUTH_PATH=userpass
VAULT_USERNAME=testuser
VAULT_PASSWORD=testpass
```

### AppRole

```properties
VAULT_AUTH_METHOD=approle
VAULT_APPROLE_AUTH_PATH=approle
VAULT_ROLE_ID=your_role_id
VAULT_SECRET_ID=your_secret_id
```

Important distinction:

```text
approle     = auth mount path
my-java-app = AppRole role name
```

For Java tests, the auth path should be `approle`, not the role name.

### JWT

```properties
VAULT_AUTH_METHOD=jwt
VAULT_JWT_AUTH_PATH=jwt
VAULT_JWT_ROLE=my-jwt-role
VAULT_JWT=your_signed_jwt
```

The local setup script can generate a signed JWT for testing.

### GitHub

```properties
VAULT_AUTH_METHOD=github
VAULT_GITHUB_AUTH_PATH=github
VAULT_GITHUB_TOKEN=your_github_pat
```

GitHub auth is optional in the local setup. Developers must create their own GitHub Personal Access Token with `read:org` and keep it only in the local ignored `vault-local.properties` file.

### LDAP

```properties
VAULT_AUTH_METHOD=ldap
VAULT_LDAP_AUTH_PATH=ldap
VAULT_LDAP_USERNAME=testuser
VAULT_LDAP_PASSWORD=testpass
```

The local setup uses OpenLDAP in Docker with domain `jpostman.io`.

## SSL configuration

For local Docker testing over HTTP, SSL verification is disabled:

```properties
VAULT_SSL_VERIFY=false
VAULT_SSL_PEM_FILE=/path/to/vault-ca.pem
```

`VAULT_SSL_PEM_FILE` is only used when SSL verification is enabled.

For an HTTPS Vault server with a custom CA file:

```properties
VAULT_SSL_VERIFY=true
VAULT_SSL_PEM_FILE=/path/to/vault-ca.pem
```

## Custom VaultAuthenticator

The default behavior is implemented by `DefaultVaultAuthenticator`. If your project needs a custom auth flow, create your own `VaultAuthenticator` implementation.

Example that delegates to the default authenticator:

```java
public class CustomVaultAuthenticator implements VaultAuthenticator {

    private final DefaultVaultAuthenticator delegate = new DefaultVaultAuthenticator();

    @Override
    public Vault authenticate(VaultSettings settings) throws Exception {
        // Add custom logic before default authentication if needed.
        return delegate.authenticate(settings);
    }
}
```

Use it with the factory:

```java
VaultClientFactory factory = new VaultClientFactory(new CustomVaultAuthenticator());
Vault vault = factory.createVault(settings);
```

This is useful when a company needs a different Vault flow, a custom mount layout, an internal token exchange, or a driver workaround.

## VaultSecretService

`VaultSecretService` is separate from authentication. Create an authenticated `Vault` client first, then use the service to read or write secrets.

```java
Vault vault = new VaultClientFactory().createVault(settings);
VaultSecretService service = new VaultSecretService(vault);

Map<String, String> data = service.readKv2("secret", "dev/myapp");
String username = data.get("username");
String password = data.get("password");
```

For the current JOpenLibs driver setup, `readKv2("secret", "dev/myapp")` reads the logical path:

```text
secret/dev/myapp
```

The driver handles the configured KV engine version.

## Local development setup

Local integration testing is documented in:

```text
src/test/resources/README.md
```

The setup script generates `vault-local.properties`. This file is local-only and should not be committed.