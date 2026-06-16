# Changelog

## 1.1.4

### Added

- Added typed `.cache(String key)` helper for reading cached values without accessing the cache map directly.

### Changed

- Updated `.path(...)` to use a generic return type for cleaner typed response value access.
- Updated `.paths(...)` to use a generic list return type for cleaner typed response list access.

## 1.1.3

### Changed

- Updated JUnit and TestNG response function handling to support both direct API responses and configurable API executors.

## 1.1.2

### Changed

- Updated JUnit and TestNG assertion `verify()` methods to return the current context after verification.

## 1.1.1

### Added

- Added support for syntax such as `.response(ctx -> executor.execute(ctx.request()))` to make test flows easier to chain.