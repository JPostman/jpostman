# Changelog

## 4.3.0

### Changed

- Changed `JPostman.Test.get(String)` to override the shared generic `<T> T get(String)` contract, allowing assignments such as `String token = test.get("token")` without an explicit cast.
- Retained `get(String, Class<T>)` for value conversion when the stored representation differs from the requested Java type.
- Updated regression coverage to avoid ambiguous overloaded assertions for unresolved generic values.

## 4.2.6

### Added

- Added `verify()` to compact `@JPostman.Call` and standalone `@JPostmanCall`; status verification runs only after `JPostman.Runtime.call(...)` completes and a response is available.
- Added `verify = 1` for Runner, Response, and Call executions. The request still runs and captures its response, but an otherwise successful test is reported as skipped.
- Added automatic default `@JPostman.Executor` selection. A single executor, or an executor without an id when multiple executors exist, is used without requiring an `executor` attribute.

### Changed

- Changed status verification values to:
  - `-1` — use `@JPostman.Context.verifyStatusCode`.
  - `0` — ignore the HTTP status code and allow the test to pass.
  - `1` — ignore the HTTP status code and mark an otherwise successful test skipped.
  - `100`–`599` — verify the exact HTTP status code.
- Changed Runner verification so its `verify` value applies only to requests actively executed by that Runner. Standalone Response and Call tests with `verify = -1` continue to use the Context default.
- Changed cache lookup so an exact cache-key match is resolved before implicit dependency-response path lookup. This restores returned scalar values such as `cache = "token"` while retaining 4.2.3 implicit path behavior.
- Changed automatic debug output to print once after execution completes instead of printing an incomplete block before execution and another block afterward.
- Changed method-level `debug = "error"` to defer the failure stack, secure request, and secure response until after the report diagnostics.
- Changed compact report diagnostics to print status and duration before annotation attributes, for example `method: statusCode=200, duration=..., {request = ...}`.
- Changed completed `verify = 1` diagnostics to retain the actual HTTP status code, for example `statusCode=401, SKIPPED`; pre-execution skips such as `skipAll = true` remain `SKIPPED` without a status code.
- Changed default executor namespace resolution so the selected executor namespace is applied before collection, folder, and request lookup unless the current annotation or dependency explicitly supplies a namespace.

### Removed

- Removed `skip()` from compact `@JPostman.Request` and standalone `@JPostmanRequest`; request helpers always execute when reached through a dependency chain.
- Removed `error` from the supported global `@JPostman.Context.debug` values. Global debug now supports `none`, `request`, `response`, `info`, and `all`.
- Removed `error` from `@JPostman.ReportContext.fail`. Method-level `debug = "error"` remains available on Runner, Response, and Call.

## 4.2.3

### Added

- Added `JPostman.Test.get(String)` and `get(String, Class<T>)` for reading cached dependency values and response paths through `#annotationId:path`, `CACHE_NAME:path`, or an implicit path when exactly one cached direct dependency is available.
- Added regression coverage for annotation-id cache aliases, custom cache-key resolution, implicit dependency lookup, ignored uncached dependencies, and ambiguity handling when multiple cached dependencies are available.

### Changed

- Changed `cache = ""` on `@JPostman.Request` and `@JPostman.Response` to use the normalized annotation id as the effective cache key when an id is present, while retaining the Java method-name fallback when no id is defined.

## 4.2.2

### Added

- Added TestNG regression coverage for `@JPostman.Runner(verify = 0)` across mixed `200 -> 201 -> 200` responses, including per-request `@JPostman.Request` dependencies, cached executor-backed `@JPostman.Response` dependencies, and report-result validation.

### Changed

- Changed report diagnostics so skipped executions are displayed as `SKIPPED` instead of showing an unexecuted `duration=00:00.000` result.

### Fixed

- Fixed `@JPostman.Runner(verify = 0)` still applying `@JPostman.Context.verifyStatusCode` to runner responses.

## 4.2.1

### Added

- Added namespace-aware runtime access through jpostman.getCollection(String namespace) and jpostman.getEnvironment(String namespace), while retaining the no-argument methods for the default namespace.
- Added nested collection-folder lookup with Collection.getFolder and slash-separated paths such as Collection.getFolder
- Added composable `@JPostman.ReportContext(fail = ...)` values with one optional action (`ignore`, `skipAll`, or `terminate`) plus `error`, `request`, `response`, `info`, or `all` output details.
- Added deferred report failure sections that retain per-execution request, response, info, and throwable snapshots and print them after the main report summary.

