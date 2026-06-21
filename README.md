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

JPostman annotations make API tests easy to read.

You keep the API request in Postman.  
You export the collection and environment.  
Then your Java test only says what request to run.

No manual collection loading.  
No repeated setup code.  
No duplicated URLs, headers, bodies, or tokens.

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

Add only the collection and environment:

```properties
collection=classpath:DummyJSON.postman_collection.json
environment=classpath:DummyJSON.postman_environment.json
```

That is enough to start.

---

## 2. Simple JUnit Test

This is the smallest annotation example.

```java
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.junit.JPostmanJUnit;
import io.jpostman.junit.JUnitContext;
import io.jpostman.ApiExecutor;
import io.jpostman.restassured.RestAssuredExecutor;

@JPostmanJUnit
public class DemoJUnitTest {

    @JPostmanContext
    private JUnitContext hello;

    @JPostmanExecutor
	public ApiExecutor defaultExecutor(JUnitContext ctx) {
		return RestAssuredExecutor.apply(ctx.request());
	}

    @JPostmanResponse(request = "Get current auth user")
    public void getCurrentAuthUser() {
    }
}
```

The test method is empty because JPostman does the work.

JPostman will:

```text
1. Load the Postman collection
2. Find the request by name
3. Build the request
4. Execute the request
5. Verify the status code
```

---

## 3. Simple TestNG Test

The same idea works with TestNG.

```java
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.testng.JPostmanTestNG;
import io.jpostman.testng.TestNgContext;
import io.jpostman.ApiExecutor;
import io.jpostman.restassured.RestAssuredExecutor;

@JPostmanTestNG
public class DemoTestNgTest {

    @JPostmanContext
    private TestNgContext hello;

    @JPostmanExecutor
	public ApiExecutor defaultExecutor(TestNgContext ctx) {
		return RestAssuredExecutor.apply(ctx.request());
	}

    @JPostmanResponse(request = "Get current auth user")
    public void getCurrentAuthUser() {
    }
}
```

---

## 4. Add Rules Only When Needed

If you want secure filtering or response rules, add a rules file:

```text
src/test/resources/demo_test_rule.ini
```

Then update `jpostman.properties`:

```properties
collection=classpath:DummyJSON.postman_collection.json
environment=classpath:DummyJSON.postman_environment.json
rules=classpath:demo_test_rule.ini
```

Now you can use `rule = "user"` in the test:

```java
@JPostmanResponse(
    request = "Get current auth user",
    rule = "user",
    verify = 200
)
public void getCurrentAuthUser() {
}
```

Rules are optional.  
Use `rule = "..."` only when you want to apply a named rule section.

---

## 5. Get a Value from a Response

Sometimes you need to read a value from one API response and reuse it later.

A common example is a login request that returns an access token.

```java
@JPostmanRequest(request = "Login user and get tokens")
public String getToken() {
    return hello.response(c -> RestAssuredExecutor.execute(c.request()))
            .asserts(true)
                .exists("accessToken", "Access token not found")
                .verify()
            .path("accessToken");
}
```

This method:

```text
1. Loads the Postman request named "Login user and get tokens"
2. Executes it with REST Assured
3. Checks that accessToken exists
4. Verifies the response
5. Returns the accessToken value
```

---

## 6. Cache the Token

Caching is optional. If `cache` is not provided, `JPostman` stores the returned value using the method name.
```java
@JPostmanRequest(request = "Login user and get tokens")
public String getToken() {
    return hello.response(c -> RestAssuredExecutor.execute(c.request()))
            .asserts(true)
                .exists("accessToken", "Access token not found")
                .verify()
            .path("accessToken");
}
```

In this example, the returned token is stored with the default cache key: 
```java
context.cache("getToken")
```

If you want a custom cache key, add `cache = "apiAccessToken"`.
```java
@JPostmanRequest(
    request = "Login user and get tokens",
    cache = "apiAccessToken"
)
public String getToken() {
    return hello.response(c -> RestAssuredExecutor.execute(c.request()))
            .asserts(true)
                .exists("accessToken", "Access token not found")
                .verify()
            .path("accessToken");
}
```

