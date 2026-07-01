# JPostman API Schema

This module imports API documents from text content and converts them into one common Java model.

Supported input types:

- OpenAPI / Swagger
- Postman Collection v2.x
- GraphQL schema

The module does **not** execute requests and does **not** import Postman scripts. It only extracts the API shape needed by JPostman.

## Main model

```java
ApiSpec {
    name
    baseUrl
    overrideUrl
    folders
    operations
    envs
}

ApiOperation {
    folder
    methodName
    description
    method
    allowedMethods
    path
    queryParams
    headers
    body
    auth
    example
    protocol
    graphQlOperationType
    urlResolved
}
```

Auth is stored separately from headers.

## BASE_URL behavior

If `baseUrl` exists, the module adds it to `envs`:

```java
envs.put("BASE_URL", baseUrl);
```

Defaults:

- OpenAPI / Swagger: `overrideUrl = false`
- Postman Collection: `overrideUrl = false`
- GraphQL schema: `overrideUrl = true`

When `overrideUrl = true`, operation paths are rewritten with `{{BASE_URL}}`.

Example:

```text
https://dummy.com/auth/login
```

becomes:

```text
{{BASE_URL}}/auth/login
```

For GraphQL, if the user enters:

```text
https://dummy.com/graphql
```

then the operation path becomes:

```text
{{BASE_URL}}
```

because the GraphQL schema itself does not define the HTTP endpoint.

## Environment extraction

The module scans these fields for `{{KEY}}` variables:

- path
- query params
- headers
- body
- auth
- examples

Example:

```json
{
  "username": "{{username}}",
  "password": "{{password}}"
}
```

creates:

```java
envs = {
  "username": "",
  "password": ""
}
```

If an example has real values:

```json
{
  "username": "emilys",
  "password": "emilyspass"
}
```

then envs are updated:

```java
envs = {
  "username": "emilys",
  "password": "emilyspass"
}
```

## Usage

```java
ApiSpec spec = ApiSpecParser.parse(textArea.getText());
```

With GraphQL URL from your UI:

```java
ApiSpecParserOptions options = new ApiSpecParserOptions();
options.setBaseUrl("https://dummy.com/graphql");
options.setOverrideUrl(true);

ApiSpec spec = ApiSpecParser.parse(textArea.getText(), options);
```

## Add to parent Maven project

Add this module to your root `pom.xml`:

```xml
<module>jpostman-api-schema</module>
```