### Changed

- Changed `JPostman.Assert` to expose normal fluent assertions plus `verify()` without extending the public soft-assertion interface.
- Changed context and annotation output configuration to use `debug` as the single setting. Context `debug` now controls minimum/full error traces and request, response, info, or all diagnostics, and local annotations use `debug = "debug"` to inherit the context setting.

### Fixed

- Fixed manually calling `asserts.verify()` causing the same soft failure to be reported again during automatic verification.
- Fixed multiple injected soft assertion fields so all pending failures are verified, aggregated, and cleared even when an earlier field fails.
- Fixed `fail = "error"` printing the full trace both before and after the report summary.
- Fixed `fail = "ignore"` still producing a `JPostman failures` section.
- Fixed report diagnostics and failure details being duplicated when `details = true` or `diagnostic = "extend"` is combined with failure output.

### Removed

- Removed `JPostman.Assert.soft()` and the public `JPostmanSoftAssertions` inheritance from the compact assertion facade; use `@JPostman.AssertContext(soft = true)` and `asserts.verify()`.
- Removed `soft()` from compact and standalone `@JPostman.Response` and `@JPostman.Runner` annotations.
- Removed the compact `@JPostman.Asserts` alias; use `@JPostman.AssertContext`.
- Removed `logs()` from compact and standalone context annotations.
- Removed local `log()` from compact and standalone request, response, runner, call, and executor annotations; use `debug()`.

## 4.2.0

### Added

- Added automatic class-level verification for `@JPostman.AssertContext(soft = true)` in TestNG and JUnit.
- Added automatic verification of runtime soft assertions created through `asserts.soft()` and `asserts.soft(true)`.
- Added assertion-origin tracking so deferred assertion failures identify the test method that created each failure.
- Added automatic `JPostman.Report.summary()` execution after test-class completion.
- Added TestNG configuration-failure reporting for automatic class-level assertion verification.
- Added regression coverage for class-soft assertions, runtime soft assertions, manual verification, automatic verification, lifecycle ordering, duplicate failure prevention, and report-summary finalization.

### Changed

- Changed deferred assertion messages to include the originating class and method in the form `ClassName::methodName`.
- Changed TestNG automatic class verification to run after user-defined `@AfterClass` methods complete.
- Changed TestNG lifecycle handling so a user-defined `@AfterClass` method may call `softAsserts.verify()` before JPostman performs automatic fallback verification.

### Fixed

- Fixed class-soft assertions being verified too early when a test class defines an `@AfterClass` method.
- Fixed JPostman automatic verification running before user teardown verification.

## 4.1.5
### Added

- Added `params` to `JPostmanInfo.log(...)` output to simplify debugging of runtime parameter resolution.

### Fixed

- Fixed dependency execution where unresolved placeholder cleanup prevented later request mutations from being applied.
- Fixed URL path parameter resolution when using `info.params(...)`.
- Fixed secure runtime logging to prevent resolved sensitive values from appearing in request output.

## 4.1.4

### Added

- Added `JPostman.Test.log(boolean)` and `JPostman.Test.print(boolean)` overloads so callers can choose resolved or unresolved request output.
- Added live parameter propagation so values assigned through `info.params(...)` and `info.sparams(...)` immediately update the active request during annotation execution.

### Changed

- Changed `JPostman.Test.log()` and `JPostman.Test.print()` to delegate to the new boolean overloads while preserving existing default behavior.
- Changed annotation output routing to forward formatted request output through the installed `JPostmanOutput` sink before falling back to the configured logger.

### Fixed

- Fixed `JPostman.Test.log()` to include request changes made through `info.body(...)`, `info.params(...)`, `info.sparams(...)`, headers, query, path, and authentication helpers during the current execution.
- Fixed `JPostman.Test.print()` to use the same formatted output as `log()` for consistent request rendering across output sinks and logging.

## 4.1.3

### Added

- Added `JPostman.Info.sparams(...)` overloads for defining secure build-time template parameters by key/value or map.

### Fixed

- Fixed `JPostman.Test.print(true)` ignoring values assigned through current `info.body(...)`, `info.params(...)`, headers, query, path, and authentication helpers.
- Fixed secure parameters supplied through `info.sparams(...)` not being exposed by the compact `JPostman.Info` API.

