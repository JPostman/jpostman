# Changelog

## 2.1.5

### Added

- Added `logOutput` automatic output modes for annotation execution: `none`, `request`, `response`, `info`, and `all`.
- Added support for combining `logOutput` values such as `{ "info", "response" }`, while keeping `none` and `all` as exclusive single-value modes.
- Added `JPostmanInfo.log(boolean)` and `JPostmanInfo.print(boolean)` so callers can choose full output or compact info output.
- Added runtime log helper var args support for `logTrace(...)`, `logDebug(...)`, `logInfo(...)`, `logWarn(...)`, and `logError(...)`.

### Changed

- Renamed context and annotation-local `logLevel` settings to `logOutput` with no backward compatibility.
- Changed response and runner `log()` defaults to `true`; automatic output is still suppressed by default because context `logOutput` defaults to `none`.
- Changed compact info output so `print(false)` omits `methodIndex`, `methods`, and created/start/end timestamps while still showing the main invocation fields.

## 2.1.4

### Added

- Added TestNG runner request callbacks so a `@JPostmanRunner` test method body can run after each executed collection request and inspect the current `jpostman.info()` / `jpostman.ctx()` state.
- Added `JPostmanInfo.runnerRequest(...)` to create an isolated info chain for each request executed by `@JPostmanRunner`.
- Added regression coverage for per-request runner test body execution, runner info isolation, and explicit `verify = 0` verification skipping.

### Changed

- Changed `@JPostmanResponse.verify()` and compact `@JPostman.Response.verify()` semantics so `-1` uses the context default, `0` explicitly skips status-code verification, and concrete values verify that exact HTTP status code.
- Updated `verifyStatusCode` documentation to describe `0` as the default status-code verification skip value and reject invalid values from `1` to `99` or greater than `599`.

## 2.1.3

### Added

- Added `methodIndex` to `JPostmanInfo` so logs can show the zero-based index of the current method inside the shared execution chain.
- Added `methods` chain support for executor steps with readable entries such as `HttpClientExecutor(#auth)`, `HttpClientExecutor(#token)`, and named request execution.
- Added namespace support to `@JPostmanExecutor` and compact `@JPostman.Executor` so `void` executor interceptors can be limited to a specific namespace.
- Added compact `JPostman.Ref<T>` plus `info.ref(...)` helpers for mutable values inside Java lambda chains.

### Changed

- Changed `JPostmanInfo` logging from the old `caller` / `callee` model to the cleaner `method`, `methodIndex`, and `methods` model.
- Changed `JPostman.Info.attr()` examples and tests to use `info.attr().method`, `info.attr().methodIndex`, and `info.attr().methods`.
- Changed `JPostman.Runtime.ctx()` to return the latest active framework context; use `ctx("")` for the default namespace and `ctx("name")` for a specific namespace.
- Changed response and runner execution so filters are applied again after `framework.response(...)`, ensuring the secure log reflects the current response and the current annotation `filter` value.
- Changed status-code configuration so context `verifyStatusCode < 1` skips automatic status-code verification, while values from `1` to `99` or greater than `599` are rejected as invalid.
- Changed response and runner `verify < 1` behavior to inherit the context-level `verifyStatusCode` setting.

### Removed

- Removed `caller` and `callee` fields and related runtime handling from `JPostmanInfo`; use `method`, `methodIndex`, and `methods` instead.

## 2.1.2

### Added

- Added `JPostman.Info.attr()` to the compact info facade so tests can access the full `JPostmanInfo` runtime object for attributes such as `caller`, `callee`, `request`, `namespace`, `cache`, and `id`.
- Added regression coverage for compact `info.attr()` attribute access.

### Fixed

- Fixed response filter state leaking between repeated annotation-driven test executions when the previous context had a response.
- Fixed response filter behavior for secure log output when the active request is prepared by a dependency method and different response tests use different `filter` values.

## 2.1.1

### Added

- Added annotation id normalization so `id = "#token"` is stored and reported as `token`, while `dependsOn = "#token"` and `executor = "#token"` continue to resolve by annotation id.
- Added validation that only one app-level `@JPostman.Context` / `@JPostmanContext` field is allowed per test class.
- Added regression coverage for single app context setup with namespace `@JPostman.TestContext` mirrors, hash-prefixed id constants, duplicate id normalization, and OAuth2 bearer header compatibility.

### Fixed

- Restored compatibility for `info.auth("oauth2", token)` and `info.sauth("oauth2", token)` by applying `Authorization: Bearer <token>` to request headers while keeping generic auth values as runtime metadata.

### Removed

