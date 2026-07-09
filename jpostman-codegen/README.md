# JPostman Codegen

`jpostman-codegen` generates Java test method snippets for JPostman annotations.

This module is intentionally separate from the Swagger/OpenAPI/Postman/GraphQL parser module. The parser reads API/schema documents. This module generates Java source snippets that can be pasted into JPostman test classes or written to a file from the CLI.

The generated methods keep test execution control inside JPostman annotations. The CLI does **not** generate TestNG-specific options such as `@Test(dependsOnMethods = ...)`.

## Build

From this module folder:

```bash
cd jpostman-codegen
mvn clean package
```

Run the CLI with:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar help
```

If you add this module to a parent Maven project, add the module name to the parent `pom.xml`:

```xml
<modules>
    <module>jpostman-codegen</module>
</modules>
```

## Generated source assumptions

The generated snippet uses these imports in the destination test class:

```java
import org.junit.jupiter.api.Test;
// or: import org.testng.annotations.Test;

import io.jpostman.annotations.JPostman;
```

The generated method itself uses only plain `@Test`:

```java
@Test
@JPostman.Runner(namespace = "auth", folder = "login")
public void loginUser() {

}
```

## CLI syntax

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar <command> --method <methodName> [options]
```

Available commands:

| Command | Generates |
| --- | --- |
| `runner` | `@Test` + `@JPostman.Runner(...)` |
| `request` | `@Test` + `@JPostman.Request(...)` |
| `response` | `@Test` + `@JPostman.Response(...)` |

General help:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar help
```

Command help:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar help runner
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar help request
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar help response
```

## Common options

| Option | Description |
| --- | --- |
| `--method <name>` | Java method name. Required. |
| `--id <id>` | Optional JPostman annotation id. Useful for `dependsOn = "#id"`. |
| `--tags <a,b>` | Tags. Comma-separated values are supported. Option can also be repeated. |
| `--namespace <name>` | JPostman context namespace. |
| `--folder <name>` | Postman collection folder. |
| `--request <name>` | Postman request name. Used by `request` and `response`. |
| `--rule <name>` | Secure rule section. |
| `--filter <a,b>` | Response fields to keep. Comma-separated values are supported. |
| `--depends-on <a,b>` | JPostman dependencies. Use Java method names or annotation ids like `#login`. |
| `--executor <id>` | JPostman executor id. |
| `--log <debug\|none\|error>` | Local JPostman failure output mode. |
| `--data <section>` | Data section or group. |
| `--skip <true\|false>` | Generate `skip = true` or `skip = false`. |
| `--output <file>` | Write generated source to a file instead of stdout. |
| `--append` | Append to `--output` instead of replacing the file. |

## Runner command

Basic runner:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar runner \
  --namespace auth \
  --folder login \
  --method loginUser
```

Output:

```java
@Test
@JPostman.Runner(namespace = "auth", folder = "login")
public void loginUser() {

}
```

Runner with tags and JPostman dependencies:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar runner \
  --namespace auth \
  --folder login \
  --method loginUser \
  --tags smoke,auth \
  --depends-on getToken \
  --enabled true
```

Output:

```java
@Test
@JPostman.Runner(tags = {"smoke", "auth"}, namespace = "auth", folder = "login", dependsOn = {"getToken"}, enabled = true)
public void loginUser() {

}
```

Runner-specific options:

| Option | Description |
| --- | --- |
| `--include <request1,request2>` | Include only selected request names from the folder. |
| `--exclude <request1,request2>` | Exclude selected request names from the folder. |
| `--verify <statusCode>` | Expected HTTP status code. Use `0` to skip status verification. |
| `--soft <true\|false>` | Enable or disable soft assertion mode. |
| `--lifecycle <true\|false>` | Enable runner lifecycle callbacks. |
| `--asserts <section1,section2>` | Assertion rule sections. |
| `--enabled <true\|false>` | Run this runner even when context `skipAll` is enabled. |

