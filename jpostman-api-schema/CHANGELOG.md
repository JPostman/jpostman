# Changelog

## 1.0.2

### Added

- Added `ApiSchemaCli` command-line bridge for RESTAGE Studio and other tools to parse API documents through the Java schema parser.
- Added `env-update` CLI command to update an existing `ApiSpec` model from an environment update JSON file.
- Added `ApiSpecEnvironmentUpdateRequest` and `ApiSpecEnvironmentUpdater` for centralized environment key/value updates.
- Added support for environment `renames`, `adds`, `values`, and `deletes`.
- Added automatic environment variable generation for OpenAPI and Swagger path/query parameters.
- Added OpenAPI and Swagger response model support with status code, description, content type, body, and examples.
- Added generated request/response examples from OpenAPI and Swagger schemas, including object properties, arrays, enums, defaults, and composed schemas.
- Added GraphQL request examples from the generated GraphQL body and headers.
- Added shaded CLI jar generation with classifier `cli`.
- Added `PostmanCollectionExporter` to export normalized `ApiSpec` models as Postman Collection v2.1 JSON.
- Added `PostmanEnvironmentExporter` to export normalized `ApiSpec` environment values as Postman Environment JSON.
- Added `collection` / `postman-collection`, `environment` / `postman-environment`, and `postman` CLI commands.
- Added `--model` and `--model-stdin` input support so Postman files can be generated from an edited existing `ApiSpec` model without reparsing the original schema.
- Added `--output` support for writing `parse`, `collection`, `environment`, and `env-update` results directly to files.
- Added Postman export naming options with `--name`, `--collection-name`, and `--environment-name`.
- Added smoke test coverage for exporting Postman Collection and Environment JSON from parsed OpenAPI models.

### Changed

- Changed OpenAPI and Swagger operation display description resolution to prefer `summary`, then operation `description`, then response description, then request body description.
- Changed OpenAPI and Swagger parameter values to use generated environment placeholders such as `{{product_limit}}`, `{{product_id}}`, and `{{product_category}}`.
- Changed JSON request and response examples to pretty-print valid JSON content.
- Changed raw OpenAPI example handling so `examples.*.value` can update environment values for path, query, header, and body placeholders.
- Changed environment token formatting to normalize `{{ key }}` into `{{key}}` after environment updates.
- Changed Maven shade configuration to merge service-loader files and reduce duplicate shaded resource warnings.
- Changed `ApiSchemaCli` from a parse/update-only bridge into a multi-command CLI for parsing, environment updates, and Postman exports.
- Changed Postman export workflow to support UI-edited local models as the preferred RESTAGE Studio flow.
- Changed JSON pretty output to use a custom Jackson pretty printer so array items are written on separate lines.
- Changed `env-update` to reuse shared model input handling and optionally write updated models through `--output`.
- Changed CLI validation to prevent mixing source document input (`--file` / `--stdin`) with existing model input (`--model` / `--model-stdin`).
- Changed CLI README to document source parsing, existing model export, Postman file generation, and Java exporter usage.

### Fixed

- Fixed Swagger/OpenAPI parser output inconsistency where response descriptions could be shown instead of operation summaries.
- Fixed missing request examples when examples were only available from schema property values.
- Fixed missing response descriptions and response examples in the normalized API model.
- Fixed environment values being overwritten by blank or placeholder values during parser-generated environment creation.
- Fixed duplicate shaded Swagger safe URL resolver classes by excluding the old relocated resolver artifact.
- Fixed raw `Schema` generic warnings in OpenAPI schema example handling.
