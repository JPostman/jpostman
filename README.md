# JPostman Annotations

[![Java](https://img.shields.io/badge/Java-11%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html)
[![Build](https://github.com/JPostman/jpostman/actions/workflows/build.yml/badge.svg)](https://github.com/JPostman/jpostman/actions/workflows/build.yml)
[![Maven](https://img.shields.io/badge/Maven-3.x-blue)](https://repo1.maven.org/maven2/io/github/jpostman/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.jpostman/jpostman-core)](https://central.sonatype.com/namespace/io.github.jpostman)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/JPostman/jpostman)](https://github.com/JPostman/jpostman/releases)
[![Coverage](https://codecov.io/gh/JPostman/jpostman/branch/main/graph/badge.svg?flag=jpostman-core)](https://app.codecov.io/gh/JPostman/jpostman)
[![License](https://img.shields.io/github/license/JPostman/jpostman)](https://raw.githubusercontent.com/JPostman/jpostman/refs/heads/main/LICENSE)

<a href="https://www.youtube.com/@JPostmanApi"><img src="logo.png" width="100" alt="JPostman logo"></a>

**JPostman** is a lightweight Java helper library that reuses exported **Postman collections** and **Postman environments** directly in Java API tests. It lets you run Postman collection requests from JUnit 5 or TestNG with very little test code. You can start with a single runner method, then add request overrides, dependencies, cached values, assertion rules, session executors, and reports only when you need them.

## Maven Dependency

[JPostmanApi on YouTube](https://www.youtube.com/@JPostmanApi)

[JPostman Wiki](https://github.com/JPostman/jpostman/wiki)

[JPostman API Documentation](https://jpostman.github.io/jpostman/)
```xml
<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-annotations</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-httpclient</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
    <scope>test</scope>
</dependency>
```

Then configure the executor in `@JPostman.Context` or in `jpostman.properties`.

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor"
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

You can also use another executor module if it better matches your project:

- HTTP Client: https://github.com/JPostman/jpostman/tree/main/jpostman-httpclient
- Playwright: https://github.com/JPostman/jpostman/tree/main/jpostman-playwright
- REST Assured: https://github.com/JPostman/jpostman/tree/main/jpostman-restassured
- Unirest: https://github.com/JPostman/jpostman/tree/main/jpostman-unirest


## Executor Configuration

Every `@JPostman.Response` and `@JPostman.Runner` needs an executor. The easiest setup is a context-level executor:

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor"
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

If you forget the executor, JPostman will stop before execution because it does not know how to send the request. Use one of these options:

```java
executor = "io.jpostman.httpclient.HttpClientExecutor"
```

or:

```java
executorClass = io.jpostman.httpclient.HttpClientExecutor.class
```

or define a custom method:

```java
@JPostman.Executor
public ApiExecutor executor(JPostman.Test ctx, JPostman.Info info) {
    return MyExecutor.apply(ctx.request());
}
```

## One Import Style

The compact facade keeps JPostman annotation usage simple:

```java
import io.jpostman.annotations.JPostman;
```

The examples below use only this JPostman import. Test framework annotations are written with fully qualified names so the examples stay focused on the JPostman API.

## Minimal Runner

The smallest useful test is a runner that executes collection requests.

```java
import io.jpostman.annotations.JPostman;

@JPostman.TestNG
public class ApiRunnerTest {

    @JPostman.Context(
            collection = "classpath:DummyJSON.all_product_collection.json",
            executor = "io.jpostman.httpclient.HttpClientExecutor"
    )
    private JPostman.Runtime<JPostman.Test> jpostman;

    @Test
    @JPostman.Runner
    public void runCollection() {
    }
}
```

What this does:

- Loads the Postman collection.
- Uses the configured executor to run requests.
- Lets JPostman handle request execution and status verification.

## Run a Folder

Use `folder` when you want to limit execution to requests inside a specific collection folder. Without `folder`, `@JPostman.Runner` runs matching requests from the collection root.

```java
import io.jpostman.annotations.JPostman;

@JPostman.TestNG
public class ProductRunnerTest {

    @JPostman.Context(
            collection = "classpath:DummyJSON.all_product_collection.json",
            executor = "io.jpostman.httpclient.HttpClientExecutor"
    )
    private JPostman.Runtime<JPostman.Test> jpostman;

    @Test
    @JPostman.Runner(folder = "Product")
    public void runProductFolder() {
    }
}
```

## Run Specific Requests

Use `include` to run only selected requests.

```java
@Test
@JPostman.Runner(
        folder = "Product",
        include = { "Get all products", "Add a new product" }
)
public void runSelectedProductRequests() {
}
```

Use `exclude` when you want to run a folder or collection except for specific requests.

```java
@Test
@JPostman.Runner(
        folder = "Product",
        exclude = "Delete a product"
)
public void runProductFolderExceptDelete() {
}
```

## Session Executor

Use `session = true` when your executor supports reusable state, such as cookies, authentication state, or a shared REST Assured session.

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor",
        session = true
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

With `session = true`, JPostman creates the context executor once and reuses it for each request. Before each request, JPostman updates the executor with the current request.

Use this when your API flow depends on state shared across requests. Leave it as `false` when each request should be independent.

## Override Verification Status Code

The context can define the default status code:

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor",
        verifyStatusCode = 200
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

Then individual runners or responses can override it:

```java
@Test
@JPostman.Response(
        folder = "Product",
        request = "Add a new product",
        dependsOn = "prepareProduct",
        verify = 201
)
public void addProduct() {
}
```

Use this when most requests return `200`, but create operations return `201`, delete operations return `200` or `204`, or error tests expect a different status.

## Skipping Tests

Use `skip = true` to disable a response test.

```java
@Test
@JPostman.Response(
        request = "Get current auth user",
        skip = true
)
public void disabledUserTest() {
}
```

Use `skipReason` when you want the reason to appear in test output. A non-empty `skipReason` also means the response is skipped, so `skip = true` is not required.

```java
@Test
@JPostman.Response(
        request = "Get current auth user",
        skipReason = "Temporarily disabled while testing session executor"
)
public void disabledWithReason() {
}
```

Use `skipAll = true` to disable all response and runner tests by default.

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor",
        skipAll = true
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

When `skipAll = true` is set on the context, all `@JPostman.Response` and `@JPostman.Runner` methods are skipped by default. Use `enabled = true` on a specific response when you want that method to run anyway.

```java
@Test
@JPostman.Response(
        request = "Get current auth user",
        enabled = true
)
public void runEvenWhenSkipAllIsEnabled() {
}
```

Do not define `enabled` and `skip` or `skipReason` on the same annotation.

## Report Summary

Inject `@JPostman.ReportContext` to print an execution summary.

```java
@JPostman.ReportContext
private JPostman.Report report;

@org.testng.annotations.AfterClass
public void afterAllTests() {
    report.summary();
}
```

## JUnit 5

Use `@JPostman.JUnit` instead of `@JPostman.TestNG` when running with JUnit 5.

```java
import io.jpostman.annotations.JPostman;

@JPostman.JUnit(printFailures = true)
public class UserApiJUnitTest {

    @JPostman.Context(
            collection = "classpath:DummyJSON.all_product_collection.json",
            executor = "io.jpostman.httpclient.HttpClientExecutor",
            session = true
    )
    private JPostman.Runtime<JPostman.Test> jpostman;

    @Test
    @JPostman.Response(request = "Get current auth user")
    public void getCurrentUser() {
    }
}
```

`@JPostman.JUnit` uses per-class lifecycle, so injected fields can be used from non-static `@BeforeAll` and `@AfterAll` methods.

## `jpostman.properties`

By default, context configuration can be loaded from:

```text
classpath:jpostman.properties
```

If this file exists, `@JPostman.Context` loads it automatically. This lets you keep shared setup such as `collection`, `executor`, `rules`, `dataload`, and `assertions` in one place instead of copying it into every test class.

Example:

```properties
collection=classpath:DummyJSON.all_product_collection.json
environment=classpath:DummyJSON.postman_environment.json
executor=io.jpostman.httpclient.HttpClientExecutor
rules=classpath:demo_test_rule.ini
dataload=classpath:product-data.ini
assertions=classpath:assertions.ini
```

Then the test can use only the default context annotation:

```java
@JPostman.Context
private JPostman.Runtime<JPostman.Test> jpostman;
```

Namespace-specific values are also supported:

```properties
collection.product=classpath:Product.postman_collection.json
dataload.product=classpath:product-data.ini
```

Then use the namespace in annotations:

```java
@Test
@JPostman.Runner(namespace = "product", folder = "Product")
public void runProductNamespace() {
}
```

You can also access a namespace through the runtime facade:

```java
jpostman.ctx("product").response().print();
```

## Rules File

JPostman can load reusable secure rules from a rules file:

```properties
rules=classpath:demo_test_rule.ini
```

Rules are useful for shared response cleanup, such as redacting sensitive values, filtering noisy fields, and keeping logs easier to read.

Example `demo_test_rule.ini`:

```ini
[default]
unsecret=base_url
redact=email
headersFilter=Date

[user]
extends=default
redact=phone[:3],/address/address
redact=ip,bloodGroup,height,weight,eyeColor,/hair

[product]
extends=default
redact=/**/tags,/**/dimensions,/**/meta,/**/images,thumbnail
filter=id,title,description,price,/**/reviews
filterList=/**/reviews[0],/**/reviews/*/rating,/**/reviews/*/reviewerName,/**/reviews/*/comment
```

Then select a rule section from a response or runner:

```java
@Test
@JPostman.Response(
        request = "Get current auth user",
        rule = "user",
        verify = 200
)
public void getCurrentUser() {
}
```

For more rule examples, see the secure module template:

https://github.com/JPostman/jpostman/blob/main/jpostman-secure/src/test/resources/secure-rules.ini

## Use Data Files

Load reusable request data files at the context level with `dataload`.

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor",
        dataload = "classpath:product-data.ini"
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

Then select a data section from a request, response, or runner with `data`.

```java
@JPostman.Request(
        folder = "Product",
        request = "Add a new product",
        data = "product"
)
public void prepareProduct() {
}

@Test
@JPostman.Response(tags = "mouse", dependsOn = "prepareProduct", verify = 201)
public void addMouse() {
}
```

Naming rule:

```text
dataload = loads INI data files at context setup time
data     = selects a section from loaded data files
```

For more dataload examples, see:

https://github.com/JPostman/jpostman/blob/main/jpostman-annotations/src/test/resources/templates/annotation-dataload.ini

## Assertion Rules

Load reusable assertion files from the context with `assertions`.

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.httpclient.HttpClientExecutor",
        assertions = "classpath:assertions.ini"
)
private JPostman.Runtime<JPostman.Test> jpostman;
```

Then select assertion sections from a response or runner with `asserts`.

```java
@Test
@JPostman.Runner(
        folder = "Product",
        include = "Get all products",
        asserts = "product",
        soft = true
)
public void verifyProducts() {
}
```

Naming rule:

```text
assertions = loads assertion INI files at context setup time
asserts    = selects assertion sections from loaded assertion files
```

For more assertion examples, see:

https://github.com/JPostman/jpostman/blob/main/jpostman-annotations/src/test/resources/templates/annotation-assertions.ini

## Authentication or Header Preparation

Use `@JPostman.Request` when a test needs to update the next request before execution.

```java
@JPostman.Request
public void applyAuth(JPostman.Info info) {
    info.sauth("oauth2", "ACCESS_TOKEN_VALUE");
}

@Test
@JPostman.Response(
        request = "Get current auth user",
        dependsOn = "applyAuth",
        verify = 200
)
public void getCurrentUser() {
}
```

For login flows, create a login response dependency, cache the returned value, and then apply it from a request helper. If you need direct cache access in the helper, use the framework-specific context type such as `TestNgContext` or `JUnitContext`. The compact `JPostman.Test` facade is best for simple request/response access with one JPostman import.

### Cache a Login Token

For authentication flows, a login request can run first and cache the returned token. The next request helper can read that cached value and apply it to the request.

```java
@JPostman.Response(
        request = "Login user and get tokens",
        cache = "token"
)
public String login(TestNgContext ctx) {
    return ctx.asserts(true)
            .exists("accessToken", "Access token not found")
            .verify()
            .path("accessToken");
}

@JPostman.Request(dependsOn = "login")
public void applyAuth(TestNgContext ctx, JPostman.Info info) {
    info.sauth("oauth2", ctx.cache("token"));
}

@Test
@JPostman.Response(
        request = "Get current auth user",
        dependsOn = "applyAuth",
        verify = 200
)
public void getCurrentUser() {
}
```

Both `@JPostman.Request` and `@JPostman.Response` can use the `cache` attribute for one-time setup steps such as login, token creation, or preparing shared data.
If the method returns a value, JPostman stores the returned value in the cache. When `cache` is empty, the method name is used as the cache key.
For `void` methods, use `cache = ""` when you want the method to participate in the one-time cache/dependency flow, then store values manually in the context.

## Use Tags to Reuse One Request Helper for Multiple Test Cases

Tags let one request helper prepare different data for different response tests.

```java
@JPostman.Request(
        folder = "Product",
        request = "Add a new product"
)
public void prepareProduct(JPostman.Info info) {
    info.tags()
            .has("mouse").then(i -> i.body(
                    "title", "Wireless Mouse",
                    "price", 25,
                    "stock", 120
            ))
            .has("keyboard").then(i -> i.body(
                    "title", "Gaming Keyboard",
                    "price", 75,
                    "stock", 60
            ));
}

@Test
@JPostman.Response(tags = "mouse", dependsOn = "prepareProduct", verify = 201)
public void addMouse() {
}

@Test
@JPostman.Response(tags = "keyboard", dependsOn = "prepareProduct", verify = 201)
public void addKeyboard() {
}
```

Each response method calls the same request helper, but the tag controls which body values are applied.


## Common Rules

- Use `executor = "fully.qualified.ClassName"` for string executor configuration.
- If a string executor value ends with `.class`, JPostman normalizes the suffix automatically.
- Use `executorClass = SomeExecutor.class` when you want real Java class syntax.
- Use `session = true` only when the executor supports reusable state.
- Use `@JPostman.Request` to prepare or modify a request.
- Use `@JPostman.Response` to execute one request and handle its response.
- Use `@JPostman.Runner` to execute multiple collection requests.
- Use `rules` to load secure rules and `rule` to select a rule section.
- Use `dataload` to load data files and `data` to select a section.
- Use `assertions` to load assertion files and `asserts` to select sections.


## Final Example: Custom Product Preload and Shared INI Data

Sometimes a collection request is reusable, but each test needs different body, header, path, query, or auth values. You can define those values directly in Java with `JPostman.Info`, or move them into a shared INI file and select them with `data`.

### Option 1: Preload values in Java

This example uses one request helper and tags to prepare different product payloads. The comments show the matching INI property that can replace each Java line later.

```java
import io.jpostman.annotations.JPostman;

@JPostman.TestNG
public class ProductPreloadTest {

    @JPostman.Context
    private JPostman.Runtime<JPostman.Test> jpostman;

    @JPostman.Request(
            namespace = "product",
            folder = "Product",
            request = "Add a new product"
    )
    public void prepareProduct(JPostman.Info info) {
        info.tags()
                                                               // [product.category]
                .has("mouse", "keyboard").then(i -> {          // tags = mouse, keyboard
                    i.body("category", "electronics");         // body.category = electronics
                })
                                                               // [product.discount]
                .any("mouse", "shoes").then(i -> {             // anyTags = mouse, shoes
                    i.body("productDiscount", 15);             // body.productDiscount = 15
                })
                                                               // [product.mouse]
                .has("mouse").then(i -> {                      // tags = mouse
                    i.sbody("title", "Wireless Mouse",         // sbody.title = Wireless Mouse
                            "description",                     // sbody.description = A simple wireless mouse
                            "A simple wireless mouse",
                            "price", 25,                       // sbody.price = 25
                            "stock", 120,                      // sbody.stock = 120
                            "rating", 4.3,                     // sbody.rating = 4.3
                            "brand", "Logitech");              // sbody.brand = Logitech
                })
                                                               // [product.keyboard]
                .has("keyboard").then(i -> {                   // tags = keyboard
                    i.spath("hello", "world");                 // spath.hello = world
                    i.sheaders("hello", "world");              // sheaders.hello = world
                    i.sbody("productTitle", "Gaming Keyboard", // sbody.productTitle = Gaming Keyboard
                            "productDesc",                     // sbody.productDesc = Mechanical keyboard with RGB
                            "Mechanical keyboard with RGB",
                            "productPrice", 75,                // sbody.productPrice = 75
                            "productDiscount", 10,             // sbody.productDiscount = 10
                            "productStock", 60,                // sbody.productStock = 60
                            "productRating", 4.6,              // sbody.productRating = 4.6
                            "productBrand", "Razer");          // sbody.productBrand = Razer
                })
                                                               // [product.shoes]
                .has("shoes").then(i -> {                      // tags = shoes
                    i.body("productTitle", "Running Shoes",    // body.productTitle = Running Shoes
                            "productDesc",                     // body.productDesc = Comfortable running shoes
                            "Comfortable running shoes",
                            "productPrice", 90,                // body.productPrice = 90
                            "productStock", 45,                // body.productStock = 45
                            "productRating", 4.4,              // body.productRating = 4.4
                            "productBrand", "Nike",            // body.productBrand = Nike
                            "productCategory", "sports");      // body.productCategory = sports
                });
    }

    @Test
    @JPostman.Response(tags = "mouse", dependsOn = "prepareProduct", verify = 201)
    public void addMouse() {
    }

    @Test
    @JPostman.Response(tags = "keyboard", dependsOn = "prepareProduct", verify = 201)
    public void addKeyboard() {
    }

    @Test
    @JPostman.Response(tags = "shoes", dependsOn = "prepareProduct", verify = 201)
    public void addShoes() {
    }
}
```

This works well when the setup is small. When the same setup is needed by multiple test classes, move it to a shared data file.

### Option 2: Move preload values to `product-data.ini`

`dataload` loads one or more INI files during context setup. Each section name must be unique across all loaded data files.

```java
@JPostman.Context
private JPostman.Runtime<JPostman.Test> jpostman;
```

Then select the shared data with `data = "product"`:

```java
@JPostman.Request(
        namespace = "product",
        folder = "Product",
        request = "Add a new product",
        data = "product"
)
public void prepareProduct() {
}

@Test
@JPostman.Response(tags = "mouse", dependsOn = "prepareProduct", verify = 201)
public void addMouse() {
}

@Test
@JPostman.Response(tags = "keyboard", dependsOn = "prepareProduct", verify = 201)
public void addKeyboard() {
}

@Test
@JPostman.Response(tags = "shoes", dependsOn = "prepareProduct", verify = 201)
public void addShoes() {
}
```

The data file can contain shared sections and tag-specific sections:

```ini
########################## PRODUCT ##########################

[product.category]
tags = mouse, keyboard
body.category = electronics

[product.discount]
anyTags = mouse, shoes
body.productDiscount = 15

[product.mouse]
tags = mouse
sbody.title = Wireless Mouse
sbody.description = A simple wireless mouse
sbody.price = 25
sbody.stock = 120
sbody.rating = 4.3
sbody.brand = Logitech

[product.keyboard]
tags = keyboard
spath.hello = world
sheaders.hello = world
sbody.productTitle = Gaming Keyboard
sbody.productDesc = Mechanical keyboard with RGB
sbody.productPrice = 75
sbody.productDiscount = 10
sbody.productStock = 60
sbody.productRating = 4.6
sbody.productBrand = Razer

[product.shoes]
tags = shoes
body.productTitle = Running Shoes
body.productDesc = Comfortable running shoes
body.productPrice = 90
body.productStock = 45
body.productRating = 4.4
body.productBrand = Nike
body.productCategory = sports
```

Using the same `product-data.ini` file removes repeated preload code from every test class. You can update the product payload in one place and reuse it across TestNG and JUnit tests.

### `body` vs `sbody`, `headers` vs `sheaders`, and other secure helpers

JPostman provides normal and secure value helpers:

| Helper | Secure helper | Target |
| --- | --- | --- |
| `body(...)` | `sbody(...)` | Request body values |
| `query(...)` | `squery(...)` | Query string values |
| `headers(...)` | `sheaders(...)` | Header values |
| `path(...)` | `spath(...)` | Path variables |
| `auth(...)` | `sauth(...)` | Authentication values |

Use the normal helper when the value can safely appear in logs. Use the secure helper when the value should be masked or treated as sensitive in JPostman secure output.

