# Changelog

## 1.0.4
### Added
- Added @JPostmanRunner to execute multiple Postman requests from a collection or folder.
- Added @JPostmanAssert to apply reusable assertion rules after annotation-based response execution.
- Added support for assertion rule files with sections, inheritance, request-specific overrides, and comparison rules.
- Added validation to prevent @JPostmanRequest and @JPostmanExecutor helper methods from also being annotated with @Test.

### Changed
- Extended @JPostmanExecutor support to allow executor methods with the current Postman request name.
- Improved annotation failure handling with shorter, cleaner stack traces for JUnit and TestNG execution.
- Refactored annotation runtime context loading, request discovery, and assertion execution into dedicated runtime helpers.

## 1.0.3

### Added

- Added dependency cache detection using key existence through `hasCache(...)`.

### Fixed

- Fixed annotation field injection so `@JPostmanContext` and `@JPostmanTestContext` can be available before lifecycle methods such as JUnit `@BeforeAll` and TestNG `@BeforeClass`.
- Fixed misleading documentation that described `@JPostmanRequest.dependsOn()` as running before a response instead of before a request method.


## 1.0.2

### Added

- Added `@JPostmanContext` to inject the loaded core `JPostman.Context` into annotation-based tests.
- Added `filter` support to `@JPostmanResponse`.
- Added soft assertion and secure-log options to `@JPostmanResponse`.
- Added annotation runtime support for `verify`, `soft`, and `log` during response validation.

### Changed

- Renamed the annotation used for JUnitContext and TestNgContext fields from @JPostmanContext to @JPostmanTestContext.