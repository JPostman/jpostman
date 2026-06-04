package io.jpostman.vault;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.response.LookupResponse;

public class VaultClientFactoryIT {

	private static final String DEFAULT_KV_MOUNT = "secret";
	private static final String DEFAULT_SECRET_PATH = "dev/myapp";

	private final VaultClientFactory vaultClientFactory = new VaultClientFactory();

	private final String address;
	private final String namespace;

	private Properties vaultProperties;

	public VaultClientFactoryIT() {
		address = optionalProperty("VAULT_ADDR", "http://127.0.0.1:8200");
		namespace = optionalProperty("VAULT_NAMESPACE", null);
	}

	@BeforeClass
	public void loadProperties() throws Exception {
		vaultProperties = loadVaultLocalProperties();

		// Make properties available to VaultSettings.required(...)
		// This is useful if VaultSettings reads System properties or environment
		// variables.
		copyToSystemPropertiesIfMissing(vaultProperties);
	}

	private static Properties loadVaultLocalProperties() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = VaultClientFactoryIT.class.getClassLoader()
				.getResourceAsStream("vault-local.properties")) {
			if (input == null) {
				throw new SkipException("Skipping: src/test/resources/vault-local.properties not found");
			}
			properties.load(input);
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
	public void shouldLoginWithConfiguredAuthMethod() throws Exception {
		String vaultAddress = requiredProperty("VAULT_ADDR");
		String authMethod = requiredProperty("VAULT_AUTH_METHOD");
		String authPath = optionalProperty("VAULT_AUTH_PATH", authMethod);

		VaultSettings settings = new VaultSettings(vaultAddress, null, authMethod, authPath);
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithToken() throws Exception {
		requirePropertyOrSkip("VAULT_TOKEN");
		VaultSettings settings = new VaultSettings(address, namespace, "token", null);
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
		assertEquals(settings.authToken(), settings.get("VAULT_TOKEN"));
	}

	@Test
	public void shouldLoginWithCustomVaultTokenAuthenticator() throws Exception {
		VaultAuthenticator customAuthenticator = new DefaultVaultAuthenticator() {
			@Override
			protected Vault authenticateWithToken(VaultConfig config, VaultSettings settings) throws Exception {
				return authenticateWithToken(config, settings.get("VAULT_TOKEN"));
			}
		};
		VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);
		VaultSettings settings = new VaultSettings(address, namespace, "token");
		Vault vault = customFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithUserPass() throws Exception {
		requirePropertyOrSkip("VAULT_USERNAME");
		requirePropertyOrSkip("VAULT_PASSWORD");

		VaultSettings settings = new VaultSettings(address, namespace, "userpass",
				optionalProperty("VAULT_AUTH_PATH", "userpass"));
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithCustomVaultUserPassAuthenticator() throws Exception {
		VaultAuthenticator customAuthenticator = new DefaultVaultAuthenticator() {
			@Override
			protected Vault authenticateWithUserPass(VaultConfig config, VaultSettings settings, Vault vault,
					String mount) throws Exception {
				return authenticateWithUserPass(config, vault, mount, "testuser", "testpass");
			}
		};
		VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);
		VaultSettings settings = new VaultSettings(address, namespace, "userpass");
		Vault vault = customFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithAppRole() throws Exception {
		requirePropertyOrSkip("VAULT_ROLE_ID");
		requirePropertyOrSkip("VAULT_SECRET_ID");
		VaultSettings settings = new VaultSettings(address, namespace, "approle",
				optionalProperty("VAULT_APPROLE_AUTH_PATH", "approle"));
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithCustomVaultAppRoleAuthenticator() throws Exception {
		VaultAuthenticator customAuthenticator = new DefaultVaultAuthenticator() {
			@Override
			protected Vault authenticateWithAppRole(VaultConfig config, VaultSettings settings, Vault vault,
					String mount) throws Exception {
				return authenticateWithAppRole(config, vault, mount, settings.get("VAULT_ROLE_ID"),
						settings.get("VAULT_SECRET_ID"));
			}
		};
		VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);
		VaultSettings settings = new VaultSettings(address, namespace, "approle");
		Vault vault = customFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithJwt() throws Exception {
		requirePropertyOrSkip("VAULT_JWT_ROLE");
		requirePropertyOrSkip("VAULT_JWT");

		VaultSettings settings = new VaultSettings(address, namespace, "jwt",
				optionalProperty("VAULT_JWT_AUTH_PATH", "jwt"));
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithCustomVaultJwtAuthenticator() throws Exception {
		VaultAuthenticator customAuthenticator = new DefaultVaultAuthenticator() {
			@Override
			protected Vault authenticateWithJwt(VaultConfig config, VaultSettings settings, Vault vault, String mount)
					throws Exception {
				return authenticateWithJwt(config, vault, mount, "my-jwt-role", settings.get("VAULT_JWT"));
			}
		};
		VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);
		VaultSettings settings = new VaultSettings(address, namespace, "jwt");
		Vault vault = customFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithGithub() throws Exception {
		requirePropertyOrSkip("VAULT_GITHUB_TOKEN");

		VaultSettings settings = new VaultSettings(address, namespace, "github",
				optionalProperty("VAULT_GITHUB_AUTH_PATH", "github"));
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithCustomVaultGithubAuthenticator() throws Exception {
		VaultAuthenticator customAuthenticator = new DefaultVaultAuthenticator() {
			@Override
			protected Vault authenticateWithGithub(VaultConfig config, VaultSettings settings, Vault vault,
					String mount) throws Exception {
				return authenticateWithGithub(config, vault, mount, settings.get("VAULT_GITHUB_TOKEN"));
			}
		};
		VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);
		VaultSettings settings = new VaultSettings(address, namespace, "github");
		Vault vault = customFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldLoginWithLdap() throws Exception {
		requirePropertyOrSkip("VAULT_LDAP_USERNAME");
		requirePropertyOrSkip("VAULT_LDAP_PASSWORD");

		VaultSettings settings = new VaultSettings(address, namespace, "ldap",
				optionalProperty("VAULT_LDAP_AUTH_PATH", "ldap"));
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
		
		// Lookup
		LookupResponse response = vault.auth().lookupSelf();
		assertEquals(response.getDisplayName(), "ldap-testuser");
		assertEquals(response.isRenewable(), true);
		
		// Renew Token
		assertEquals(settings.authToken(), vault.auth().renewSelf().getAuthClientToken());
	}

	@Test
	public void shouldLoginWithCustomVaultLdapAuthenticator() throws Exception {
		VaultAuthenticator customAuthenticator = new DefaultVaultAuthenticator() {
			@Override
			protected Vault authenticateWithLdap(VaultConfig config, VaultSettings settings, Vault vault, String mount)
					throws Exception {
				return authenticateWithLdap(config, vault, mount, "testuser", "testpass");
			}
		};
		VaultClientFactory customFactory = new VaultClientFactory(customAuthenticator);
		VaultSettings settings = new VaultSettings(address, namespace, "github");
		Vault vault = customFactory.createVaultWithAuth(settings);
		assertValidVaultLogin(vault);
	}

	@Test
	public void shouldRejectUnsupportedAuthMethod() {
		VaultSettings settings = new VaultSettings(address, namespace, "invalid-auth");

		try {
			vaultClientFactory.createVaultWithAuth(settings);
			fail("Expected IllegalArgumentException for unsupported auth method.");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("Unsupported auth method"),
					"Error message should mention unsupported auth method.");
		} catch (Exception e) {
			fail("Expected IllegalArgumentException but got: " + e.getClass().getName(), e);
		}
		
		assertEquals(settings.authToken(), null);
	}

	@Test
	public void canAuthenticateAndReadKv2Secret() throws Exception {
		String mount = optionalProperty("VAULT_KV_MOUNT", DEFAULT_KV_MOUNT);
		String path = optionalProperty("VAULT_SECRET_PATH", DEFAULT_SECRET_PATH);

		VaultSettings settings = new VaultSettings(address, namespace, "token");
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		VaultSecretService service = new VaultSecretService(vault);

		assertEquals(service.readKv2Secret(mount, path, "username"), "testuser",
				"Unexpected username from Vault secret");
		assertEquals(service.readKv2Secret(mount, path, "password"), "testpass",
				"Unexpected password from Vault secret");

		Map<String, String> data = service.readKv2Secret(mount, path);
		assertNotNull(data, "Vault secret data should not be null");
		assertEquals(data.get("username"), "testuser", "Unexpected username from Vault secret");
		assertEquals(data.get("password"), "testpass", "Unexpected password from Vault secret");
	}

	@Test
	public void canWriteReadAndDeleteKv2Secret() throws Exception {
		String mount = optionalProperty("VAULT_KV_MOUNT", DEFAULT_KV_MOUNT);
		String path = "io/jpostman";
		String key = "version";
		String value = "1.0.1";

		VaultSettings settings = new VaultSettings(address, namespace, "token");
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		VaultSecretService service = new VaultSecretService(vault);

		Map<String, String> secret = new HashMap<>();
		secret.put(key, value);

		service.writeKv2Secret(mount, path, secret);

		Map<String, String> data = service.readKv2Secret(mount, path);

		assertNotNull(data, "Vault secret data should not be null");
		assertEquals(data.get(key), value);

		service.deleteKv2Secret(mount, path, key);

		Map<String, String> dataAfterKeyDelete = service.readKv2Secret(mount, path);
		assertTrue(dataAfterKeyDelete == null || !dataAfterKeyDelete.containsKey("version"),
				"Vault key should be deleted");

		service.deleteKv2Secret(mount, path);

		Map<String, String> deletedData = service.readKv2Secret(mount, path);

		assertTrue(deletedData == null || deletedData.isEmpty(), "Vault secret should be deleted");
	}

	@Test
	public void canReadShellValuesFromVaultSecret() throws Exception {
		String mount = optionalProperty("VAULT_KV_MOUNT", DEFAULT_KV_MOUNT);
		String path = "io/jpostman";

		VaultSettings settings = new VaultSettings(address, namespace, "token");
		Vault vault = vaultClientFactory.createVaultWithAuth(settings);
		VaultSecretService service = new VaultSecretService(vault);

		String script = "" + "export KEY1=VALUE1\n" + "export KEY2=VALUE2\n";

		Map<String, String> secret = new HashMap<>();
		secret.put("script", script);

		service.writeKv2Secret(mount, path, secret);

		Map<String, String> shellValues = service.readKv2ShellExports(mount, path, "script");

		assertEquals(shellValues.get("KEY1"), "VALUE1");
		assertEquals(shellValues.get("KEY2"), "VALUE2");

		service.deleteKv2Secret(mount, path);
	}

	private void assertValidVaultLogin(Vault vault) throws Exception {
		assertNotNull(vault, "Vault client should not be null.");
		LookupResponse response = vault.auth().lookupSelf();
		assertNotNull(response, "lookupSelf response should not be null.");
		assertNotNull(response.getPolicies(), "lookupSelf policies should not be null.");
	}

	private String requiredProperty(String name) {
		String value = get(name, null);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing required property: " + name);
		}
		return value;
	}

	private void requirePropertyOrSkip(String name) {
		String value = get(name, null);
		if (value == null || value.isBlank()) {
			throw new SkipException("Skipping test because " + name + " is not set.");
		}
	}

	private String optionalProperty(String name, String defaultValue) {
		return get(name, defaultValue);
	}

	private String get(String name, String defaultValue) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) {
			value = System.getenv(name);
		}
		if ((value == null || value.isBlank()) && vaultProperties != null) {
			value = vaultProperties.getProperty(name);
		}
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}
}