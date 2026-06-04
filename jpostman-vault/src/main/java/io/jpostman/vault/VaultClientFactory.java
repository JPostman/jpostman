package io.jpostman.vault;

import io.github.jopenlibs.vault.Vault;

import java.util.Objects;

/**
 * Factory for creating authenticated Vault clients.
 *
 * <p>
 * By default, this factory uses {@link DefaultVaultAuthenticator}. Users can
 * inject a custom {@link VaultAuthenticator} when they need to override the
 * default authentication behavior or support an authentication flow that is not
 * built in.
 * </p>
 */
public class VaultClientFactory {

	private final VaultAuthenticator authenticator;

	/**
	 * Creates a factory that uses {@link DefaultVaultAuthenticator}.
	 */
	public VaultClientFactory() {
		this(new DefaultVaultAuthenticator());
	}

	/**
	 * Creates a factory with a custom authenticator.
	 *
	 * @param authenticator authenticator strategy to use for all created Vault
	 *                      clients
	 * @throws NullPointerException if {@code authenticator} is {@code null}
	 */
	public VaultClientFactory(VaultAuthenticator authenticator) {
		this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
	}

	/**
	 * Creates an authenticated Vault client using the configured authenticator.
	 *
	 * @param settings Vault connection and authentication settings
	 * @return authenticated Vault client
	 * @throws NullPointerException if {@code settings} is {@code null}
	 * @throws Exception            if authentication fails or the Vault client
	 *                              cannot be created
	 */
	public Vault createVault(VaultSettings settings) throws Exception {
		Objects.requireNonNull(settings, "settings");
		return authenticator.authenticate(settings);
	}

	/**
	 * Creates an authenticated Vault client using the configured authenticator.
	 *
	 * <p>
	 * This method is kept for backward compatibility with earlier tests and
	 * examples. New code can call {@link #createVault(VaultSettings)}.
	 * </p>
	 *
	 * @param settings Vault connection and authentication settings
	 * @return authenticated Vault client
	 * @throws Exception if authentication fails or the Vault client cannot be
	 *                   created
	 */
	public Vault createVaultWithAuth(VaultSettings settings) throws Exception {
		return createVault(settings);
	}
}
