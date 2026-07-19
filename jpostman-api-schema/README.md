# JPostman API Schema 1.1.0

Pure Java library for parsing and transforming OpenAPI, Postman Collection, and GraphQL documents into the shared `ApiSpec` model.

The library also provides:

- environment update support through `ApiSpecEnvironmentUpdater`
- Postman collection export through `io.jpostman.schema.export.PostmanCollectionExporter`
- Postman environment export through `io.jpostman.schema.export.PostmanEnvironmentExporter`

This module has no command-line entry point and produces no executable or shaded CLI artifact. ReStage Engine invokes its Java APIs directly.
