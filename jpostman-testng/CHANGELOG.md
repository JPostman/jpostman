# Changelog

## 1.2.4

### Added
- Added test coverage to verify that `ctx()` delegates to `TestNgContext.current()`.

## 1.2.3

### Added

- Added automatic TestNG listener registration through Java ServiceLoader.
- Added `META-INF/services/org.testng.ITestNGListener` to register `JPostmanTestNgAnnotationListener`.
- Added support for using only `@JPostmanTestNG` on TestNG test classes without also adding `@Listeners(...)`.

## 1.2.2

### Added

- Added optional JPostman annotation support hook for TestNG through JPostmanTestNgAnnotationListener.
- Added TestNgContext.current(), TestNgContext.setCurrent(...), and TestNgContext.clearCurrent() to support per-thread current context access during annotation-based execution.
- Added tests for the optional TestNG annotation listener and current context lifecycle.

### Changed

- Updated @JPostmanTestNG to register the optional annotation listener.
- Updated TestNgSoftAssertions.assertAll() to return the owning TestNgContext, allowing fluent chaining after soft assertion verification.

## 1.2.0

### Added

- Added `context(Consumer<TestNgContext>)` to support custom context logic inside fluent method chains.
- Added support for applying assertions, cache updates, and other context operations without breaking the fluent API flow.
- Added `context()` to TestNG assertion classes to allow returning to the owning `TestNgContext` from an assertion chain.

### Changed

- Updated TestNG assertion chaining to preserve the concrete assertion type during fluent method calls.
- Updated `TestNgAssertions` to use a self-type generic pattern for cleaner fluent API support.
- Updated `TestNgSoftAssertions` so chained assertion methods return `TestNgSoftAssertions` instead of the base assertion type.

## 1.1.5

### Added

- Added `cache(String key, Object value)` to JUnit and TestNG contexts for storing shared cache values in fluent style.

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