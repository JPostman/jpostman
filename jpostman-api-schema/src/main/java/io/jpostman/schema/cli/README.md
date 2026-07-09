# JPostman API Schema CLI

This module provides a command-line bridge for parsing API documents into the common JPostman `ApiSpec` JSON model.
It supports Swagger/OpenAPI, Postman Collection, and GraphQL schema documents.

The CLI entry point is:

```bash
io.jpostman.schema.cli.ApiSchemaCli
```

The parser prints the normalized `ApiSpec` JSON to `stdout`.
Errors are printed to `stderr`.

## Supported Input Formats

The CLI auto-detects the input format from the document content.

| Format | Supported Examples |
| --- | --- |
| OpenAPI | OpenAPI 3.x JSON or YAML documents containing `openapi` |
| Swagger | Swagger 2.0 JSON or YAML documents containing `swagger` |
| Postman | Postman Collection JSON documents containing `info`, `item`, and `request` |
| GraphQL | GraphQL schema documents containing `type Query`, `type Mutation`, `schema { ... }`, or `extend type Query` |

## Commands

### `parse`

Parses an API document and writes the normalized `ApiSpec` JSON model.

The `parse` command name is optional. These two commands are equivalent:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse --file openapi.yaml
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli --file openapi.yaml
```

#### Options

| Option | Required | Description |
| --- | --- | --- |
| `--file <path>` | Yes, unless `--stdin` is used | Reads the API document from a file. |
| `--stdin` | Yes, unless `--file` is used | Reads the API document from standard input. |
| `--base-url <url>` | No | Overrides or supplies the base URL used by the generated model. Useful for GraphQL schemas or specs without server information. |
| `--override-url` | No | Sets the `overrideUrl` flag in the generated model. |
| `--pretty` | No | Prints formatted JSON instead of compact JSON. |

#### Parse an OpenAPI document

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file openapi.yaml \
  --pretty
```

#### Parse a Swagger 2.0 document

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file swagger.json \
  --pretty
```

#### Parse a Postman Collection

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file DummyJSON.postman_collection.json \
  --pretty
```

#### Parse a GraphQL schema

GraphQL schema documents do not contain a request URL, so pass `--base-url` when the caller knows the GraphQL endpoint.

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file schema.graphql \
  --base-url https://api.example.com/graphql \
  --pretty
```

#### Parse from standard input

```bash
cat openapi.yaml | java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --stdin \
  --pretty
```

#### Save parser output to a file

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file openapi.yaml \
  --pretty > api-spec.json
```

#### Use a custom base URL

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file openapi.yaml \
  --base-url https://dummyjson.com \
  --override-url true \
  --pretty
```

---

### `env-update`

Updates environment keys and values inside an existing normalized `ApiSpec` JSON model.

This command is useful when the UI or another caller changes environment variables and needs the same `ApiSpec` model returned with consistent environment data.

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli env-update \
  --model api-spec.json \
  --updates env-updates.json \
  --pretty
```

#### Options

| Option | Required | Description |
| --- | --- | --- |
| `--model <path>` | Yes, unless `--model-stdin` is used | Reads the existing normalized `ApiSpec` JSON model from a file. |
| `--model-stdin` | Yes, unless `--model` is used | Reads the existing normalized `ApiSpec` JSON model from standard input. |
| `--updates <path>` | Yes | Reads the environment update request JSON file. |
| `--pretty` | No | Prints formatted JSON instead of compact JSON. |

#### Environment update request format

```json
{
  "renames": {
    "BASE_URL": "base_url"
  },
  "adds": {
    "productLimit": "10"
  },
  "values": {
    "base_url": "https://dummyjson.com",
    "accessToken": "{{accessToken}}"
  },
  "deletes": [
    "oldKey"
  ]
}
```

#### Update from files

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli env-update \
  --model api-spec.json \
  --updates env-updates.json \
  --pretty > api-spec-updated.json
```

#### Update from standard input

```bash
cat api-spec.json | java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli env-update \
  --model-stdin \
  --updates env-updates.json \
  --pretty > api-spec-updated.json
```

#### Environment update behavior

Environment updates are applied in this order:

1. Rename keys from `renames`.
2. Add new keys from `adds`.
3. Update values from `values`.
4. Delete keys from `deletes`.
5. Normalize environment token whitespace, for example `{{ base_url }}` becomes `{{base_url}}`.

Rename operations update matching `{{key}}` token usages across the model.
Delete operations remove keys from the environment map only; existing request tokens are preserved so unresolved variables are still visible.

## Exit Codes

| Exit Code | Meaning |
| --- | --- |
| `0` | Command completed successfully. |
| `1` | General error, invalid arguments, file read/write error, or unexpected failure. |
| `2` | API document parse error with a user-readable message. |

## Common Workflow

Parse an API document, save the normalized model, then apply environment updates.

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file DummyJSON.postman_collection.json \
  --base-url https://dummyjson.com \
  --pretty > api-spec.json

java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli env-update \
  --model api-spec.json \
  --updates env-updates.json \
  --pretty > api-spec-updated.json
```

## Minimal Sample Files

### OpenAPI sample

```yaml
openapi: 3.0.0
info:
  title: Demo API
  version: 1.0.0
servers:
  - url: https://dummyjson.com
paths:
  /products:
    get:
      operationId: getProducts
      summary: Get products
      responses:
        '200':
          description: OK
```

### GraphQL sample

```graphql
type Query {
  product(id: ID!): Product
}

type Product {
  id: ID!
  title: String
}
```

### Environment update sample

```json
{
  "adds": {
    "base_url": "https://dummyjson.com",
    "productLimit": "10"
  },
  "values": {
    "productLimit": "20"
  }
}
```
