# Changelog

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