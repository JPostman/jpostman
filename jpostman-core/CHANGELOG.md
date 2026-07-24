# Changelog

## 4.1.1

### Added

- Added `Params.jsonList(...)` helper for passing JSON array fragments into raw body templates.
- Added `Params.jsonMap(...)` helper for passing JSON object fragments into raw body templates.
- Added comprehensive regression and branch coverage for raw body template processing.

## 1.2.6

### Added

- SLF4J logger support to JPostman.Context.
- context log helpers: trace, debug, info, warn, error.
- Context.logger(Class<?>) so annotation runners can log as the user test class.
- RequestBuilder.variables() hook for subclasses.

### Changed

- Updated RequestBuilder to implement RequestProvider.
- Changed build() to resolve variables through build(variables()).
- build(Map<String, ?> vars) for request variable substitution.
- buildRaw() to avoid recursive variable resolution.

## 1.1.4

### Added

- Added `Params.props(Environment, Set<String>)` to copy environment values and override selected keys with non-empty Java system properties.
- Added `Params.props(Map<String, String>, Set<String>)` to control which keys can be overridden by system properties.
- Added support for checking all existing keys when the override key set is `null` or empty.

## 1.0.3

### Added

- Added `Params.props(Map<String, String>)` to copy values and override matching keys with non-empty system properties.


## 1.0.2

### Added

- Added `RequestProvider` as a functional interface for request wrapper support.
  - Added `RequestProvider.build()`.
  - Updated `Request` to implement `RequestProvider`.

- Added `ApiResponse.exists(String)` to check whether a JSON response path exists.
  - Supports simple dot/bracket paths such as:
    - `accessToken`
    - `products[0]`
    - `products[0].title`

- Added `Environment.removeKey(String)` for removing environment variables by key.

- Added `Environment.entry(String)` to access environment parameter metadata.
  - Allows reading and updating `Params.Entry` state.

- Added public accessors and mutator to `Params.Entry`:
  - `getValue()`
  - `isEnabled()`
  - `setEnabled(boolean)`

### Changed
- Renamed request debug output from `toDebugString()` to `log()`.
- Updated `Request.print()` to log using `Request.log()`.
