package io.jpostman.vault;

import java.io.File;

import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.response.AuthResponse;

/**
 * Default Vault authenticator used by {@link VaultClientFactory}.
 *
 * <p>
 * Supports token, userpass, AppRole, LDAP, GitHub, and JWT authentication. Each
 * auth method is implemented in a protected method so subclasses can override
 * only the behavior they need.
 * </p>
 */
public class DefaultVaultAuthenticator implements VaultAuthenticator {

	protected final String vaultToken;
	protected final String vaultUsername;
	protected final String vaultPassword;
	protected final String vaultRoleId;
	protected final String vaultSecretId;
	protected final String vaultLdapUsername;
	protected final String vaultLdapPassword;
	protected final String vaultGitHubToken;
	protected final String vaultJwtRole;
	protected final String vaultJwt;
	protected final String vaultSslVerify;
	protected final String vaultSslFile;
	
	public DefaultVaultAuthenticator() {
		vaultToken = "VAULT_TOKEN";
		vaultUsername = "VAULT_USERNAME";
		vaultPassword = "VAULT_PASSWORD";
		vaultRoleId = "VAULT_ROLE_ID";
		vaultSecretId = "VAULT_SECRET_ID";
		vaultLdapUsername = "VAULT_LDAP_USERNAME";
		vaultLdapPassword = "VAULT_LDAP_PASSWORD";
		vaultGitHubToken = "VAULT_GITHUB_TOKEN";
		vaultJwtRole = "VAULT_JWT_ROLE";
		vaultJwt = "VAULT_JWT";
		vaultSslVerify = "VAULT_SSL_VERIFY";
		vaultSslFile = "VAULT_SSL_PEM_FILE";
	}
	
	/**
	 * Authenticates with Vault using the configured auth method.
	 *
	 * <p>
	 * authMethod selects the login flow, for example token, userpass, approle,
	 * ldap, github, or jwt.
	 * </p>
	 *
	 * <p>
	 * authPath is the Vault auth mount path. For example: vault auth enable
	 * -path=userpass userpass
	 * </p>
	 *
	 * <p>
	 * In that example, method is "userpass" and mount is "userpass". They often
	 * have the same value, but they are different concepts. With a custom mount,
	 * for example vault auth enable -path=employees userpass, method is "userpass"
	 * and mount is "employees".
	 * </p>
	 *
	 * @param settings Vault connection and authentication settings
	 * @return authenticated Vault client
	 * @throws Exception if authentication fails or configuration is invalid
	 */
	@Override
	public Vault authenticate(VaultSettings settings) throws Exception {
		String method = settings.authMethod();
		String mount = settings.authPath();

		if (method == null || method.isBlank()) {
			throw new IllegalArgumentException("Vault auth method cannot be null or blank.");
		}

		if (mount == null || mount.isBlank()) {
			mount = defaultMountFor(method);
		}

		VaultConfig config = new VaultConfig().address(settings.address()).sslConfig(sslConfig(settings));
		if (settings.namespace() != null && !settings.namespace().isBlank()) {
			config.nameSpace(settings.namespace());
		}
		Vault vault = Vault.create(config.build());

		switch (method.toLowerCase()) {
		case "token":
			vault = authenticateWithToken(config, settings);
			break;
		case "userpass":
			vault = authenticateWithUserPass(config, settings, vault, mount);
			break;
		case "approle":
			vault = authenticateWithAppRole(config, settings, vault, mount);
			break;
		case "ldap":
			vault = authenticateWithLdap(config, settings, vault, mount);
			break;
		case "github":
			vault = authenticateWithGithub(config, settings, vault, mount);
			break;
		case "jwt":
			vault = authenticateWithJwt(config, settings, vault, mount);
			break;
		default:
			vault = authenticateUnsupported(config, settings, method, mount);
			break;
		}
		
		settings.authToken(config.getToken());
		
		return vault;
	}

	/**
	 * Authenticates using an existing Vault token from settings.
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings containing VAULT_TOKEN
	 * @return authenticated Vault client
	 * @throws Exception if the token is missing or the client cannot be created
	 */
	protected Vault authenticateWithToken(VaultConfig config, VaultSettings settings) throws Exception {
		return authenticateWithToken(config, settings.get(vaultToken));
	}

	/**
	 * Authenticates using an existing Vault token.
	 *
	 * @param config Vault driver configuration
	 * @param token  Vault token
	 * @return authenticated Vault client
	 * @throws Exception if the token is blank or the client cannot be created
	 */
	protected Vault authenticateWithToken(VaultConfig config, String token) throws Exception {
		return createVaultWithToken(config, token);
	}

