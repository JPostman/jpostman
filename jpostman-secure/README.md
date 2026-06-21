# JPostman Secure

Secret-safe request and response helpers for JPostman.

`jpostman-secure` adds a secure wrapper layer around the core JPostman `Request` and `ApiResponse` models. It helps prevent sensitive values from being printed in logs, debug output, console output, and response logs.

## Why use this module?

The core JPostman module provides `Request` and `ApiResponse` models for parsing, resolving, building, and logging API requests and responses.

`jpostman-secure` adds protection on top of those models.

Benefits:

- Keep real secret values in memory until request build time.
- Mask sensitive values in `toString()`, `toDebugString()`, `print()`, and response logs.
- Reuse the same secure configuration across multiple requests and responses.
- Redact values by key, JSON path, wildcard path, regex, or slice rule.
- Load redaction rules from a simple file.
- Protect values loaded from Vault, GitHub Secrets, Kubernetes Secrets, environment files, or maps.

## Maven dependency

```xml
<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-secure</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
    <scope>test</scope>
</dependency>
```

## Main classes

```text
SecureContext        Reusable secure configuration for many requests/responses
SecureRequest        Secret-safe wrapper around Request
SecureResponse       Secret-safe wrapper around ApiResponse
RedactionPolicy      Rules that define what should be masked
```

## Basic request usage

```java
Map<String, Object> plainEnv = Params.asMap(
        "baseUrl", "https://api.example.com"
);

Map<String, Object> vaultEnv = Params.asMap(
        "accessToken", "real-token",
        "API-KEY", "real-api-key"
);

SecureRequest secure = SecureRequest.from(loginRequest())
        .redactionPolicy(RedactionPolicy.defaults())
        .plain(plainEnv)
        .secret(vaultEnv);

String unresolved = secure.toDebugString();
String resolved = secure.toDebugString(true);

Request request = secure.build();
```

`toDebugString()` keeps templates unresolved but masks hardcoded protected fields:

```text
[POST  ] Login                                    -> {{baseUrl}}/login
Headers:
  Authorization                       = Bearer {{accessToken}}
  API-KEY                             = {{API-KEY}}
  Content-Type                        = application/json

Body: [raw] {
  "username": "sam",
  "password": "********"
}
```

`toDebugString(true)` resolves values first, then masks protected output:

```text
[POST  ] Login                                    -> https://api.example.com/login
Headers:
  Authorization                       = ********
  API-KEY                             = ********
  Content-Type                        = application/json

Body: [raw] {
  "username": "sam",
  "password": "********"
}
```

`build()` returns the real resolved core `Request`:

```java
Request request = secure.build();

assertEquals(request.getHeader().get("Authorization"), "Bearer real-token");
assertEquals(request.getHeader().get("API-KEY"), "real-api-key");
assertEquals(request.toUrl(), "https://api.example.com/login");
```

## Reuse one secure context

Use `SecureContext` when the same secure values and rules should apply to many requests and responses.

```java
SecureContext secure = SecureContext.create()
        .redactionPolicy(RedactionPolicy.defaults())
        .redact("username", "Content-Type[-4:]")
        .plain(plainEnv)
        .secret(vaultEnv);

SecureRequest login = secure.from(loginRequest());
SecureRequest user = secure.from(userRequest());

Request resolvedLogin = login.build();
Request resolvedUser = user.build();
```

## Response usage

```java
SecureResponse secureResponse = SecureResponse.from(response)
        .redactionPolicy(RedactionPolicy.defaults())
        .redact("token", "ssn", "cardNumber[-4:]");

String log = secureResponse.log(true);
```

Example output:

```json
{
  "token": "********",
  "ssn": "********",
  "cardNumber": "********0987"
}
```

## Redaction rules

Supported redaction rules:

```text
username                       -> key rule
Content-Type[-4:]              -> key rule with slice
/products/*/reviews            -> JSON path wildcard rule, * matches one path segment
/**/reviews                    -> JSON path wildcard rule, ** matches any number of path segments
regex:.*token.*                -> regex key rule
regex:/products/\d+/reviews    -> regex JSON path rule
-Authorization                 -> removes Authorization from default redaction
```

