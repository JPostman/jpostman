package io.jpostman.github;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Integration tests for GitHub Actions repository variables and secrets.
 *
 * <p>
 * API tests are skipped unless the required GitHub settings are available.
 * </p>
 */
public class GitHubServiceIT {

	private static final String DEFAULT_VARIABLE_NAME = "JPOSTMAN_TEST_VARIABLE";
	private static final String DEFAULT_SCRIPT_VARIABLE_NAME = "JPOSTMAN_SCRIPT";

	private Properties githubProperties;
	private GitHubService service;
	private String owner;
	private String repo;
	private String variableName;
	private String scriptVariableName;

	@BeforeClass
	public void loadProperties() throws Exception {
		githubProperties = loadGithubLocalProperties();
		copyToSystemPropertiesIfMissing(githubProperties);

		String token = requiredOrSkip("GITHUB_TOKEN");
		owner = requiredOrSkip("GITHUB_OWNER");
		repo = requiredOrSkip("GITHUB_REPO");
		variableName = optional("GITHUB_VARIABLE_NAME", DEFAULT_VARIABLE_NAME);
		scriptVariableName = optional("GITHUB_SCRIPT_VARIABLE_NAME", DEFAULT_SCRIPT_VARIABLE_NAME);

		service = new GitHubService(token);
	}

	@AfterClass(alwaysRun = true)
	public void cleanup() throws Exception {
		if (service == null || owner == null || repo == null) {
			return;
		}

		service.deleteRepositoryVariable(owner, repo, variableName);
		service.deleteRepositoryVariable(owner, repo, scriptVariableName);
		service.deleteRepositorySecret(owner, repo, requiredOrSkip("GITHUB_SECRET_NAME"));
	}

	private static Properties loadGithubLocalProperties() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = GitHubServiceIT.class.getClassLoader()
				.getResourceAsStream("github-local.properties")) {
			if (input != null) {
				properties.load(input);
			}
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
	public void canParseShellExports() {
		String script = "" + "export KEY1=VALUE1\n" + "export KEY2=\"VALUE 2\"\n" + "# ignored comment\n";

		Map<String, String> values = GitHubService.parseShellExports(script);

		assertEquals(values.get("KEY1"), "VALUE1");
		assertEquals(values.get("KEY2"), "VALUE 2");
	}

	@Test
	public void canDeleteRepositoryVariable() throws Exception {
		service.createOrUpdateRepositoryVariable(owner, repo, variableName, "delete-test-value");

		assertTrue(service.repositoryVariableExists(owner, repo, variableName),
				"GitHub variable should exist before delete");

		service.deleteRepositoryVariable(owner, repo, variableName);

		assertFalse(service.repositoryVariableExists(owner, repo, variableName),
				"GitHub variable should not exist after delete");
	}

	@Test(dependsOnMethods = "canDeleteRepositoryVariable")
	public void canCreateAndReadRepositoryVariable() throws Exception {
		String variableValue = "jpostman-" + Instant.now().toEpochMilli();

		service.createOrUpdateRepositoryVariable(owner, repo, variableName, variableValue);
		String value = service.readRepositoryVariable(owner, repo, variableName);

		assertEquals(value, variableValue);
	}

	@Test
	public void canCreateUpdateAndReadRepositoryShellExports() throws Exception {
		String script = "" + "export KEY1=VALUE1\n" + "export KEY2=VALUE2\n";

		service.createOrUpdateRepositoryVariable(owner, repo, scriptVariableName, script);
		Map<String, String> values = service.readRepositoryShellExports(owner, repo, scriptVariableName);

		assertEquals(values.get("KEY1"), "VALUE1");
		assertEquals(values.get("KEY2"), "VALUE2");
	}

	@Test
	public void canReadRepositorySecretPublicKey() throws Exception {
		GitHubPublicKey publicKey = service.readRepositorySecretPublicKey(owner, repo);

		assertNotNull(publicKey.keyId(), "GitHub secret public key id should not be null");
		assertNotNull(publicKey.key(), "GitHub secret public key should not be null");
	}

	@Test
	public void canCreateOrUpdateRepositorySecretWithPlainValue() throws Exception {
		String secretName = requiredOrSkip("GITHUB_SECRET_NAME");
		String secretValue = requiredOrSkip("GITHUB_SECRET_VALUE");

		service.createOrUpdateRepositorySecret(owner, repo, secretName, secretValue);

		assertTrue(service.repositorySecretExists(owner, repo, secretName), "GitHub secret metadata should exist");

		GitHubSecretMetadata metadata = service.readRepositorySecret(owner, repo, secretName);

		assertEquals(metadata.getName(), secretName);
		assertNotNull(metadata.getCreatedAt());
		assertNotNull(metadata.getUpdatedAt());
	}

	private static String requiredOrSkip(String name) {
		String value = optional(name, null);

		if (value == null || value.isBlank()) {
			throw new SkipException("Skipping GitHub API test. Missing setting: " + name);
		}

		return value;
	}

	private static String optional(String name, String defaultValue) {
		String value = System.getProperty(name);

		if (value == null || value.isBlank()) {
			value = System.getenv(name);
		}

		return value == null || value.isBlank() ? defaultValue : value;
	}
}