- Removed `namespace()` from compact `@JPostman.Context`.
- Removed `namespace()` from legacy `@JPostmanContext`.

## 2.1.0

### Added

- Added `JPostmanInfo.add()` and compact `JPostman.Info.add()` to make the next `body`, `sbody`, `query`, `squery`, `headers`, or `sheaders` call use explicit add semantics.
- Added namespaced collection fallback support so `collection.<namespace>` falls back to shared `collection` when a namespaced collection property is not configured.

## 2.0.9

### Added

- Added `id` to `@JPostmanRequest`, `@JPostmanResponse`, and compact `@JPostman.Request` / `@JPostman.Response` annotations.
- Added strict annotation-id dependency references with `dependsOn = "#id"` for `@JPostmanRequest`, `@JPostmanResponse`, and `@JPostmanRunner` dependency methods.
- Added strict executor-id references with `executor = "#id"` while keeping plain `executor = "methodName"` as method-name-only lookup.
- Added `JPostmanInfo.id` so request, response, and executor invocation logs can show the resolved annotation id.

### Changed

- Changed dependency resolution so plain `dependsOn = "name"` resolves only Java method names, while `dependsOn = "#name"` resolves only annotation ids.
- Updated dependency and executor error messages to suggest `#id` when an annotation id exists but the user used a plain method-style reference.

## 2.0.8

### Added

- Added regression coverage for cached `@JPostman.Response` dependencies across multiple TestNG method runs.
- Added regression coverage for compact `@JPostman.TestContext` mirrors and `JPostman.Runtime.ctx(...)` access after TestNG method execution and skipped responses.

### Changed

- Updated annotation context storage to keep prepared contexts per test instance and framework context type, preventing TestNG and JUnit context leakage.
- Updated `JPostmanAnnotationRunner` to refresh injected test-context and runtime-context fields after each annotation run, including skipped responses.
- Updated `JPostmanReport` to count real `@JPostmanRunner` request executions while still ignoring dependency helper records.
- Updated `JPostmanTestProxy` with compact context unwrapping so `JPostmanContextRunner` can preserve existing `JPostman.Test` mirrors.

### Fixed

- Fixed `@JPostman.TestContext(active = false)` losing the default namespace/login response after product namespace execution or skipped responses.
- Fixed `@JPostman.TestContext(active = true)` losing the latest active/product response during teardown-style access.
- Fixed `JPostman.Runtime.ctx()` and `JPostman.Runtime.ctx("product")` returning stale or empty contexts after method execution.
- Fixed response dependency cache lookup to use cache-key existence through `hasCache(...)` instead of treating a `null` cache value as missing.
- Fixed cached response dependency execution so parent `JPostmanInfo` is restored after a cached dependency returns early.
- Fixed `JPostmanReport.summary()` showing zero totals for executed `@JPostmanRunner` requests after dependency-helper filtering.

## 2.0.7

### Changed

- Updated `JPostmanReport` result recording so dependency helpers update execution info without increasing top-level pass, fail, or skip totals.
- Updated `JPostmanAnnotationRunner` to record top-level failures and framework skips even when they happen before response execution starts.
- Updated compact `JPostman.Test` proxy return handling to wrap TestNG and JUnit hard/soft assertion implementations with framework-neutral assertion facades.

### Fixed

- Fixed compact `JPostman.Test.asserts(...)` and `JPostman.Test.soft(...)` chaining when Java generic return types are erased at runtime.

## 2.0.6

### Changed

- Moved the compact assertion facade types out of the nested `JPostman` facade so `JPostman.Assertions` and `JPostman.SoftAssertions` are no longer exposed in `JPostman.` autocomplete.
- Updated `JPostmanTestProxy` to wrap hard and soft assertion results with the new top-level assertion facade types.

### Fixed

- Fixed compact facade autocomplete noise by hiding internal assertion aliases from the main `JPostman` namespace.
- Updated coverage test imports and compact test reflection references to use `JPostman.Test` directly.

## 2.0.3

### Added

- Added framework-neutral compact assertion facades: `JPostman.Assertions` and `JPostman.SoftAssertions`.
- Added compact `JPostman.Test` helpers for response path reads, cache reads, plain value reads, and plain value storage: `path(...)`, `cache(...)`, `get(...)`, and `plain(...)`.

### Fixed

- Fixed compact `JPostman.Test` assertion chaining so public examples can use one framework-neutral API with `JPostman.Test` and `JPostman.Info`.
- Fixed tag-chain coverage setup to use request dependencies and a local executor so dependency tag accumulation is tested without relying on skipped execution.

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