Now the returned token is stored with this cache key:
```java
context.cache("apiAccessToken")
```

Now other requests can reuse the token from the JPostman cache.

---

## 7. Use `dependsOn`

Use `dependsOn` when one API call must run before another.

```java
@JPostmanExecutor(name = "auth", dependsOn ="getToken")
public ApiExecutor authExecutor(JUnitContext ctx) {
    return RestAssuredExecutor.apply(ctx.request()).auth().oauth2(hello.cache("getToken"));
}

@JPostmanResponse(request = "Get current auth user", executor = "auth")
public void getCurrentAuthUser() {
}
```

Before running `getCurrentAuthUser`, JPostman runs `getToken`.

Because `getToken` caches the value as `accessToken`, the next request can use it.

For one dependency, use:

```java
dependsOn = "getToken"
```

For multiple dependencies, use:

```java
dependsOn = { "getToken", "prepareUser" }
```

---

## 8. Add an Executor for Auth

The executor controls how the request is sent.

Here the executor adds the cached access token before sending the request.

```java
@JPostmanExecutor(name = "auth", dependsOn = "getToken")
public ApiExecutor authExecutor(JUnitContext context, String methodName) {
    return RestAssuredExecutor.apply(context.request())
            .auth()
            .oauth2(context.cache("accessToken"));
}
```

The test method stays clean:

```java
@JPostmanResponse(
    folder = "Product", 
    request = "Get all products", 
    rule = "product"
    dependsOn = "getToken",
    executor = "auth",
    verify = 200
)
public void getCurrentAuthUser() {
}
```

---

## 9. Full JUnit Example

```java
import io.jpostman.ApiExecutor;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.junit.JPostmanJUnit;
import io.jpostman.junit.JUnitContext;
import io.jpostman.restassured.RestAssuredExecutor;

@JPostmanJUnit(printFailures = true)
public class DemoJUnitTest {

    @JPostmanContext
    private JUnitContext hello;

    @JPostmanRequest(
        request = "Login user and get tokens",
        cache = "accessToken"
    )
    public String getToken() {
        return hello.response(c -> RestAssuredExecutor.execute(c.request()))
                .asserts(true)
                    .exists("accessToken", "Access token not found")
                    .verify()
                .path("accessToken");
    }

    @JPostmanResponse(
        request = "Get current auth user",
        dependsOn = "getToken",
        executor = "auth",
        verify = 200
    )
    public void getCurrentAuthUser() {
    }

    @JPostmanExecutor(name = "auth", dependsOn = "getToken")
    public ApiExecutor authExecutor(JUnitContext context, String methodName) {
        return RestAssuredExecutor.apply(context.request())
                .auth()
                .oauth2(context.cache("accessToken"));
    }
}
```

---

## 10. Full TestNG Example

```java
import io.jpostman.ApiExecutor;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.testng.JPostmanTestNG;
import io.jpostman.testng.TestNgContext;
import io.jpostman.restassured.RestAssuredExecutor;

@JPostmanTestNG
public class DemoTestNgTest {

    @JPostmanContext
    private TestNgContext hello;

    @JPostmanRequest(
        request = "Login user and get tokens",
        cache = "accessToken"
    )
    public String getToken() {
        return hello.response(c -> RestAssuredExecutor.execute(c.request()))
                .asserts(true)
                    .exists("accessToken", "Access token not found")
                    .verify()
                .path("accessToken");
    }

    @JPostmanResponse(
        request = "Get current auth user",
        dependsOn = "getToken",
        executor = "auth",
        verify = 200
    )
    public void getCurrentAuthUser() {
    }

    @JPostmanExecutor(name = "auth", dependsOn = "getToken")
    public ApiExecutor authExecutor(TestNgContext context, String methodName) {
        return RestAssuredExecutor.apply(context.request())
                .auth()
                .oauth2(context.cache("accessToken"));
    }
}
```

---

## Why This Is Easy

Without annotations, each test needs setup code.

With annotations, the test focuses on the API flow:

```text
Login
Get token
Use token
Call protected API
Verify result
```

JPostman keeps Postman as the source of truth and lets Java tests stay small.