	/**
	 * Authenticates using Userpass auth from settings.
	 *
	 * <p>
	 * The mount is only the auth engine path. It does not contain credentials.
	 * Credentials come from VAULT_USERNAME and VAULT_PASSWORD.
	 * </p>
	 *
	 * <p>
	 * Example Vault setup: vault auth enable -path=userpass userpass vault write
	 * auth/userpass/users/testuser password=testpass policies=developer
	 * </p>
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings containing username and password
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example userpass
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithUserPass(VaultConfig config, VaultSettings settings, Vault vault, String mount)
			throws Exception {
		return authenticateWithUserPass(config, vault, mount, settings.get(vaultUsername), settings.get(vaultPassword));
	}

	/**
	 * Authenticates using Userpass auth.
	 *
	 * @param config   Vault driver configuration
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example userpass
	 * @param username Vault username
	 * @param password Vault password
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithUserPass(VaultConfig config, Vault vault, String mount, String username,
			String password) throws Exception {
		AuthResponse response = vault.auth().loginByUserPass(username, password, mount);
		return createVaultWithToken(config, response.getAuthClientToken());
	}

	/**
	 * Authenticates using AppRole auth from settings.
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings containing role_id and secret_id
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example approle
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithAppRole(VaultConfig config, VaultSettings settings, Vault vault, String mount)
			throws Exception {
		return authenticateWithAppRole(config, vault, mount, settings.get(vaultRoleId), settings.get(vaultSecretId));
	}

	/**
	 * Authenticates using AppRole auth.
	 *
	 * @param config   Vault driver configuration
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example approle
	 * @param roleId   Vault AppRole role_id
	 * @param secretId Vault AppRole secret_id
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithAppRole(VaultConfig config, Vault vault, String mount, String roleId,
			String secretId) throws Exception {
		AuthResponse response = vault.auth().loginByAppRole(mount, roleId, secretId);
		return createVaultWithToken(config, response.getAuthClientToken());
	}

	/**
	 * Authenticates using LDAP auth from settings.
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings containing LDAP username and password
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example ldap
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithLdap(VaultConfig config, VaultSettings settings, Vault vault, String mount)
			throws Exception {
		return authenticateWithLdap(config, vault, mount, settings.get(vaultLdapUsername), settings.get(vaultLdapPassword));
	}

	/**
	 * Authenticates using LDAP auth.
	 *
	 * @param config   Vault driver configuration
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example ldap
	 * @param username LDAP username
	 * @param password LDAP password
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithLdap(VaultConfig config, Vault vault, String mount, String username,
			String password) throws Exception {
		AuthResponse response = vault.auth().loginByLDAP(username, password, mount);
		return createVaultWithToken(config, response.getAuthClientToken());
	}

	/**
	 * Authenticates using GitHub auth from settings.
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings containing a GitHub token
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example github
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithGithub(VaultConfig config, VaultSettings settings, Vault vault, String mount)
			throws Exception {
		return authenticateWithGithub(config, vault, mount, settings.get(vaultGitHubToken));
	}

	/**
	 * Authenticates using GitHub auth.
	 *
	 * @param config      Vault driver configuration
	 * @param vault       unauthenticated Vault client
	 * @param mount       auth mount path, for example github
	 * @param githubToken GitHub token used by Vault GitHub auth
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithGithub(VaultConfig config, Vault vault, String mount, String githubToken)
			throws Exception {
		AuthResponse response = vault.auth().loginByGithub(githubToken, mount);
		return createVaultWithToken(config, response.getAuthClientToken());
	}

	/**
	 * Authenticates using JWT auth from settings.
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings containing JWT role and token
	 * @param vault    unauthenticated Vault client
	 * @param mount    auth mount path, for example jwt
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithJwt(VaultConfig config, VaultSettings settings, Vault vault, String mount)
			throws Exception {
		return authenticateWithJwt(config, vault, mount, settings.get(vaultJwtRole), settings.get(vaultJwt));
	}

	/**
	 * Authenticates using JWT auth.
	 *
	 * @param config Vault driver configuration
	 * @param vault  unauthenticated Vault client
	 * @param mount  auth mount path, for example jwt
	 * @param role   Vault JWT role
	 * @param jwt    JWT token
	 * @return authenticated Vault client
	 * @throws Exception if login fails
	 */
	protected Vault authenticateWithJwt(VaultConfig config, Vault vault, String mount, String role, final String jwt)
			throws Exception {
		AuthResponse response = vault.auth().loginByJwt(mount, role, jwt);
		return createVaultWithToken(config, response.getAuthClientToken());
	}

	/**
	 * Handles unsupported authentication methods.
	 *
	 * @param config   Vault driver configuration
	 * @param settings Vault settings
	 * @param method   unsupported method name
	 * @param mount    auth mount path
	 * @return authenticated Vault client
	 * @throws Exception always by default
	 */
	protected Vault authenticateUnsupported(VaultConfig config, VaultSettings settings, String method, String mount)
			throws Exception {
		throw new IllegalArgumentException("Unsupported auth method: " + method);
	}

	/**
	 * Creates a Vault client configured with a token.
	 *
	 * @param config Vault driver configuration
	 * @param token  Vault client token
	 * @return authenticated Vault client
	 * @throws Exception if token is blank or client cannot be created
	 */
	protected Vault createVaultWithToken(VaultConfig config, String token) throws Exception {
		if (token == null || token.isBlank()) {
			throw new IllegalStateException("Vault login did not return a client token.");
		}
		
		return Vault.create(config.token(token).build());
	}

	/**
	 * Creates SSL configuration from Vault settings.
	 *
	 * @param settings Vault settings
	 * @return SSL configuration
	 * @throws Exception if SSL configuration cannot be built
	 */
	protected SslConfig sslConfig(VaultSettings settings) throws Exception {
		return sslConfig(Boolean.parseBoolean(settings.get(vaultSslVerify, "false")),
				settings.get(vaultSslFile, null));
	}

	/**
	 * Creates SSL configuration for the Vault driver.
	 *
	 * @param verify  whether SSL certificate verification is enabled
	 * @param pemFile optional PEM file path
	 * @return SSL configuration
	 * @throws Exception if SSL configuration cannot be built
	 */
	protected SslConfig sslConfig(boolean verify, String pemFile) throws Exception {
		SslConfig sslConfig = new SslConfig().verify(verify);
		if (verify && pemFile != null && !pemFile.isBlank()) {
			sslConfig.pemFile(new File(pemFile));
		}
		return sslConfig.build();
	}

	/**
	 * Returns the default auth mount path for an auth method.
	 *
	 * <p>
	 * If Vault uses a custom mount path, pass it through VaultSettings.authPath().
	 * </p>
	 *
	 * @param method authentication method name
	 * @return default mount path
	 */
	protected String defaultMountFor(String method) {
		return method;
	}
}
