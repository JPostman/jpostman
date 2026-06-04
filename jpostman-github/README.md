# JPostman GitHub Integration

`jpostman-github` is a Java module for working with GitHub Actions repository variables and repository secrets.

The module can:

- create, update, read, and delete GitHub Actions repository variables
- parse shell-style variable values such as `export KEY=VALUE`
- create, update, verify metadata for, and delete GitHub Actions repository secrets
- run integration tests against a real GitHub repository

Important distinction:

| GitHub item | Create/update | Read value back | Delete | Notes |
|---|---:|---:|---:|---|
| Repository variable | Yes | Yes | Yes | Good for non-sensitive values |
| Repository secret | Yes | No | Yes | GitHub never returns plaintext secret values |

GitHub repository secrets must be encrypted before they are sent to the REST API. The module uses the repository public key from GitHub to encrypt the secret value before upload.

---

## Why use `github_pat_...` instead of `ghp_...`

GitHub token prefixes usually mean:

```text
github_pat_... = fine-grained personal access token
ghp_...        = classic personal access token
```

For this module, a **fine-grained personal access token** is recommended because you can grant exactly the permissions needed for one repository.

Classic `ghp_...` tokens may not work for these tests because:

- the token may not have access to the selected repository
- the token may not have repository Actions Variables permission
- the token may not have repository Actions Secrets permission
- classic scopes are broader and do not map as clearly to fine-grained repository permissions
- GitHub error messages for Actions variables often specifically mention the required fine-grained permission

Example error when the token does not have the right permissions:

```text
GitHub API request failed. status=403
You must have repository read permissions or have the repository variables fine-grained permission.
```

Use `github_pat_...` unless you have a specific reason to use a classic token.

---

## Create a fine-grained GitHub token

1. Open GitHub.
2. Click your profile picture.
3. Go to **Settings**.
4. Go to **Developer settings**.
5. Go to **Personal access tokens**.
6. Go to **Fine-grained tokens**.
7. Click **Generate new token**.
8. Set **Resource owner**.

Example:

```text
jpostman
```

9. Set **Repository access** to:

```text
Only selected repositories
```

10. Select the repository.

Example:

```text
jpostman/JPostman
```

11. Under **Repository permissions**, set the permissions below.

---

## Required token permissions to pass all tests

To pass all current integration tests, the token needs:

```text
Metadata: Read-only
Variables: Read and write
Secrets: Read and write
```

Use these permissions when running all tests, including secret write/delete tests.

### Why each permission is needed

| Permission | Level | Needed for |
|---|---|---|
| Metadata | Read-only | Basic repository API access |
| Variables | Read and write | create, update, read, and delete repository variables |
| Secrets | Read and write | get public key, create/update secret, read secret metadata, delete secret |

For variable-only tests, you can use:

```text
Metadata: Read-only
Variables: Read and write
```

For secret tests, also add:

```text
Secrets: Read and write
```

---

## Create a GitHub Actions repository variable manually

You can create a variable in the GitHub UI:

1. Open your repository.
2. Go to **Settings**.
3. Go to **Secrets and variables**.
4. Go to **Actions**.
5. Select the **Variables** tab.
6. Click **New repository variable**.
7. Add the variable name and value.

Example variable:

```text
Name:
JPOSTMAN_SCRIPT

Value:
export KEY1=VALUE1
export KEY2=VALUE2
```

The Java service can read this value and parse it into:

```text
KEY1 = VALUE1
KEY2 = VALUE2
```

The tests can also create/update this variable automatically, so manual creation is not required if the token has `Variables: Read and write`.

---

## Required test settings

The integration tests read settings from Java system properties first and then environment variables.

### `GITHUB_TOKEN`

Fine-grained GitHub token.

Example:

```text
github_pat_xxxxxxxxxxxxxxxxx
```

### `GITHUB_OWNER`

GitHub user or organization that owns the repository.

Example:

```text
jpostman
```

### `GITHUB_REPO`

Repository name only.

Example:

```text
JPostman
```

### Secret value cannot be read back

This is expected.

GitHub repository secrets are write-only from the REST API perspective. After a secret is created, GitHub returns only metadata such as name and timestamps. It never returns the plaintext secret value.
