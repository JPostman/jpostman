# jpostman-testng

`jpostman-testng` adds TestNG-friendly assertion helpers for JPostman secure API tests.

The module wraps `SecureContext` and uses the secure request/response log as the default assertion message.
This keeps failed API assertions easier to troubleshoot while still masking protected values.

## Dependency

```xml
<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-testng</artifactId>
    <version>1.0.6</version>
</dependency>
```

## Example

```java
@Test
public void getTokens() {
    TestNgContext cxt = secure.request(col.getRequest("Login user and get tokens"))
            .response(RestAssuredExecutor.execute(secure.request()));

    cxt.asserts()
            .statusCode(200)
            .exists("accessToken");

    cxt.secret("accessToken", cxt.response().path("accessToken"));
}
```

## Soft assertions

```java
cxt.soft()
        .statusCode(200)
        .exists("accessToken")
        .pathNotNull("accessToken")
        .assertAll();
```

You can also call `cxt.assertAll()` when using `cxt.soft()`.

## Supported assertion helpers

Hard and soft assertions support:

```java
isEqual(actual, expected)
isNotEqual(actual, expected)
isTrue(condition)
isFalse(condition)
isNull(value)
isNotNull(value)
statusCode(expected)
exists(path)
notExists(path)
pathEquals(path, expected)
pathNotNull(path)
```

Each method also has an overload that accepts a custom message. When a custom message is supplied,
the secure log is appended to it.

## Parent POM

Add this module to the parent `pom.xml`:

```xml
<module>jpostman-testng</module>
```