## Key rules

A simple key rule masks all values for that key.

```java
SecureResponse.from(response)
        .redact("accessToken", "password", "ssn")
        .log(true);
```

Example:

```json
{
  "accessToken": "********",
  "password": "********",
  "ssn": "********"
}
```

## Slice rules

Slice rules use Python-style slicing to keep part of a value visible.

```java
SecureResponse.from(response)
        .redact("cardNumber[-4:]")
        .log(true);
```

Example:

```json
{
  "cardNumber": "********0987"
}
```

Supported examples:

```text
[0]      Keep first character at index 0
[-1]     Keep the last character
[1:3]    Keep characters from index 1 to before index 3
[0:-2]   Keep everything except the last two characters
[:-4]    Keep everything except the last four characters
[-4:]    Keep the last four characters
```

## JSON path rules

Use JSON path-style rules to redact only a specific nested field.

```java
SecureResponse.from(response)
        .redact("/key2/subkey")
        .log(true);
```

Input:

```json
{
  "key1": {
    "subkey": "value"
  },
  "key2": {
    "subkey": "value"
  }
}
```

Output:

```json
{
  "key1": {
    "subkey": "value"
  },
  "key2": {
    "subkey": "********"
  }
}
```

To redact a whole object or array:

```java
SecureResponse.from(response)
        .redact("/key2")
        .log(true);
```

Output:

```json
{
  "key2": "********"
}
```

## Wildcard JSON path rules

Use `*` to match one path segment.

```java
SecureResponse.from(response)
        .redact("/products/*/reviews")
        .log(true);
```

This matches:

```text
/products/0/reviews
/products/1/reviews
```

It does not match:

```text
/orders/0/reviews
```

Use `**` to match any number of path segments.

```java
SecureResponse.from(response)
        .redact("/**/reviews")
        .log(true);
```

This matches all `reviews` fields anywhere in the JSON body.

## Regex rules

Use `regex:` for regular expression rules.

A regex that does not start with `/` is matched against keys:

```java
SecureResponse.from(response)
        .redact("regex:(?i).*token.*")
        .log(true);
```

This can match:

```text
accessToken
refreshToken
id_token
```

A regex that starts with `/` is matched against JSON paths:

```java
SecureResponse.from(response)
        .redact("regex:/products/[0-9]/reviews")
        .log(true);
```

This matches:

```text
/products/0/reviews
/products/1/reviews
```

It does not match:

```text
/products/10/reviews
```

Use `\\d+` in Java strings when matching multiple digits:

```java
.redact("regex:/products/\\d+/reviews")
```

## Remove default rules

`RedactionPolicy.defaults()` protects common sensitive keys such as:

```text
Authorization
API-KEY
access-token
refresh-token
token
password
secret
set-cookie
```

You can remove a default rule:

```java
RedactionPolicy policy = RedactionPolicy.defaults()
        .removeRules("Authorization");
```

Then output changes from:

```text
Authorization = ********
```

to:

```text
Authorization = Bearer ********
```

The header name and structure stay visible, while the secret value can still be masked.

## Load rules from a file

You can load redaction rules from an input stream:

```java
SecureContext secure = SecureContext.create()
        .redactionPolicy(RedactionPolicy.defaults())
        .plain(env)
        .load(getClass().getClassLoader().getResourceAsStream("secure-rules.ini"));
```

Example `secure-rules.ini`:

```text
# Supported redaction rules:
# username                       -> key rule
# Content-Type[-4:]              -> key rule with slice
# -Authorization                 -> removes Authorization from default redaction
# /products/*/reviews            -> JSON path wildcard rule, * matches one path segment
# /**/reviews                    -> JSON path wildcard rule, ** matches any number of path segments
# regex:.*token.*                -> regex key rule
# regex:/products/\d+/reviews    -> regex JSON path rule

# Header partial masking
Content-Type[-4:]

# Remove default rule
-password

# Login request fields
username
```

Rules beginning with `#` are comments.

Rules beginning with `-` remove a rule:

```text
-password
```

To redact a field whose actual name starts with `-`, escape it:

```text
\-password
```
