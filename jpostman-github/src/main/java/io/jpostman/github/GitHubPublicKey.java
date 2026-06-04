package io.jpostman.github;

import java.util.Objects;

/**
 * GitHub repository public key used to encrypt Actions secrets.
 */
public class GitHubPublicKey {

	private final String keyId;
	private final String key;

	/**
	 * Creates a GitHub public key holder.
	 *
	 * @param keyId GitHub public key id
	 * @param key   Base64 encoded public key
	 */
	public GitHubPublicKey(String keyId, String key) {
		this.keyId = Objects.requireNonNull(keyId, "keyId");
		this.key = Objects.requireNonNull(key, "key");
	}

	/**
	 * Returns the GitHub public key id.
	 *
	 * @return public key id
	 */
	public String keyId() {
		return keyId;
	}

	/**
	 * Returns the Base64 encoded GitHub public key.
	 *
	 * @return Base64 encoded public key
	 */
	public String key() {
		return key;
	}
}
