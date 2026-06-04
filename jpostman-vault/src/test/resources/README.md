# JPostman Vault Local Docker Test Setup

This folder contains the local Docker setup used by the JPostman Vault integration tests.

Supported local auth methods:

- Token
- Userpass
- AppRole
- JWT
- GitHub, optional
- LDAP

This setup is for local development and testing only. Vault dev mode uses in-memory storage and resets when the container is recreated.

---

## Recommended setup: run everything with Ansible

This is the easiest way to set up the full local Vault test environment.

```bash
jpostman-vault$ cd src/test/resources
```

Run the full setup with GitHub auth enabled:

```bash
GITHUB_ORG=<ENTER YOUR GITHUB_ORG> \
GITHUB_USER=<ENTER YOUR GITHUB_USER> \
VAULT_GITHUB_TOKEN=ghp_your_real_token_here \
ansible-playbook ansible-setup.yml
```

---

## Get started without Ansible

Use this section if you do not want to use Ansible or if you want to run each Docker/Vault command manually.

### 1. Download/install and run `local-vault`

Docker downloads the image automatically if it is not installed locally. You can also pull it first:

```bash
docker pull hashicorp/vault
```

Start Vault in the background:

```bash
docker run -d --cap-add=IPC_LOCK -e VAULT_DEV_ROOT_TOKEN_ID=root -e VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200 -p 8200:8200 --name local-vault hashicorp/vault
```

Verify the container is running:

```bash
docker ps --filter name=local-vault
```

Vault UI:

```text
http://localhost:8200
```

Root token:

```text
root
```

---

### 2. Download/install and run `local-openldap`

Docker downloads the image automatically if it is not installed locally. You can also pull it first:

```bash
docker pull osixia/openldap:1.5.0
```

Start OpenLDAP in the background:

```bash
docker run -d --name local-openldap -p 389:389 -e LDAP_ORGANISATION="JPostman" -e LDAP_DOMAIN="jpostman.io" -e LDAP_ADMIN_PASSWORD="adminpass" osixia/openldap:1.5.0
```

This creates:

```text
Base DN:  dc=jpostman,dc=io
Admin DN: cn=admin,dc=jpostman,dc=io
Password: adminpass
```

Verify the container is running:

```bash
docker ps --filter name=local-openldap
```

---

### 3. Go to `src/test/resources`

From your repository root:

```bash
jpostman-vault$ cd src/test/resources
```

All commands below assume you are in this folder.

---

### 4. Create Docker network for Vault and LDAP

Vault must resolve the LDAP container name `local-openldap`. Create a shared Docker network and connect both containers:

```bash
docker network create vault-test-net
docker network connect vault-test-net local-vault
docker network connect vault-test-net local-openldap
```

If Docker says the network already exists or a container is already connected, continue.

Check the network:

```bash
docker network inspect vault-test-net
```

You should see both containers:

```text
"Name": "local-openldap"
"Name": "local-vault"
```

---

### 5. Import the LDAP test user

Copy `testuser.ldif` into the LDAP container:

```bash
docker cp testuser.ldif local-openldap:testuser.ldif
```

Import the LDAP entries from that file:

```bash
docker exec -it local-openldap ldapadd -x -D "cn=admin,dc=jpostman,dc=io" -w adminpass -f testuser.ldif
```

The first command only copies the file. The second command creates the LDAP entries.

If you already imported it before, LDAP may say the entry already exists. That is okay if the user already exists.

---

### 6. Copy the Vault setup script into the Vault container

```bash
docker cp setup-vault-local.sh local-vault:/tmp/setup-vault-local.sh
```

---

### 7. Run the setup script

Choose only one option.

### Option A: Run without GitHub auth

Use this for Token, Userpass, AppRole, JWT, and LDAP tests:

```bash
docker exec -it --user root local-vault sh /tmp/setup-vault-local.sh
```

### Option B: Run with GitHub auth

Use this when you also want GitHub auth configured in Vault:

```bash
docker exec -it --user root -e GITHUB_ORG=<ENTER YOUR GITHUB_ORG> -e GITHUB_USER=<ENTER YOUR GITHUB_USER> local-vault sh /tmp/setup-vault-local.sh
```

`GITHUB_ORG` is the GitHub organization name.

`GITHUB_USER` is the GitHub username to map to the Vault `developer` policy.

The script does not generate a GitHub token. Each developer creates their own PAT locally.

---

### 8. Copy generated properties back to your project

The script writes generated AppRole credentials and JWT into the Vault container:

Copy it back to your local resources folder:

```bash
docker cp local-vault:/tmp/vault-local.generated.properties vault-local.properties
```

`vault-local.properties` should stay local and ignored by Git.

---

### 9. Optional: add GitHub PAT locally

Create your own GitHub Personal Access Token:

```text
GitHub -> Settings -> Developer settings -> Personal access tokens -> Tokens classic
```

Required scope:

```text
read:org
```

Then edit the generated local file:

```text
src/test/resources/vault-local.properties
```

Set:

```properties
VAULT_GITHUB_TOKEN=ghp_your_real_token_here
```

Do not commit this token.

---

### 10. Optional: verify installed Vault auth methods

```bash
docker exec -it --user root local-vault sh
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=root
vault auth list
vault secrets list
```

---

## Run TestNG tests

From the `jpostman-vault` module folder:

```bash
mvn clean test -Dtest=VaultClientFactoryTest
```

Run one test:

```bash
mvn clean test -Dtest=VaultClientFactoryTest#shouldLoginWithCustomVaultAuthenticator
```

---

