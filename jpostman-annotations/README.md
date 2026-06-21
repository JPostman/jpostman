# JPostman Annotations

`jpostman-annotations` provides annotation-based API test execution for JPostman.

This module is a convenience layer on top of the existing JPostman JUnit and TestNG modules. It keeps the core JPostman modules clean while adding a shared annotation API and runtime engine for test execution.

## Maven Dependency

```xml
<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-annotations</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
    <scope>test</scope>
</dependency>
```

## Module Purpose

Use this module when you want to write JPostman tests with annotations instead of preparing every request manually in each test method.

The module provides shared annotations:

```java
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanExecutor;
```

## Main Annotations

### `@JPostmanContext`

Injects and prepares a JPostman context.

```java
@JPostmanContext
private JUnitContext base;
```

With namespace:

```java
@JPostmanContext(namespace = "product")
private JUnitContext product;
```

With direct configuration:

```java
@JPostmanContext(
    collection = "classpath:DummyJSON.postman_collection.json",
    environment = "classpath:DummyJSON.postman_environment.json",
    rules = "classpath:demo_test_rule.ini"
)
private JUnitContext base;
```

### `@JPostmanRequest`

Prepares a request and optionally caches the method return value.

```java
@JPostmanRequest(
    request = "Login user and get tokens",
    rule = "login",
    cache = "accessToken"
)
public String login() {
    return base.response(c -> RestAssuredExecutor.execute(c.request()))
            .asserts(true)
            .exists("accessToken", "Access token not found")
            .verify()
            .path("accessToken");
}
```

### `@JPostmanResponse`

Prepares and executes a response using a named executor.

```java
@JPostmanResponse(
    request = "Get current auth user",
    rule = "user",
    dependsOn = "login",
    executor = "auth",
    verify = 200
)
public void getCurrentAuthUser() {
}
```

### `@JPostmanExecutor`

Defines how the request is executed.

```java
@JPostmanExecutor(name = "auth", dependsOn = "login")
public ApiExecutor authExecutor(JUnitContext context, String methodName) {
    return RestAssuredExecutor.apply(context.request())
            .auth()
            .oauth2(context.cache("accessToken"));
}
```

## JUnit Example

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
    private JUnitContext base;

    @JPostmanRequest(
        request = "Login user and get tokens",
        rule = "login",
        cache = "accessToken"
    )
    public String login() {
        return base.response(c -> RestAssuredExecutor.execute(c.request()))
                .asserts(true)
                .exists("accessToken", "Access token not found")
                .verify()
                .path("accessToken");
    }

    @JPostmanResponse(
        request = "Get current auth user",
        rule = "user",
        dependsOn = "login",
        executor = "auth",
        verify = 200
    )
    public void getCurrentAuthUser() {
    }

    @JPostmanExecutor(name = "auth", dependsOn = "login")
    public ApiExecutor authExecutor(JUnitContext context, String methodName) {
        return RestAssuredExecutor.apply(context.request())
                .auth()
                .oauth2(context.cache("accessToken"));
    }
}
```

## TestNG Example

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
    private TestNgContext base;

    @JPostmanRequest(
        request = "Login user and get tokens",
        rule = "login",
        cache = "accessToken"
    )
    public String login() {
        return base.response(c -> RestAssuredExecutor.execute(c.request()))
                .asserts(true)
                .exists("accessToken", "Access token not found")
                .verify()
                .path("accessToken");
    }

    @JPostmanResponse(
        request = "Get current auth user",
        rule = "user",
        dependsOn = "login",
        executor = "auth",
        verify = 200
    )
    public void getCurrentAuthUser() {
    }

    @JPostmanExecutor(name = "auth", dependsOn = "login")
    public ApiExecutor authExecutor(TestNgContext context, String methodName) {
        return RestAssuredExecutor.apply(context.request())
                .auth()
                .oauth2(context.cache("accessToken"));
    }
}
```

## `jpostman.properties`

By default, `@JPostmanContext` looks for:

```text
classpath:jpostman.properties
```

Example:

```properties
collection=classpath:DummyJSON.postman_collection.json
environment=classpath:DummyJSON.postman_environment.json
rules=classpath:demo_test_rule.ini
```

Namespace support:

```properties
collection=classpath:DummyJSON.postman_collection.json
environment=classpath:DummyJSON.postman_environment.json
rules=classpath:demo_test_rule.ini

collection.product=classpath:Product.postman_collection.json
environment.product=classpath:Product.postman_environment.json
rules.product=classpath:product_rules.ini
```

Usage:

```java
@JPostmanContext(namespace = "product")
private JUnitContext product;
```

If a namespace has only a collection and no environment, JPostman uses collection-only loading.

```properties
collection.product=classpath:Product.postman_collection.json
```

Rules are optional. However, if a request or response uses `rule = "..."`, then the matching context must load a rules file containing that section.

## Dependency Flow

Annotation dependencies are handled through method names.

```java
@JPostmanResponse(dependsOn = "login")
public void getCurrentAuthUser() {
}
```

The dependency method must exist and normally should be annotated with `@JPostmanRequest`.

```java
@JPostmanRequest(request = "Login user and get tokens", cache = "accessToken")
public String login() {
    return "...";
}
```

If the dependency returns a value, the runtime caches it. If `cache` is empty, the method name is used as the cache key.

## `dependsOn` Syntax

For one dependency, use the short form:

```java
dependsOn = "login"
```

For multiple dependencies, use array syntax:

```java
dependsOn = { "login", "prepareUser" }
```

`dependsOn` is defined as a `String[]`, but Java annotations allow the short form when there is only one value.


## Executor Flow

`@JPostmanExecutor` can accept either:

```java
public ApiExecutor defaultExecutor(JUnitContext context)
```

or:

```java
public ApiExecutor defaultExecutor(JUnitContext context, String methodName)
```

The second form allows logs to include the current test method name.

```java
@JPostmanExecutor
public ApiExecutor defaultExecutor(JUnitContext context, String methodName) {
    System.out.println(methodName + " >>> " + context.log());
    return RestAssuredExecutor.apply(context.request());
}
```

## License

This module is licensed under the GNU General Public License v3.0.

See [LICENSE.txt](LICENSE.txt).