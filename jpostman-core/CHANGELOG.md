# Changelog

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
