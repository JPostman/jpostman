# Changelog

## 1.2.7

### Added

- Added `JPostmanTestContext<C, A, S>` as a shared fluent test context contract for the TestNG, JUnit, and annotations modules.
- Added `JPostmanAssertions<C, A>` as a shared fluent assertion contract for framework-specific assertion implementations.
- Added `JPostmanSoftAssertions<C, A>` as a shared soft assertion contract extending the common assertion API.

## 1.2.6

- Added test coverage for modifying a secure request builder with a new header containing {{accessToken}}.

### Changed
- Removed temporary Environment wrapping from SecureRequest.build().
- Changed SecureRequest.build() to call builder().build() directly.
- Updated SecureRequest.builder() to return a custom RequestBuilder.
- The custom builder overrides variables() and returns resolveValues().

## 1.1.4

### Added

- Added typed `SecureContext.cache(String key)` helper for reading cached values without accessing the cache map directly.
- Added `SecureRequest.builder()` to expose the wrapped request builder.

## 1.1.1

### Added

- Added support for syntax such as `.response(ctx -> executor.execute(ctx.request()))` to make test flows easier to chain.

## 1.1.0

### Added

- Added INI policy support for `redactRegex=REGEX -> VALUE_EXPRESSION`.
- Added INI policy support for `filterList=...` rules.
- Added product policy examples for `filterList`, `redactRegex`, and email redaction in `secure-rules.ini`.
- Added support for custom prefix and suffix masks around regex value expressions such as `****[regex:...]`, `[regex:...]****`, and `****[regex:...]****`.
- Added `SecureContext.filterList(...)` and `SecureResponse.filterList(...)` for filtering JSON array items while preserving parent response fields.
- Added `SecureContext.path(...)`, `SecureContext.paths(...)`, `SecureContext.exists(...)`, and `SecureContext.statusCode()` helpers for accessing the current secure response directly.
- Added `SecureContext.cache()` to expose the shared cache map for manual inspection and manipulation.
- Added `SecureContext.cache(...)` for creating and reusing cached values across related contexts.
- Added `SecureContext.cacheClean(...)` for clearing one or more cached values, or all cached values when no keys are provided.
- Added `SecureContext.CachedFailureException` so framework adapters can skip dependent tests when cached value creation failed earlier.
- Added `SecureContext.callerMethodName(int)` for deriving caller method names used by method-based cache keys.
- Added `JPostmanAssertionError` to carry optional secure log context with assertion failures.

## 1.0.6

### Added

- Added regex value expressions for slice rules, such as phone[regex:^\\+\\d{1,2}], to keep only the matched value portion visible.
- Added SecureContext.redactRegex(String keyRegex, String valueExpression) for applying slice or regex value expressions to fields matched by key regex.
- Added SecureContext.get(...) to return the original secure value by key.
- Added SecureContext.asString(...) to return a secure value as a string.
- Added SecureContext.request(...) and SecureContext.response(...) helpers for storing the latest secure request and response in the context.
- Added SecureContext.from(ApiExecutor) and SecureContext.response(ApiExecutor) helpers for wrapping executor responses.
- Added SecureContext.print() and SecureContext.print(boolean) for TRACE-level secure request and response logging.
- Added support for storing non-string secure values, including numbers, booleans, maps, and lists.

### Changed

- Updated SecureValue to store the original value as Object instead of converting everything to String.
- Updated INI policy rule splitting so commas inside bracket expressions, such as regex value expressions, are not split incorrectly.

## 1.0.5

### Added

- Added INI-style secure policy profiles with `SecureContext.loadPolicy(...)`.
- Added `SecureContext.loadRules(...)` to apply named policy profiles to a copied context without mutating the original context.
- Added policy profile dependencies with `extends=...` and section references such as `[default]`.
- Added `unsecret(...)` to convert protected values back to plain values by key.
- Added `unheaders(...)` for `SecureContext`, `SecureRequest`, and `SecureResponse` to remove protected header masking rules.
- Added shared exact, wildcard path, and `regex:` matching support for JSON rules and header rules.

### Changed

- Updated response header filtering to support exact and `regex:` header rules.

## 1.0.4

### Added

- Added protected header redaction with configurable `headers(...)` support for `SecureContext`, `SecureRequest`, and `SecureResponse`.
- Added response header filtering with `headersFilter(...)` and `removeHeaders(...)`.

### Changed

- Updated secure logging so unresolved request placeholders remain visible in `log(false)`, while concrete protected header values are masked.

### Fixed

- Fixed response header leaks for protected headers such as cookies and authorization-style headers.
- Fixed protected header configuration copying so custom header rules are preserved across policy changes and context copies.

## 1.0.3

### Added

- Added `SecureContext.copy()` for creating an independent secure context copy for parallel request flows.

## 1.0.2

### Added

- Added `SecureContext.filter(...)` for filtering response fields in log output.
- Added `SecureContext.log()` and `SecureContext.log(boolean)` to log the latest secure request and response together.
- Added `SecureResponse.exists(...)` support for simple paths, slash paths, wildcard paths, recursive wildcard paths, and regex rules.
- Added `SecureResponse.paths(...)` to return all values matching a path rule, for example `response.paths("/**/id")`.
- Added `SecureResponse.filtered()` for filtered and redacted response body output.
- Added `JsonPathRules` to centralize JSON path normalization, matching, and value extraction.

### Changed

- Renamed secure request debug output from `toDebugString()` to `log()`.
- Changed `SecureRequest.print()` to log resolved output by default.
- Changed `SecureResponse.pretty()` to return the full redacted response body without applying filters.
- Changed `SecureResponse.log()` to apply configured filters before redaction.

### Fixed

- Fixed path normalization for mixed path formats such as `/products[0].id`, `/products[0]/id`, and `/products/0/id`.
