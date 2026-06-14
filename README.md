# JPostman

[![Java](https://img.shields.io/badge/Java-11%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html)
[![Build](https://github.com/JPostman/jpostman/actions/workflows/build.yml/badge.svg)](https://github.com/JPostman/jpostman/actions/workflows/build.yml)
[![Maven](https://img.shields.io/badge/Maven-3.x-blue)](https://repo1.maven.org/maven2/io/github/jpostman/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jpostman/jpostman-core)](https://central.sonatype.com/namespace/io.github.jpostman)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/JPostman/jpostman)](https://github.com/JPostman/jpostman/releases)
[![Coverage](https://codecov.io/gh/JPostman/jpostman/branch/main/graph/badge.svg?flag=jpostman-core)](https://app.codecov.io/gh/JPostman/jpostman)
[![License](https://img.shields.io/github/license/JPostman/jpostman)](https://raw.githubusercontent.com/JPostman/jpostman/refs/heads/main/LICENSE)

<a href="https://www.youtube.com/@JPostmanApi"><img src="logo.png" width="100" alt="JPostman logo"></a>

---

[JPostmanApi on YouTube](https://www.youtube.com/@JPostmanApi)

[JPostman Wiki](https://github.com/JPostman/jpostman/wiki)

[JPostman API Documentation](https://jpostman.github.io/jpostman/)

**JPostman** is a lightweight Java helper library that reuses exported **Postman collections** and **Postman environments** directly in Java API tests.

Instead of copying request URLs, headers, authentication, query parameters, and request bodies into Java code, JPostman keeps Postman as the source of truth. Export the collection and environment, load them in Java, override only what your test needs, resolve Postman-style templates, and execute the final request with the executor you prefer.

---

## Modules

This repository is one GitHub project with multiple Maven modules:

|  |  |
|---|---|
| [`jpostman-core/`](https://jpostman.github.io/jpostman/io/jpostman/package-summary.html) | Framework-neutral parser, model, templates, and `ApiResponse` |
| [`jpostman-secure/`](https://jpostman.github.io/jpostman/io/jpostman/secure/package-summary.html) | Secret-safe request and response helpers |
| [`jpostman-testng/`](https://jpostman.github.io/jpostman/io/jpostman/testng/package-summary.html) | TestNG context, secure assertions, response verification, and reusable cache helpers |
| [`jpostman-junit/`](https://jpostman.github.io/jpostman/io/jpostman/junit/package-summary.html) | JUnit 5 context, secure assertions, response verification, and failure printing support |
| [`jpostman-httpclient/`](https://jpostman.github.io/jpostman/io/jpostman/executor/HttpClientExecutor.html) | Optional Java 11 HttpClient executor |
| [`jpostman-restassured/`](https://jpostman.github.io/jpostman/io/jpostman/restassured/RestAssuredExecutor.html) | Optional REST Assured executor adapter |
| [`jpostman-playwright/`](https://jpostman.github.io/jpostman/io/jpostman/playwright/PlaywrightExecutor.html) | Optional Playwright APIRequestContext executor adapter |
| [`jpostman-unirest/`](https://jpostman.github.io/jpostman/io/jpostman/unirest/UnirestExecutor.html) | Optional Unirest executor adapter |
| [`jpostman-vault/`](https://jpostman.github.io/jpostman/io/jpostman/vault/package-summary.html) | Vault authentication and secret loading helpers |
| [`jpostman-github/`](https://jpostman.github.io/jpostman/io/jpostman/github/package-summary.html) | GitHub Actions variable and secret integration utilities |
| [`jpostman-kubernetes/`](https://jpostman.github.io/jpostman/io/jpostman/kubernetes/package-summary.html) | Kubernetes ConfigMap and Secret loading helpers |
| [`jpostman-examples/`](https://github.com/JPostman/jpostman/tree/main/jpostman-examples/src/test/java/io/jpostman) | Sample TestNG tests; not published to Maven Central |
---

## Exporting from Postman

Export your Postman collection and environment, then place them under your test resources:

```text
src/test/resources/DummyJSON.postman_collection.json
src/test/resources/DummyJSON.postman_environment.json
```

### Export Postman Collection and Environment

Watch this short video showing how to export a Postman collection and environment:

<a href="https://www.youtube.com/watch?v=UxFjeONEq60" target="_blank">
  <img src="https://img.youtube.com/vi/UxFjeONEq60/maxresdefault.jpg" alt="Export Postman collection and environment" width="640">
</a>

---

## Basic Usage

```java
Collection collection = Collection.load(
        getClass().getClassLoader().getResourceAsStream("DummyJSON.postman_collection.json"));

Environment environment = Environment.load(
        getClass().getClassLoader().getResourceAsStream("DummyJSON.postman_environment.json"));

Request template = collection.getRequest("Login user and get tokens");
Request request = template.builder().build(environment);
```

`build(environment)` resolves remaining `{{variable}}` templates using enabled environment values.

---

## Execute with Java HttpClient

```java
import io.jpostman.ApiResponse;
import io.jpostman.executor.HttpClientExecutor;

ApiResponse response = HttpClientExecutor.execute(request);

int status = response.statusCode();
String token = response.path("accessToken");
```

For a shared cookie/session flow:

```java
HttpClientExecutor executor = HttpClientExecutor.create();

ApiResponse login = executor.setRequest(loginRequest).response();
ApiResponse user = executor.setRequest(userRequest).response();
```

---

## Execute with REST Assured

Use JPostman's framework-neutral response:

```java
import io.jpostman.ApiResponse;
import io.jpostman.restassured.RestAssuredExecutor;

ApiResponse response = RestAssuredExecutor
        .apply(request)
        .auth()
        .oauth2(token)
        .response();
```

Or keep the native REST Assured style:

```java
import static io.restassured.RestAssured.given;
import io.jpostman.restassured.RestAssuredExecutor;

Response response = RestAssuredExecutor.execute(request, given())
        .then()
        .statusCode(200)
        .extract()
        .response();
```

---

## Execute with Playwright

```java
import io.jpostman.ApiResponse;
import io.jpostman.playwright.PlaywrightExecutor;

try (PlaywrightExecutor executor = new PlaywrightExecutor()) {
    ApiResponse response = executor.execute(request);
}
```

---

## Fluent TestNG and JUnit Contexts

JPostman TestNG and JUnit contexts can keep request setup, response execution, assertions, verification, and cached values in one fluent flow.

### TestNG example

```java
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.jpostman.Collection;
import io.jpostman.JPostman;
import io.jpostman.JPostman.Context;
import io.jpostman.restassured.RestAssuredExecutor;
import io.jpostman.testng.TestNgContext;

public class DemoTestNgTest {

    private Collection col;
    private TestNgContext base;

    @BeforeClass
    public void init() throws Exception {
        Context ctx = JPostman.load(getClass().getResourceAsStream("DummyJSON.postman_collection.json"),
                getClass().getResourceAsStream("DummyJSON.postman_environment.json"));

        col = ctx.getCollection(); // Load Postman collection
        base = TestNgContext.create().secret(ctx.getEnvironment()) // Protect environment values
                .load(getClass().getResourceAsStream("demo_test_rule.ini")); // Load masking rules
    }

    private String accessToken() {
        return base.cache(() -> { // Cache token for reuse
            TestNgContext ctx = base.request(col.getRequest("Login user and get tokens"))
                    .response(c -> RestAssuredExecutor.execute(c.request()))
                    .asserts().exists("accessToken", "Access token not found")
                    .verify(); // Verify status 200 by default
            return String.valueOf(ctx.path("accessToken"));
        });
    }

    @Test
	public void getAccessToken() {
		accessToken();
	}
}
```

### JUnit example

```java
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.jpostman.Collection;
import io.jpostman.JPostman;
import io.jpostman.JPostman.Context;
import io.jpostman.restassured.RestAssuredExecutor;
import io.jpostman.junit.JPostmanJUnit;
import io.jpostman.junit.JUnitContext;

@JPostmanJUnit(printFailures = true) // Use PER_CLASS lifecycle and print assertion failures
public class DemoJUnitTest {

    private Collection col;
    private JUnitContext base;

    @BeforeAll
    public void init() throws Exception {
        Context ctx = JPostman.load(getClass().getResourceAsStream("DummyJSON.postman_collection.json"),
                getClass().getResourceAsStream("DummyJSON.postman_environment.json"));

        col = ctx.getCollection(); // Load Postman collection
        base = JUnitContext.create().secret(ctx.getEnvironment()) // Protect environment values
                .load(getClass().getResourceAsStream("demo_test_rule.ini")); // Load masking rules
    }

    private String accessToken() {
        return base.cache(() -> { // Cache token for reuse
            JUnitContext ctx = base.request(col.getRequest("Login user and get tokens"))
                    .response(c -> RestAssuredExecutor.execute(c.request()))
                    .asserts().exists("accessToken", "Access token not found")
                    .verify(); // Verify status 200 by default
            return String.valueOf(ctx.path("accessToken"));
        });
    }

    @Test
	public void getAccessToken() {
		accessToken();
	}
}
```

---

## Supported Postman Request Parts

JPostman parses and applies common Postman request components:

- Collection folders and requests
- URLs and enabled URL query parameters
- Enabled headers
- Auth parameters
- Raw JSON bodies
- Raw text, XML, and template bodies
- Environment variables
- Postman-style template replacement such as `{{base_url}}`, `{{username}}`, `{{password}}`, and `{{accessToken}}`

Disabled Postman headers, query parameters, and environment variables are preserved internally but skipped during normal execution and variable resolution.

---

## Fluent Request Overrides

Override only the values needed for a test:

```java
Request request = template.builder()
        .url(u -> u.set("text", "Hello World"))
        .headers(h -> h.add("X-Test", "123"))
        .auth(a -> a.set("token", "my-token"))
        .body(b -> b.set("username", "emilys"))
        .build(environment);
```

Use `add(...)` to create or overwrite a value. Use `set(...)` when the key must already exist in the Postman export.

Nested style is also supported:

```java
Request request = template.builder()
        .url()
            .set("text", "Hello World")
        .end()
        .build(environment);
```

---

## Body Handling

### JSON body field mutation

Use `body().set(...)` and `body().add(...)` for top-level JSON object fields.

Postman raw JSON body:

```json
{
    "username": "{{username}}",
    "password": "{{password}}"
}
```

Builder:

```java
Request request = template.builder()
        .body()
            .set("username", "emilys")
            .add("age", 21)
        .build(environment);
```

Final body:

```json
{
    "username": "emilys",
    "password": "resolved-from-environment",
    "age": 21
}
```

### Deferred JSON body mutation

JPostman can queue `body().set(...)` and `body().add(...)` when the raw body is not valid JSON yet because of an unquoted template token.

Postman raw body:

```json
{
    "username": {{TOKEN}},
    "password": "{{password}}"
}
```

Builder:

```java
Request request = template.builder()
        .body()
            .set("password", "emilyspass")
            .add("age", 21)
            .json("TOKEN", "emilys")
        .build();
```

Final body:

```json
{
    "username": "emilys",
    "password": "emilyspass",
    "age": 21
}
```

If the body never becomes a JSON object, `add(...)` or `set(...)` throws an error explaining that a JSON object body is required.

### Raw text and XML body templates

For raw text or XML bodies, use template resolution instead of JSON field mutation.

```xml
<id>{{USER_ID}}</id>
```

```java
Environment environment = new Environment("Test Env")
        .builder()
        .add("USER_ID", "42")
        .end();

Request request = template.builder().build(environment);
```

Resolved body:

```xml
<id>42</id>
```

`body().set(...)` means “update a JSON object field,” not “replace any template variable.”

---

## Local Template Values with `map(...)` and `json(...)`

Local request-part values are resolved before `build(environment)`, so they have higher priority than environment values. Tokens not provided locally remain available for final environment resolution.

Use `map(...)` for normal template replacement:

```java
Request request = template.builder()
        .url()
            .set("q", "find")
            .map("TOKEN", "login")
        .build(environment);
```

For JSON bodies, use `map(...)` when the placeholder is already inside quotes:

```json
{
    "age": "{{age}}"
}
```

```java
Request request = template.builder()
        .body()
            .map("age", 25)
        .build();
```

Final body:

```json
{
    "age": "25"
}
```

Use `json(...)` when a raw JSON body has unquoted template placeholders and string values must become JSON-safe strings:

```json
{
    "username": {{username}},
    "age": {{age}},
    "active": {{active}}
}
```

```java
Request request = template.builder()
        .body()
            .json("username", "emmy", "age", 25, "active", true)
        .build();
```

Final body:

```json
{
    "username": "emmy",
    "age": 25,
    "active": true
}
```

Rule of thumb:

```json
"username": "{{username}}"
```

Use `map(...)`.

```json
"username": {{username}}
```

Use `json(...)`.

---

## Reusable Variable Helpers

Use `Params.asMap(...)` and `Params.asJson(...)` when you want reusable local variables without Java's `Map.of(...)` entry limit.

```java
Map<String, Object> params = Params.asMap("key", "value");
Map<String, Object> jsonParams = Params.asJson("username", "emmy", "age", 25);
```

Use `Params.asList(...)` for mutable list values and `Params.copy(...)` to merge maps. Later maps override duplicate keys and `null` maps are ignored.

```java
Map<String, Object> merged = Params.copy(defaults, overrides);
List<String> roles = Params.asList("admin", "tester");
```

---

## Enabled vs Raw Parameter Values

Most request builders use enabled values only. Disabled Postman entries are preserved, but skipped during normal request preparation.

Use `get(...)` when you want the active value only:

```java
String token = environment.get("accessToken");
```

Use `raw(...)` when you want the stored value even if the entry is disabled:

```java
String token = environment.raw("accessToken");
```

---

## Troubleshooting

### `Body builder add/set requires a JSON object body`

This means `body().add(...)` or `body().set(...)` was used on a body that did not become a JSON object. For XML/text, use environment or part-level template resolution instead of JSON field mutation.

### `Body key not found: 'KEY'`

This means `body().set("KEY", value)` was used, but the resolved JSON body did not contain that field. Use `add(...)` if you want to create a new field.

### `URL query parameter not found: 'KEY'`

This means `url().set("KEY", value)` was used, but the query parameter does not exist in the Postman URL/query list. Use `url().add(...)` if you want to create a new query parameter.

### Unknown template variables become empty

Final request-level resolution uses Handlebars behavior. If a template variable is missing from the supplied map or environment, it renders as an empty value.

