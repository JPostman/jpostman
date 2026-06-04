package io.jpostman.github;

/**
 * Metadata for a GitHub Actions repository secret.
 *
 * GitHub does not return the secret value. It only returns metadata such as the
 * name and timestamps.
 */
public class GitHubSecretMetadata {

	private final String name;
	private final String createdAt;
	private final String updatedAt;

	public GitHubSecretMetadata(String name, String createdAt, String updatedAt) {
		this.name = name;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public String getName() {
		return name;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}
}