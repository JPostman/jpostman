package io.jpostman.vault;

/**
 * Configuration object used to create and authenticate a Vault client.
 *
 * <p>
 * The constructor values define Vault connection details such as the Vault
 * address, optional namespace, authentication method, and authentication mount
 * path. Additional credentials are resolved from Java system properties first
 * and then environment variables by {@link #get(String, String)} and
 * {@link #get(String)}.
 * </p>
 *
 * <p>
 * After authentication, the authenticator may store the returned Vault token in
 * this object.
 * </p>
 */
public class VaultSettings {

	private final String address;
	private final String namespace;
	private final String authMethod;
	private final String authPath;

	private String authToken;

	/**
	 * Returns the authenticated Vault token after login.
	 *
	 * @return authenticated Vault token, or {@code null} before login
	 */
	public String authToken() {
	    return authToken;
	}

	/**
	 * Stores the authenticated Vault token.
	 *
	 * @param authToken authenticated Vault token
	 */
	void authToken(String authToken) {
	    this.authToken = authToken;
	}


	/**
	 * Creates Vault settings.
	 *
	 * @param address    Vault server address, for example
	 *                   {@code http://127.0.0.1:8200}
	 * @param namespace  optional Vault Enterprise namespace, or {@code null} when
	 *                   not used
	 * @param authMethod authentication method name, for example {@code token},
	 *                   {@code userpass}, {@code approle}, {@code jwt},
	 *                   {@code github}, or {@code ldap}
	 */
	public VaultSettings(String address, String namespace, String authMethod) {
		this(address, namespace, authMethod, authMethod);
	}
	
	
	/**
	 * Creates Vault settings.
	 *
	 * @param address    Vault server address, for example
	 *                   {@code http://127.0.0.1:8200}
	 * @param namespace  optional Vault Enterprise namespace, or {@code null} when
	 *                   not used
	 * @param authMethod authentication method name, for example {@code token},
	 *                   {@code userpass}, {@code approle}, {@code jwt},
	 *                   {@code github}, or {@code ldap}
	 * @param authPath   optional authentication mount path; when blank, the default
	 *                   mount for the authentication method is used by the
	 *                   authenticator
	 */
	public VaultSettings(String address, String namespace, String authMethod, String authPath) {
		this.address = address;
		this.namespace = namespace;
		this.authMethod = authMethod;
		this.authPath = authPath;
	}

	/**
	 * Returns the Vault server address.
	 *
	 * @return Vault server address
	 */
	public String address() {
		return address;
	}

	/**
	 * Returns the optional Vault Enterprise namespace.
	 *
	 * @return namespace value, or {@code null} when no namespace is configured
	 */
	public String namespace() {
		return namespace;
	}

	/**
	 * Returns the configured authentication method.
	 *
	 * @return authentication method name
	 */
	public String authMethod() {
		return authMethod;
	}

	/**
	 * Returns the configured authentication mount path.
	 *
	 * @return authentication mount path, or {@code null} when the default path
	 *         should be used
	 */
	public String authPath() {
		return authPath;
	}

	/**
	 * Returns a setting from Java system properties or environment variables.
	 *
	 * <p>
	 * System properties have priority over environment variables. If neither source
	 * has a non-blank value, the supplied default value is returned.
	 * </p>
	 *
	 * @param name         setting name to resolve
	 * @param defaultValue value to return when the setting is missing or blank
	 * @return resolved setting value or {@code defaultValue}
	 */
	public String get(String name, String defaultValue) {
		String value = get(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	/**
	 * Resolves a setting from Java system properties first, then environment
	 * variables.
	 *
	 * @param name setting name to resolve
	 * @return resolved setting value, or {@code null} when not found
	 */
	public String get(String name) {
		String value = System.getProperty(name);

		if (value == null || value.isBlank()) {
			value = System.getenv(name);
		}

		return value;
	}
}
