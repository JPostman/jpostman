# JPostman API Schema CLI

This module provides a command-line bridge for RESTAGE Studio and other tools.
It has two separate jobs:

1. Parse Swagger/OpenAPI/Postman/GraphQL documents into the common JPostman `ApiSpec` model.
2. Export an existing `ApiSpec` model into Postman Collection and Postman Environment JSON.

The second job is important for UI workflows: the UI can parse once, edit the local `ApiSpec` model, add/remove/update environment values, and then generate the final Postman files from that edited model.

The CLI entry point is:

```bash
io.jpostman.schema.cli.ApiSchemaCli
```

## Supported Source Document Formats

The `parse` command auto-detects the input format from the document content.

| Format | Supported Examples |
| --- | --- |
| OpenAPI | OpenAPI 3.x JSON or YAML documents containing `openapi` |
| Swagger | Swagger 2.0 JSON or YAML documents containing `swagger` |
| Postman | Postman Collection JSON documents containing `info`, `item`, and `request` |
| GraphQL | GraphQL schema documents containing `type Query`, `type Mutation`, `schema { ... }`, or `extend type Query` |

## Commands

| Command | Input | Output |
| --- | --- | --- |
| `parse` | Source API document | Normalized JPostman `ApiSpec` JSON model |
| `collection` or `postman-collection` | Source API document or existing `ApiSpec` model | Postman Collection v2.1 JSON |
| `environment` or `postman-environment` | Source API document or existing `ApiSpec` model | Postman Environment JSON |
| `postman` | Source API document or existing `ApiSpec` model | Saves both Postman Collection and Postman Environment JSON files |
| `env-update` | Existing `ApiSpec` model plus update request | Updated `ApiSpec` JSON model |

## Source Document Input Options

Use these when you want the CLI to parse Swagger/OpenAPI/Postman/GraphQL first.

| Option | Required | Description |
| --- | --- | --- |
| `--file <path>` | Yes, unless `--stdin` is used | Reads the source API document from a file. |
| `--stdin` | Yes, unless `--file` is used | Reads the source API document from standard input. |
| `--base-url <url>` | No | Overrides or supplies the base URL used by the generated model. Useful for GraphQL schemas or specs without server information. |
| `--override-url <true|false>` | No | Controls whether operation URLs are generated with `{{BASE_URL}}` included in each request path. |

## Existing Model Input Options

Use these when the UI already has a local `ApiSpec` model and the user may have edited it.

| Option | Required | Description |
| --- | --- | --- |
| `--model <path>` | Yes, unless `--model-stdin` is used | Reads an existing normalized `ApiSpec` JSON model from a file. |
| `--model-stdin` | Yes, unless `--model` is used | Reads an existing normalized `ApiSpec` JSON model from standard input. |

Do not mix source input and model input in the same command. Use either `--file`/`--stdin` or `--model`/`--model-stdin`.

## Shared Output Options

| Option | Description |
| --- | --- |
| `--output <path>` | Writes output to a file instead of stdout. Supported by `parse`, `collection`, `environment`, and `env-update`. |
| `--pretty` | Prints formatted JSON instead of compact JSON. |
| `--name <name>` | Overrides the Postman Collection or Postman Environment name for `collection` and `environment`. |

---

## `parse`

Parses a source API document and writes the normalized `ApiSpec` JSON model.

The `parse` command accepts source document input only. It does not accept `--model` because the model already exists.

The `parse` command name is optional. These two commands are equivalent:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse --file openapi.yaml
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli --file openapi.yaml
```

### Save parser output to a file

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file openapi.yaml \
  --pretty \
  --output api-spec.json
```

### Parse a GraphQL schema

GraphQL schema documents do not contain a request URL, so pass `--base-url` when the caller knows the GraphQL endpoint.

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file schema.graphql \
  --base-url https://api.example.com/graphql \
  --pretty
```

---

## `collection`

Generates a Postman Collection v2.1 JSON document.

For the RESTAGE UI, prefer generating from the edited local model:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli collection \
  --model api-spec-edited.json \
  --name "DummyJSON Collection" \
  --pretty \
  --output src/test/resources/collection.json
```

Or pass the edited model through stdin:

```bash
cat api-spec-edited.json | java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli collection \
  --model-stdin \
  --name "DummyJSON Collection" \
  --pretty \
  --output src/test/resources/collection.json
```

You can still generate directly from a source document when no UI editing is needed:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli collection \
  --file openapi.yaml \
  --name "DummyJSON Collection" \
  --pretty \
  --output src/test/resources/collection.json
```

The generated collection uses `{{BASE_URL}}` as the Postman environment variable for the base URL.

---

## `environment`

Generates a Postman Environment JSON document from the `envs` map in the `ApiSpec` model.

For the RESTAGE UI, pass the edited model after the user has added, removed, or changed environment variables:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli environment \
  --model api-spec-edited.json \
  --name "DummyJSON Environment" \
  --pretty \
  --output src/test/resources/environment.json
```

