package io.jpostman.vault;

import io.github.jopenlibs.vault.Vault;

/**
 * Strategy interface for authenticating with Vault and returning an
 * authenticated client.
 *
 * <p>
 * Applications can implement this interface when they need custom
 * authentication logic, a company-specific Vault flow, or behavior that is not
 * supported by {@link DefaultVaultAuthenticator}.
 * </p>
 */
@FunctionalInterface
public interface VaultAuthenticator {

	/**
	 * Authenticates with Vault using the supplied settings and returns an
	 * authenticated client.
	 *
	 * @param settings Vault connection and authentication settings
	 * @return authenticated Vault client
	 * @throws Exception if authentication fails or the Vault client cannot be
	 *                   created
	 */
	Vault authenticate(VaultSettings settings) throws Exception;
}
