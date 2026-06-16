# Changelog

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
