# Changelog

## 1.2.7

### Changed

- Updated `JUnitContext` to implement the shared `JPostmanTestContext` contract.
- Updated `JUnitAssertions` to implement the shared `JPostmanAssertions` contract.
- Updated `JUnitSoftAssertions` to implement the shared `JPostmanSoftAssertions` contract.

## 1.2.6

### Added

- Added `allMatch(...)` assertion support for validating every numeric value returned by a response path.
- Added limited stack trace printing for JUnit failures, using `DEFAULT_MAX_STACK_TRACE`.

### Fixed

- Fixed noisy JUnit annotation failures by routing thrown errors through the annotation engine cleanup logic when `jpostman-annotations` is available.
- Improved fallback behavior so original JUnit errors are preserved when annotation cleanup is unavailable.

## 1.2.5

### Added

- Added TestNgContext.hasKey(String key) to check whether a plain or protected value key exists.

## 1.2.4

### Added

- Added `JUnitContext.ctx()` instance helper for annotation-based JUnit tests.
- Added JUnit failure printing support for `beforeEach` and `afterEach` lifecycle failures.

### Changed

- Changed `@JPostmanJUnit(printFailures)` default from `false` to `true`.

## 1.2.2

### Added

- Added optional JPostman annotation support hook for JUnit through `JPostmanJUnitAnnotationExtension`.
- Added `JUnitContext.current()`, `JUnitContext.setCurrent(...)`, and `JUnitContext.clearCurrent()` to support per-thread current context access during annotation-based execution.

### Changed

- Updated `@JPostmanJUnit` to register both the existing failure printer and the optional annotation extension.
- Updated `JUnitSoftAssertions.assertAll()` to return the owning `JUnitContext`, allowing fluent chaining after soft assertion verification.

## 1.2.1

### Changed

- Replaced `AfterTestExecutionCallback` with JUnit exception handlers to support failures from both test methods and lifecycle methods.

### Fixed

- Fixed JUnit failure printing so secure logs are printed for failures raised during `@AfterEach` teardown.

## 1.2.0

### Added

- Added `context(Consumer<JUnitContext>)` to support custom context logic inside fluent method chains.
- Added support for applying assertions, cache updates, and other context operations without breaking the fluent API flow.
- Added `context()` to JUnit assertion classes to allow returning to the owning `JUnitContext` from an assertion chain.

### Changed

- Updated JUnit assertion chaining to preserve the concrete assertion type during fluent method calls.
- Updated `JUnitAssertions` to use a self-type generic pattern for cleaner fluent API support.
- Updated `JUnitSoftAssertions` so chained assertion methods return `JUnitSoftAssertions` instead of the base assertion type.

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