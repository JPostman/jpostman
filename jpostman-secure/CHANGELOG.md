# Changelog

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
