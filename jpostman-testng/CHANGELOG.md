# Changelog

## 1.1.2

### Changed

- Updated JUnit and TestNG assertion `verify()` methods to return the current context after verification.

## 1.1.1

### Added

- Added support for syntax such as `.response(ctx -> executor.execute(ctx.request()))` to make test flows easier to chain.