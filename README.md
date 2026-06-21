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
| [`jpostman-annotations/`](https://github.com/JPostman/jpostman/tree/main/jpostman-annotations) | Optional annotation-based execution support for JPostman JUnit and TestNG tests |
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

## Basic Usage: JPostman Annotations

JPostman annotations keep API tests focused on the flow, not setup code.

You keep requests in Postman, export the collection and environment, and let JPostman prepare and execute the request from Java.

---

## 1. Add `jpostman.properties`

Place your exported Postman files under test resources:

```text
src/test/resources/DummyJSON.postman_collection.json
src/test/resources/DummyJSON.postman_environment.json
```

Create:

```text
src/test/resources/jpostman.properties
```

Add the collection and environment:

```properties
collection=classpath:DummyJSON.postman_collection.json
environment=classpath:DummyJSON.postman_environment.json
```

Rules are optional. Add them only when you need secure filtering or masking:

```properties
rules=classpath:demo_test_rule.ini
```

classpath: means the file is loaded from the Java test resources folder, usually:

```properties
src/test/resources/demo_test_rule.ini
```

You can also use a regular file path:

```properties
rules=src/test/resources/demo_test_rule.ini
```

or an absolute path:

```properties
rules=/path/to/demo_test_rule.ini
```
---

## 2. Context Annotations

Use `@JPostmanContext` when you need direct access to the loaded Postman collection or environment.

```java
import io.jpostman.JPostman;
import io.jpostman.annotations.JPostmanContext;

@JPostmanContext
private JPostman.Context ctx;
```

Example:

```java
@Test
public void printPostmanFiles() {
    ctx.getCollection().print();
    ctx.getEnvironment().print();
}
```

Use `@JPostmanTestContext` for the active JUnit or TestNG execution context.

```java
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.junit.JUnitContext;

@JPostmanTestContext
private JUnitContext api;
```

The test context is used to execute requests, verify responses, read cache values, and print the active response.

```java
api.ctx().verify().print();
```

---

## 3. Simple JUnit Test

This is the smallest JUnit annotation example.

```java
import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.junit.JPostmanJUnit;
import io.jpostman.junit.JUnitContext;
import io.jpostman.restassured.RestAssuredExecutor;

@JPostmanJUnit
public class DemoJUnitTest {

    @JPostmanTestContext
    private JUnitContext api;

    @JPostmanExecutor
    public ApiExecutor defaultExecutor(JUnitContext context) {
        return RestAssuredExecutor.apply(context.request());
    }

    @JPostmanResponse(request = "Get current auth user")
    @Test
    public void getCurrentAuthUser() {
        api.ctx().print();
    }
}
```

JPostman will load the collection, find the request by name, execute it, and verify the response status.

---

## 4. Simple TestNG Test

The same idea works with TestNG.

```java
import org.testng.annotations.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.restassured.RestAssuredExecutor;
import io.jpostman.testng.JPostmanTestNG;
import io.jpostman.testng.TestNgContext;

@JPostmanTestNG
public class DemoTestNgTest {

    @JPostmanTestContext
    private TestNgContext api;

    @JPostmanExecutor
    public ApiExecutor defaultExecutor(TestNgContext context) {
        return RestAssuredExecutor.apply(context.request());
    }

    @JPostmanResponse(request = "Get current auth user")
    @Test
    public void getCurrentAuthUser() {
        api.ctx().print();
    }
}
```

---

## 5. Rules and Response Filters

Use `rule` when you want to apply a named secure rule section.

```java
@JPostmanResponse(
    request = "Get current auth user",
    rule = "user",
    verify = 200
)
@Test
public void getCurrentAuthUser() {
}
```

Use `filter` when you want to keep only selected fields from the response.

```java
@JPostmanResponse(
    request = "Get current auth user",
    rule = "user",
    filter = { "id", "firstName", "lastName", "gender" },
    verify = 200
)
@Test
public void getCurrentAuthUser() {
}
```

Rules and filters are optional. Use them only when a test needs secure output or a smaller response view.

---

## 6. Executors

Executors control how the prepared request is sent.

Default executor:

```java
@JPostmanExecutor
public ApiExecutor defaultExecutor(JUnitContext context) {
    return RestAssuredExecutor.apply(context.request());
}
```

Named executor with authentication:

```java
@JPostmanExecutor(name = "auth", dependsOn = "getToken")
public ApiExecutor authExecutor(JUnitContext context, String methodName) {
    return RestAssuredExecutor.apply(context.request())
            .auth()
            .oauth2(context.cache("getToken"));
}
```

Then use the executor by name:

```java
@JPostmanResponse(
    request = "Get current auth user",
    executor = "auth",
    verify = 200
)
@Test
public void getCurrentAuthUser() {
}
```

---

## 7. Cache and Dependencies

Use `@JPostmanRequest` for a helper request that returns a value, such as a token.

If `cache` is not provided, JPostman stores the returned value using the method name.

```text
getToken() -> api.cache("getToken")
```

Use a custom cache key when you want a different name.

```java
@JPostmanRequest(
    request = "Login user and get tokens",
    cache = "accessToken"
)
```

Then read it with:

```java
context.cache("accessToken")
```

Use `dependsOn` when one request must run before another.

```java
@JPostmanExecutor(name = "auth", dependsOn = "getToken")
```

Before the `auth` executor runs, JPostman runs `getToken` and stores the returned token in cache.

For multiple dependencies:

```java
dependsOn = { "getToken", "prepareUser" }
```

---

## 8. Full JUnit Example

```java
import org.junit.jupiter.api.Test;

import io.jpostman.ApiExecutor;
import io.jpostman.JPostman;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.junit.JPostmanJUnit;
import io.jpostman.junit.JUnitContext;
import io.jpostman.restassured.RestAssuredExecutor;

@JPostmanJUnit
public class DemoJUnitTest {

    @JPostmanContext
    private JPostman.Context ctx;

    @JPostmanTestContext
    private JUnitContext api;

    @Test
    public void printLoadedContext() {
        ctx.getCollection().print();
        ctx.getEnvironment().print();
    }

    @JPostmanExecutor
    public ApiExecutor defaultExecutor(JUnitContext context) {
        return RestAssuredExecutor.apply(context.request());
    }

    @JPostmanExecutor(name = "auth", dependsOn = "getToken")
    public ApiExecutor authExecutor(JUnitContext context, String methodName) {
        return RestAssuredExecutor.apply(context.request())
                .auth()
                .oauth2(context.cache("getToken"));
    }

    @JPostmanRequest(request = "Login user and get tokens")
    public String getToken() {
        return api.response(c -> RestAssuredExecutor.execute(c.request()))
                .asserts(true)
                    .exists("accessToken", "Access token not found")
                    .verify()
                .path("accessToken");
    }

    @JPostmanResponse(
        request = "Get current auth user",
        rule = "user",
        filter = { "id", "firstName", "lastName", "gender" },
        executor = "auth",
        verify = 200,
        soft = true,
        log = true
    )
    @Test
    public void getCurrentAuthUser() {
        api.ctx().verify().print();
    }

    @JPostmanResponse(
        folder = "Product",
        request = "Get all products",
        rule = "product"
    )
    @Test
    public void getAllProducts() {
        api.ctx().print();
    }
}
```

---

## Why This Is Easy

JPostman keeps Postman as the source of truth and lets Java tests stay small.
