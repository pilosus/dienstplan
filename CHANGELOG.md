# Change Log

All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.1.3] - 2022-01-09

### Added
- Env `SERVER__ACCESS_LOG` (true/false, default true) to control access log for endpoints
- Env `DB__POOL_MIN_IDLE` (integer, default 10) for minimal idle DB connections in the pool
- Env `DB__POOL_MAX_SIZE` (integer, default 20) for DB connection pool max size
- Env `DB__TIMEOUT_MS_CONNECTION` (integer, default 10000) for DB connection timeout in milliseconds
- Default value `latest` for env `APP__VERSION` used in Sentry stack trace metadata

### Changed
- Configuration type coercion (via `spec/conform`) in `config` mount-component start.

### Fixed
- Default routing handler working outside of the `/api/*` path

## [0.1.2] - 2022-01-03

### Added
- Support for `Docker` and `Docker Compose`
- `Makefile` to ease `Docker Compose` ops

## [0.1.1] - 2022-01-02

### Added
- GitHub workflows for pull requests and master branch merges:
  linters, tests, coverage report

### Fixed
- CHANGELOG.md file links

### [0.1.0] - 2022-01-01

### Added
- Documentation
- Unit-tests

## 0.0.0 - 2021-10-22 / 2021-12-31

### Added
- Bot app MVP

[Unreleased]: https://github.com/pilosus/dienstplan/compare/0.1.3...HEAD
[0.1.3]: https://github.com/pilosus/dienstplan/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/pilosus/dienstplan/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/pilosus/dienstplan/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/pilosus/dienstplan/compare/0.0.0...0.1.0