## 4.1.2

### Added

- Added `info.params(...)` for defining build-time template parameters shared across auth, headers, URL/path, query, and request bodies without adding new component fields.
- Added explicit resolve-only placeholder keys for component methods, for example `info.body("{{name}}", value)`.

### Changed

- Plain keys passed to `info.body(...)`, `info.auth(...)`, `info.headers(...)`, `info.query(...)`, and `info.path(...)` retain the existing add/set behavior for backward compatibility.
- Placeholder-form keys now resolve existing template variables only and are never added as new fields.
- Component-specific values override matching global `info.params(...)` values.

### Fixed

- Fixed backward-compatibility issues where body values intended only for placeholder resolution could be added as new JSON fields.
- Fixed raw JSON fragment parameters, including values created by `Params.jsonList(...)`, so they can be resolved through either `{{key}}` component keys or global `info.params(...)`.

## 4.1.1

### Changed

- Improved JSON serialization of raw template values.

## 3.0.0

### Added

- Added automatic class-finalization support for `@JPostman.AssertContext(soft = true)` in JUnit and TestNG.
- Added automatic `JPostman.Report.summary()` execution after class completion when `@JPostman.ReportContext` is injected.
- Added assertion-origin tracking so deferred failures identify the test method that produced the assertion.
- Added regression coverage for empty `verify()`, explicit `@AfterAll` verification, class-soft assertions, Runner soft assertions, report-summary idempotency, request-scoped Runner assertions, and compact stack traces.

### Changed

- Changed `@JPostman.AssertContext(soft = true)` to remain class-scoped even when used inside `@JPostman.Runner(soft = true)` or `@JPostman.Response(soft = true)`.
- Changed hard/default `@JPostman.AssertContext` soft facades to remain Runner-aware so `asserts.soft(true)` can still be collected and verified at method/request completion.
- Changed deferred assertion output to prefix failures with `ClassName::methodName`.
- Changed root-folder diagnostics from `folder=<default>` to `folder=<root>`.

## 2.3.0

### Added

- Added `JPostmanOutput` and scoped `JPostmanOutputs` routing so hosting integrations can receive JPostman output directly without `System.out` or logger interception.
- Added inherited output-sink support for child threads created during annotation execution.
- Added output-sink routing for `JPostman.Info.print()`, `JPostmanReport.summary()`, annotation method headers, `JPostman.Test.print()`, `request().print()`, and `response().print()`.
- Added output-sink routing for `JPostman.Runtime.logTrace(...)`, `logDebug(...)`, `logInfo(...)`, `logWarn(...)`, and `logError(...)`, including SLF4J-style `{}` placeholder expansion.

### Changed

- Changed user-facing annotation output to prefer the installed `JPostmanOutput` sink and retain the existing SLF4J/core logging behavior when no sink is installed.
- Changed automatic context, request, and response printing to forward their formatted `log()` text directly to the active output sink.
- Changed scoped output handling to restore the previous sink automatically when the execution scope closes.


## 2.2.9

### Fixed

- Fixed `@JPostman.Runner` report totals when every request in the runner scope is handled by explicit `@JPostman.Response` methods. The runner is now recorded as a successful top-level execution instead of being omitted.
- Fixed `JPostmanReport` runner-request detection so multiple requests executed from the same runner method are tracked as distinct report entries rather than sharing a single top-level identity.
- Fixed runner result matching to uniquely identify runner request records by annotation, method, namespace, folder, and request name, allowing pass/fail status updates to affect only the correct request.
- Added regression coverage for multiple runner-request reporting, runner request status updates, and parent-runner reporting when explicit response annotations own every scoped request.

## 2.2.8

### Added

- Added cache path expressions such as `test.cache("token/accessToken")` and nested paths such as `test.cache("token/user/id")`.
- Added typed cache conversion with `test.cache("token/user/id", Integer.class)`.

### Fixed

- Fixed cached response values being replaced or becoming unavailable after later requests changed the active context.
- Fixed cached response paths returning Gson `JsonPrimitive` values instead of ordinary Java scalar values.
- Fixed secure cached values being serialized as wrapper objects such as `{ "value": "********" }` instead of scalar request values.
- Fixed missing body, header, query, and path parameters not being added when their keys were absent from the original request.
- Fixed `info.auth("oauth2", token)` and `info.sauth("oauth2", token)` compatibility by retaining the legacy bearer-header fallback.