Full runner example:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar runner \
  --method runProducts \
  --id productRunner \
  --tags smoke,products \
  --namespace api \
  --folder Products \
  --include "Get products,Get product by id" \
  --exclude "Delete product" \
  --rule product \
  --filter id,title,price \
  --depends-on loginUser \
  --verify 200 \
  --executor restAssured \
  --log debug \
  --soft true \
  --lifecycle true \
  --data product \
  --asserts product-default \
  --enabled true
```

## Request command

Basic request helper:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar request \
  --namespace auth \
  --folder users \
  --request "Get current auth user" \
  --method prepareCurrentAuthUser
```

Output:

```java
@Test
@JPostman.Request(namespace = "auth", folder = "users", request = "Get current auth user")
public void prepareCurrentAuthUser() {

}
```

Request helper with dependency and cache:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar request \
  --namespace auth \
  --folder users \
  --request "Get current auth user" \
  --method prepareCurrentAuthUser \
  --depends-on loginUser \
  --cache currentUserRequest \
  --log debug
```

Output:

```java
@Test
@JPostman.Request(namespace = "auth", folder = "users", request = "Get current auth user", dependsOn = {"loginUser"}, cache = "currentUserRequest", log = "debug")
public void prepareCurrentAuthUser() {

}
```

Request-specific options:

| Option | Description |
| --- | --- |
| `--request <name>` | Postman request name. |
| `--cache <cacheKey>` | Cache key for the helper dependency. |

`request` does not support `--enabled`, `--verify`, `--soft`, `--asserts`, `--include`, `--exclude`, or `--lifecycle` because those fields are not part of `@JPostman.Request`.

## Response command

Basic response test:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar response \
  --namespace auth \
  --folder users \
  --request "Get current auth user" \
  --method verifyCurrentAuthUser
```

Output:

```java
@Test
@JPostman.Response(namespace = "auth", folder = "users", request = "Get current auth user")
public void verifyCurrentAuthUser() {

}
```

Response with dependency, verification, and assertions:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar response \
  --namespace auth \
  --folder users \
  --request "Get current auth user" \
  --method verifyCurrentAuthUser \
  --depends-on loginUser \
  --verify 200 \
  --asserts current-user \
  --enabled true
```

Output:

```java
@Test
@JPostman.Response(namespace = "auth", folder = "users", request = "Get current auth user", dependsOn = {"loginUser"}, verify = 200, asserts = {"current-user"}, enabled = true)
public void verifyCurrentAuthUser() {

}
```

Response-specific options:

| Option | Description |
| --- | --- |
| `--request <name>` | Postman request name. |
| `--verify <statusCode>` | Expected HTTP status code. Use `0` to skip status verification. |
| `--cache <cacheKey>` | Cache key for the response dependency. |
| `--soft <true\|false>` | Enable or disable soft assertion mode. |
| `--asserts <section1,section2>` | Assertion rule sections. |
| `--enabled <true\|false>` | Run this response even when context `skipAll` is enabled. |

## Writing to a file

Replace file contents:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar runner \
  --namespace auth \
  --folder login \
  --method loginUser \
  --output generated/LoginUser.java
```

Append another generated method:

```bash
java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar response \
  --namespace auth \
  --folder users \
  --request "Get current auth user" \
  --method verifyCurrentAuthUser \
  --output generated/LoginUser.java \
  --append
```

## Fluent builder API

The CLI uses the same model and renderer that can be called from Java code.

Runner builder:

```java
String source = JPostmanSourceBuilder.runner()
        .namespace("auth")
        .folder("login")
        .method("loginUser")
        .tags("smoke", "auth")
        .dependsOn("getToken")
        .enabled(true)
        .build();
```

Request builder:

```java
String source = JPostmanSourceBuilder.request()
        .namespace("auth")
        .folder("users")
        .request("Get current auth user")
        .method("prepareCurrentAuthUser")
        .dependsOn("loginUser")
        .cache("currentUserRequest")
        .build();
```

Response builder:

```java
String source = JPostmanSourceBuilder.response()
        .namespace("auth")
        .folder("users")
        .request("Get current auth user")
        .method("verifyCurrentAuthUser")
        .dependsOn("loginUser")
        .verify(200)
        .asserts("current-user")
        .enabled(true)
        .build();
```
