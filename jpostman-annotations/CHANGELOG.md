# Changelog

## 1.2.6

### Added

- Added `@JPostmanData` for loading request data into `JPostmanInfo.params` from configured data files.
- Added `@JPostmanContext.dataload()` to register one or more data files for annotation-driven tests.
- Added data support for `type=map`, `type=json`, `type=xml`, and external `source=` files.
- Added data expressions for environment, secret, plain, cache, and current params values.
- Added cached-context data expressions such as `{{jpostman:[user]firstName}}` and `{{jpostman:[user]/**/lastName}}`.
- Added `@JPostmanData.dependsOn()` so dependencies can run before data is loaded.
- Added `@JPostmanResponse.cache()` for caching response dependency context/results.
- Added shared cache handling so cached response values can be reused across prepared contexts and namespaces.
- Added `@JPostmanContext.verifyStatusCode()` to configure default response status verification.
- Added `@JPostmanContext.debug()` and `debugFormat()` for annotation runtime debug logging.
- Added `@JPostmanTestContext(active = true)` support to mirror the latest active framework context.
- Added richer `JPostmanInfo` fields including `id`, `callerId`, `annotation`, `callee`, `caller`, `executor`, `cache`, `data`, and shared `params`.
- Added `JPostmanReport.summary()`, latest-info accessors, status lists, total count, and duration helpers.
- Added request-specific assertion-rule precedence and improved section inheritance behavior.
- Added soft runner failure grouping with request location details.
- Added public API JavaDoc coverage for annotations and runtime helper classes.
- Added coverage tests for new validation, assertion, data, dependency, cache, and runner flows.

### Changed

- Changed executor selection from `@JPostmanExecutor(name = ...)` to `@JPostmanExecutor(id = ...)`.
- Changed supported executor signatures to context-only or context plus `JPostmanInfo`.
- Changed annotation runtime validation to run before JUnit/TestNG annotation execution.
- Changed `@JPostmanResponse.verify()` and `@JPostmanRunner.verify()` default to `-1`, allowing `verifyStatusCode()` to decide default verification.
- Changed dependency behavior so request location is not inherited from unrelated dependencies.
- Changed empty `@JPostmanResponse(request = "")` with only dependencies to run dependencies only, without executing a stale active request.
- Changed response cache behavior so cached response dependencies execute once and reuse the cached value on later dependency calls.
- Changed `@JPostmanResponse(cache = ...)` with a void method to cache the active framework context.
- Changed `@JPostmanRequest(cache = ...)` validation to require a non-void return value.
- Changed response cache validation so `@JPostmanResponse(cache = ...)` cannot be combined with `@Test`.
- Changed each annotation execution to use clean request-scoped context state while preserving cache values.
- Changed assertion-rule inheritance so repeatable assertion rules append parent values while preserving request-specific section authority.
- Changed `JPostmanReport` counters to derive totals and duration from recorded execution info instead of mutable counters.
- Updated README examples to use `JPostmanInfo` in executors and added assertion-rule documentation.

### Fixed

- Fixed stale request execution when a response method had dependencies but no explicit request.
- Fixed mixed dependency location bugs such as combining a product namespace with an auth request name.
- Fixed duplicate cached response dependency execution.
- Fixed filter/rule state leaking between annotation executions.
- Fixed cached-path wildcard resolution for paths such as `/**/lastName`.
- Fixed `@JPostmanRunner(soft = true)` so failures are collected per request and reported with namespace, folder, and request.
- Fixed assertion-rule selection so `[Get all products] extends=default` does not also merge unrelated `[product]` rules.
- Fixed validation messages for invalid helper/test annotation combinations.

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