## 2.2.7

### Added

- Added `JPostman.Assert.fail(String)` for immediate hard failures with an exact custom message, including when called from a soft assertion facade.
- Added response and call scope inheritance from direct `@JPostman.Request` dependencies. Missing `namespace`, `folder`, and `request` values are resolved independently while explicit values on the current annotation remain authoritative.
- Added clear `@JPostman.Call` validation when no executable request name can be resolved from the call or its dependencies.

### Fixed

- Fixed responses and calls with an explicit request name incorrectly searching the default namespace or root folder when a dependency supplied the missing namespace and folder.
- Fixed blank-request response and call dependencies receiving a null current request instead of the prepared request selected by the parent annotation.
- Fixed response test bodies running after hard status verification or request-preparation failures.
- Fixed soft response assertion failures leaking into later test methods.
- Fixed nested response filters affecting child response dependencies before the child body executed.
- Fixed runner request helpers and runner bodies observing different or missing current-request state during default per-request execution.
- Fixed runner info-isolation expectations for blank-request dependencies that now execute once per selected request.

## 2.2.6

### Added

- Added setup-context baselines for annotation-driven JUnit and TestNG execution so values configured in lifecycle methods such as JUnit `@BeforeAll` and TestNG `@BeforeClass` are retained during test-method execution.
- Added framework context-copy support for creating clean per-method contexts that preserve setup values, redaction rules, filters, and shared cache state while excluding previous request and response state.

## 2.2.5

### Added

- Added runner scope inheritance from direct `@JPostman.Request` dependencies. When a runner leaves `namespace` or `folder` blank, it can reuse those values from the referenced request annotation.
- Added support for both blank-request and named-request dependencies as runner scope providers; runner `include` and `exclude` values continue to control which collection requests execute.
- Added clear validation for `@JPostman.Response` when no request name can be resolved directly or from a request dependency.
- Added regression coverage for inherited runner scopes, named-request dependencies, missing inherited folders, response request-name validation, and duplicate request filtering.

## 2.2.4

### Added

- Added ordered folder traversal with syntax such as `folder = { "level1", "level2", "level3" }`.

### Changed

- Changed annotation `folder()` values from `String` to `String[]`; existing single-folder source syntax such as `folder = "Products"` remains valid after recompilation.

### Fixed

- Fixed nested runner and request resolution so every folder level is matched in parent-to-child order.


## 2.2.3

### Added

- Added `JPostman.Info.method(int stepsBack)` to read entries from the active execution method chain, with bounds fallback and validation for negative values.
- Added regression coverage for internal diagnostics, `@JPostman.Call` placeholder replacement, full request and response capture, failure deduplication, compact stack traces, and reusable TestNG runner callbacks.

### Fixed

- Fixed TestNG `TestNotInvokedException` for reusable `@JPostman.Runner(dependsOn = "#id")` launcher methods by invoking the launcher through TestNG's hook callback.
- Fixed duplicate internal failure records when the same throwable is observed through multiple annotation execution paths.

## 2.2.2

### Added

- Added regression coverage for `JPostman.Runtime.call()` and `JPostman.Runtime.call((ctx, info) -> ...)`, including optional request customization and verification that the old runtime `request()` API is no longer exposed.

### Changed

- Renamed `JPostman.Runtime.request()` to `JPostman.Runtime.call()` for clearer manual execution of requests declared with `@JPostman.Call`.
- Renamed `JPostman.Runtime.request((ctx, info) -> ...)` to `JPostman.Runtime.call((ctx, info) -> ...)`.
- Updated `@JPostman.Call` documentation, validation guidance, and TestNG listener comments to use `jpostman.call(...)`.

## 2.2.1

### Added

- Added `id` to compact `@JPostman.Runner` and legacy `@JPostmanRunner` so runner annotations can be referenced with `dependsOn = "#id"` and included in duplicate annotation-id validation.
- Added `lifecycle = true` runner mode for explicit request/response lifecycle callbacks while keeping the default runner behavior after-response-only for existing tests.
- Added fluent runner lifecycle callbacks: `start(...)` runs once before the first runner request, `request(...)` runs before each runner request, and `response(...)` runs after each runner response.
- Added compact `JPostman.Info` shortcut methods `method()`, `folder()`, and `request()` for easier access to common runtime info values.
- Added `JPostman.Runtime.test()` and `JPostman.Runtime.test(String)` aliases for `ctx()` and `ctx(String)`.

