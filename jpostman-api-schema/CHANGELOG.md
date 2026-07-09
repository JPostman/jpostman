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

### Changed

- Changed OpenAPI and Swagger operation display description resolution to prefer `summary`, then operation `description`, then response description, then request body description.
- Changed OpenAPI and Swagger parameter values to use generated environment placeholders such as `{{product_limit}}`, `{{product_id}}`, and `{{product_category}}`.
- Changed JSON request and response examples to pretty-print valid JSON content.
- Changed raw OpenAPI example handling so `examples.*.value` can update environment values for path, query, header, and body placeholders.
- Changed environment token formatting to normalize `{{ key }}` into `{{key}}` after environment updates.
- Changed Maven shade configuration to merge service-loader files and reduce duplicate shaded resource warnings.

### Fixed

- Fixed Swagger/OpenAPI parser output inconsistency where response descriptions could be shown instead of operation summaries.
- Fixed missing request examples when examples were only available from schema property values.
- Fixed missing response descriptions and response examples in the normalized API model.
- Fixed environment values being overwritten by blank or placeholder values during parser-generated environment creation.
- Fixed duplicate shaded Swagger safe URL resolver classes by excluding the old relocated resolver artifact.
- Fixed raw `Schema` generic warnings in OpenAPI schema example handling.
- Fixed shaded CLI resource warnings for duplicate license files, Maven metadata, signed jar metadata, and duplicated schema resources.