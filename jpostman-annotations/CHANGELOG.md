# Changelog

## 2.0.2

### Added

- Added `skipAll` to `@JPostmanContext` and compact `@JPostman.Context` to disable JPostman response and runner test executions by default.
- Added `enabled` to `@JPostmanResponse`, `@JPostmanRunner`, and compact response/runner annotations so selected tests can run while `skipAll` is active.
- Added `skip` and `skipReason` to `@JPostmanRequest`, `@JPostmanResponse`, and compact request/response annotations. A non-empty `skipReason` now also marks the annotation as skipped.
- Added compact `JPostman.Test` support for framework-neutral TestNG/JUnit method parameters.
- Added compact runtime support for `JPostman.Runtime<JPostman.Test>`.
- Added `executorClass` to compact `@JPostman.Context` and kept string-based `executor` for fully qualified executor class names.

### Changed

- Changed JPostman request helper validation so only `@JPostmanRequest` methods are blocked from also being annotated with `@Test`.
- Changed skip handling so response and runner skips are recorded in `JPostmanReport` before framework skip exceptions are thrown.
- Changed TestNG skip handling to clean `SkipException` stack traces using the same stack trace filter used for failures.

## 2.0.1

### Added

- Added the compact `JPostman` facade with nested annotations and runtime aliases.
- Added runtime log shortcut methods: `logTrace`, `logDebug`, `logInfo`, `logWarn`, and `logError`.
- Added `ctx()` and `ctx(String namespace)` access from `JPostmanRuntime` and `JPostman.Runtime`.
- Added `@JPostmanContext.assertions()` and compact `@JPostman.Context.assertions()` for loading assertion rule files from the context.
- Added `@JPostmanContext.dataload()` and compact `@JPostman.Context.dataload()` for loading data files for annotation-driven tests.
- Added `data` selectors directly on `@JPostmanRequest`, `@JPostmanResponse`, `@JPostmanRunner`, and their compact equivalents.
- Added `@JPostmanContext.executor()` and compact `@JPostman.Context.executor()` to configure a default executor class without defining a default executor method.
- Added `logLevel` support on context, executor, request, response, and runner annotations, including compact annotations.
- Added richer `JPostmanInfo` fields including `id`, `callerId`, `annotation`, `callee`, `caller`, `executor`, `cache`, `data`, shared `params`, timing fields, and formatted duration output.
- Added `JPostmanReport.summary()`, latest-info accessors, status lists, total count, duration helpers, and summary output.

### Changed

- Changed executor selection from `@JPostmanExecutor(name = ...)` to `@JPostmanExecutor(id = ...)`.
- Changed annotation debug configuration from `debug` to `logLevel` for new APIs and property resolution.

### Removed

- Removed `@JPostmanAssert`; assertion rule selection now uses `asserts` on response, request, and runner annotations.
- Removed standalone `@JPostmanData`; data selection now uses the `data` attribute on response, request, and runner annotations.

## 1.0.4

### Added

- Added `@JPostmanRunner` to execute multiple Postman requests from a collection or folder.
- Added `@JPostmanAssert` to apply reusable assertion rules after annotation-based response execution.
- Added `JPostmanInfo` to share runtime execution details across annotation chains, dependencies, and executors.
- Added support for passing `JPostmanInfo` into `@JPostmanRequest` helper methods.
- Added support for passing `JPostmanInfo` into `@JPostmanExecutor` methods.
- Added `JPostmanReport` to collect latest execution info, passed executions, failed executions, skipped executions, totals, and total execution time.
- Added `@JPostmanReportContext` to inject a `JPostmanReport` into test classes.
- Added `@JPostmanRequest.next()` to support forward-readable request helper chains.

### Changed

- Added support for assertion rule files with sections, inheritance, request-specific overrides, and comparison rules.
- Added validation to prevent @JPostmanRequest and @JPostmanExecutor helper methods from also being annotated with @Test.

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
