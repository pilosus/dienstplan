# Change Log

All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.2.9] - 2022-02-15

### Added
- Integration tests and fixtures
  ([#41](https://github.com/pilosus/dienstplan/issues/41))

## [0.2.8] - 2022-02-05

### Added
- App version in default help message text
  ([#42](https://github.com/pilosus/dienstplan/issues/42))
- Community documents: contributing guide, bug report issue template,
  feature request issue template
  ([#39](https://github.com/pilosus/dienstplan/issues/39))

## [0.2.7] - 2022-01-29

### Fixed
- `NullPointerException` fixed for `create` and `assign` commands when
  no rotation name passed in
  ([#46](https://github.com/pilosus/dienstplan/issues/46))
- Rotation name is always non-empty string for `create` and `assign`
  commands

## [0.2.6] - 2022-01-29

### Changed
- `:name` keyword for rotation name in commands arguments renamed to
  `:rotation` ([#44](https://github.com/pilosus/dienstplan/issues/44))

## [0.2.5] - 2022-01-29

### Added
- `assign` command ([#36](https://github.com/pilosus/dienstplan/issues/36)) by [BurlakovNick](https://github.com/BurlakovNick)
- Pretty formatting for usage example in command help messages

## [0.2.4] - 2022-01-23

### Added
- Complex functions specs and instrumenting
  ([#22](https://github.com/pilosus/dienstplan/issues/22))

## [0.2.3] - 2022-01-23

### Added
- Verbosity to `rotate` command added so that previous and current
  on-call persons are displayed
  ([#35](https://github.com/pilosus/dienstplan/issues/35))

## [0.2.2] - 2022-01-16

### Removed
- `ENV` instructions removed from the `Dockerfile` to make sure
  default config values are used if an env is not provided. Use
  `docker run -e YOUR_ENV=YOUR_VAL` to explicitly pass in envs to the
  app in container ([#32](https://github.com/pilosus/dienstplan/issues/32))

## [0.2.1] - 2022-01-15

### Fixed
- Default `uri` in request
  ([#29](https://github.com/pilosus/dienstplan/issues/29))
- Better logging for 500 error handler

## [0.2.0] - 2022-01-15

### Added
- Support for `about` command: `@dienstplan about <rotation name>`
  ([#19](https://github.com/pilosus/dienstplan/issues/19))

## [0.1.4] - 2022-01-15

### Fixed
- `team` id in `app_mention` request context is optional to make it
  compatible with Slack workflow builder
  ([#26](https://github.com/pilosus/dienstplan/issues/26))

### Added
- README badges
- Envs from 0.1.3 added to `Dockerfile` and Docker Compose's `.env`

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

[Unreleased]: https://github.com/pilosus/dienstplan/compare/0.2.9...HEAD
[0.2.9]: https://github.com/pilosus/dienstplan/compare/0.2.8...0.2.9
[0.2.8]: https://github.com/pilosus/dienstplan/compare/0.2.7...0.2.8
[0.2.7]: https://github.com/pilosus/dienstplan/compare/0.2.6...0.2.7
[0.2.6]: https://github.com/pilosus/dienstplan/compare/0.2.5...0.2.6
[0.2.5]: https://github.com/pilosus/dienstplan/compare/0.2.4...0.2.5
[0.2.4]: https://github.com/pilosus/dienstplan/compare/0.2.3...0.2.4
[0.2.3]: https://github.com/pilosus/dienstplan/compare/0.2.2...0.2.3
[0.2.2]: https://github.com/pilosus/dienstplan/compare/0.2.1...0.2.2
[0.2.1]: https://github.com/pilosus/dienstplan/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/pilosus/dienstplan/compare/0.1.4...0.2.0
[0.1.4]: https://github.com/pilosus/dienstplan/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/pilosus/dienstplan/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/pilosus/dienstplan/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/pilosus/dienstplan/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/pilosus/dienstplan/compare/0.0.0...0.1.0