Or with stdin:

```bash
cat api-spec-edited.json | java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli environment \
  --model-stdin \
  --name "DummyJSON Environment" \
  --pretty \
  --output src/test/resources/environment.json
```

Environment output format:

```json
{
  "id": "generated-uuid",
  "name": "DummyJSON Environment",
  "values": [
    {
      "key": "BASE_URL",
      "value": "https://dummyjson.com",
      "type": "default",
      "enabled": true
    }
  ],
  "_postman_variable_scope": "environment",
  "_postman_exported_at": "2026-01-01T00:00:00Z",
  "_postman_exported_using": "JPostman API Schema"
}
```

---

## `postman`

Generates both Postman Collection and Postman Environment files.

For RESTAGE Studio, this is the main command to call after the UI has finished editing the local model:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli postman \
  --model api-spec-edited.json \
  --collection-name "DummyJSON Collection" \
  --environment-name "DummyJSON Environment" \
  --collection-output src/test/resources/collection.json \
  --environment-output src/test/resources/environment.json \
  --pretty
```

Using stdin from the edited UI model:

```bash
cat api-spec-edited.json | java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli postman \
  --model-stdin \
  --collection-output src/test/resources/collection.json \
  --environment-output src/test/resources/environment.json \
  --pretty
```

You may also generate only one output from this command by passing only one output flag:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli postman \
  --model api-spec-edited.json \
  --collection-output src/test/resources/collection.json \
  --pretty
```

### `postman` options

| Option | Required | Description |
| --- | --- | --- |
| `--collection-output <path>` | Optional, but at least one output is required | Saves the generated Postman Collection JSON. |
| `--environment-output <path>` | Optional, but at least one output is required | Saves the generated Postman Environment JSON. |
| `--collection-name <name>` | No | Overrides the collection name. |
| `--environment-name <name>` | No | Overrides the environment name. |

---

## `env-update`

Updates environment keys and values inside an existing normalized `ApiSpec` JSON model.

This command is useful when the UI or another caller changes environment variables and needs the same `ApiSpec` model returned with consistent environment data.

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli env-update \
  --model api-spec.json \
  --updates env-updates.json \
  --pretty \
  --output api-spec-updated.json
```

### `env-update` options

| Option | Required | Description |
| --- | --- | --- |
| `--model <path>` | Yes, unless `--model-stdin` is used | Reads the existing normalized `ApiSpec` JSON model from a file. |
| `--model-stdin` | Yes, unless `--model` is used | Reads the existing normalized `ApiSpec` JSON model from standard input. |
| `--updates <path>` | Yes | Reads the environment update request JSON file. |
| `--output <path>` | No | Writes the updated model to a file instead of stdout. |
| `--pretty` | No | Prints formatted JSON instead of compact JSON. |

### Environment update request format

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

Environment updates are applied in this order:

1. Rename keys from `renames`.
2. Add new keys from `adds`.
3. Update values from `values`.
4. Delete keys from `deletes`.
5. Normalize environment token whitespace, for example `{{ base_url }}` becomes `{{base_url}}`.

Rename operations update matching `{{key}}` token usages across the model.
Delete operations remove keys from the environment map only; existing request tokens are preserved so unresolved variables are still visible.

---

## Java Usage From Existing Models

The CLI is only one entry point. If another project already has an `ApiSpec` object in memory, it can export directly without reading a file:

```java
ApiSpec editedSpec = getEditedModelFromUi();
Object collection = new PostmanCollectionExporter().export(editedSpec, "DummyJSON Collection");
Object environment = new PostmanEnvironmentExporter().export(editedSpec, "DummyJSON Environment");
```

Then write the returned objects with Jackson:

```java
ObjectMapper mapper = new ObjectMapper();
mapper.writerWithDefaultPrettyPrinter().writeValue(collectionPath.toFile(), collection);
mapper.writerWithDefaultPrettyPrinter().writeValue(environmentPath.toFile(), environment);
```

---

## Exit Codes

| Exit Code | Meaning |
| --- | --- |
| `0` | Command completed successfully. |
| `1` | General error, invalid arguments, file read/write error, or unexpected failure. |
| `2` | API document parse error with a user-readable message. |

## Recommended RESTAGE UI Workflow

Parse the schema once:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli parse \
  --file DummyJSON.openapi.yaml \
  --pretty \
  --output api-spec.json
```

Let the UI edit the local `ApiSpec` model.

Generate final Postman files from the edited model, not from the original schema file:

```bash
java -cp <classpath-or-jar> io.jpostman.schema.cli.ApiSchemaCli postman \
  --model api-spec-edited.json \
  --collection-output src/test/resources/collection.json \
  --environment-output src/test/resources/environment.json \
  --pretty
```