## 2.2.0

### Changed

- Changed automatic debug output so it is controlled by `debug` independently from the `logs` failure-output setting.
- Changed automatic annotation debug handling to respect each annotation's local `log` value for requests, responses, calls, runners, and executors.
- Changed local `log = "debug"` to explicitly inherit the context-level `debug` output, while `log = "none"` suppresses automatic debug output for that annotation.
- Changed context `debug = "request"`, `debug = "response"`, `debug = "info"`, and `debug = "all"` to continue working when context `logs` defaults to `none`.

## 2.1.9

### Added

- Added `logs = "request"`, `logs = "response"`, `logs = "info"`, and `logs = "all"` failure diagnostics to compact `@JPostman.Context` and legacy `@JPostmanContext`.
- Added support for combining failure diagnostics, for example `logs = { "request", "response" }` and `logs = { "error", "response" }`.
- Added JUnit runner request callbacks so `@JPostman.Runner` / `@JPostmanRunner` can execute the test body after each runner request, matching TestNG runner callback behavior.
- Added framework-level hooks to flush pending hard and soft assertions from active JUnit/TestNG contexts during runner callback execution.
- Added full JUnit and TestNG consistency coverage for runner status verification, context status defaults, hard assertions, soft assertions, `@JPostman.AssertContext`, and local `jpostman.ctx().asserts()` / `jpostman.ctx().soft()` usage.

### Changed

- Changed the default context failure-output mode from `logs = { "debug" }` to `logs = { "none" }` for compact and legacy context annotations.
- Changed compact `JPostman.Assert` so the same facade supports both hard and soft framework-neutral assertion methods.
- Changed compact `JPostman.Test` assertion types so `asserts()` and `soft()` both return the compact `JPostman.Assert` facade.

## 2.1.8

## Added

- Added fluent runner rules to `JPostman.Runtime` and `JPostmanRuntime`.
- Added `runner()` to the compact `JPostman.Runtime<C>` API.
- Added `end(...)` callback support for runner execution.
- Added `enabled()` to `@JPostmanCall`.
- Added `skip()` to `@JPostmanRunner`.

## Removed

- Removed `skipReason()` from all annotation APIs.

## 2.1.7

### Added

- Added compact `JPostman.Assert` assertion facade for framework-neutral assertions backed by the latest active JPostman test context.
- Added `@JPostman.Asserts` and `@JPostman.AssertContext` field injection for compact assertion facade access inside test classes.
- Added legacy `@JPostmanAssertContext` field injection for the same assertion facade support.
- Added `JPostman.Assert.soft(boolean)` to switch the injected assertion facade into soft assertion mode.
- Added assertion cleanup hooks for test-body assertion facade calls in TestNG and JUnit.
- Added `@JPostman.Call` and legacy `@JPostmanCall` for one-method request execution when a test should call the request manually from inside the test body.
- Added `JPostman.Runtime.request()` and `JPostman.Runtime.request((ctx, info) -> ...)` so a `@JPostman.Call` test can execute the annotated request and continue with framework-neutral assertions..

## 2.1.6

### Added

- Added `JPostmanInfo.toJson()` and compact `JPostman.Info.toJson()` to convert values in the last body, query, header, path, or auth group to JSON literal strings.
- Added fluent tag value lookup with `info.tags().get(...)`, returning the plain tag value for matching plain tags and the value part for `key=value` tags.
- Added two-parameter tag rule callbacks with `then((info, tags) -> ...)` so callbacks can use the tag helper directly.
- Added regular expression support to `info.tags().has(...)` and `info.tags().any(...)`, including case-insensitive patterns such as `(?i).*mouse.*` and escaped patterns such as `\+\d{1,2}`.

### Changed

- Renamed context automatic annotation output from `logOutput` to `debug` for compact `@JPostman.Context` and legacy `@JPostmanContext`.
- Changed context `logs` from boolean to `String[]`, defaulting to `{ "debug" }`.
- Changed local annotation `log` from boolean to string mode, defaulting to `"debug"`.

### Removed

- Removed `debugFormat()` from compact `@JPostman.Context` and legacy `@JPostmanContext`.
- Removed local annotation `logOutput()` overrides from executor, request, response, and runner annotations.
- Removed `info.tags().contains(...)`; use `has(...)` or `any(...)` with regular expressions such as `.*mouse.*` instead.

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
