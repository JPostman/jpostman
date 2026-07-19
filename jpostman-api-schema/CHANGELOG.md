# Changelog

## 1.1.0

- Removed all command-line entry points and shaded executable JAR generation.
- Kept the project as a pure reusable Java schema library.
- Moved Postman exporters to `io.jpostman.schema.export`.
- Schema parsing, environment updates, and exports are invoked by ReStage Engine services.
