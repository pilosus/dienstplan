# Change Log

All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

Nothing here yet.

### Added

- Exception handling and logging added to DB migrate/rollback commands
  ([#117](https://github.com/pilosus/dienstplan/issues/117))

## [1.1.112] - 2024-09-25

### Added

- Support for typographic quotes in schedules
  ([#119](https://github.com/pilosus/dienstplan/issues/119)) by
  [zvymazal](https://github.com/zvymazal)

## [1.1.109] - 2024-05-25

### Added

- Support user groups
  ([#115](https://github.com/pilosus/dienstplan/issues/115)) so that
  they can be used in rotations just like normal users.
- Support `arm64` platform in Docker images
  ([#111](https://github.com/pilosus/dienstplan/issues/111))

## [1.1.98] - 2023-10-22

### Added

- Entrypoint to process schedules as a daemon (background running worker)
  ([#105](https://github.com/pilosus/dienstplan/issues/105))

### Changed

- Java in Docker image updated to Eclipse Temurin 21 JRE
  ([#107](https://github.com/pilosus/dienstplan/issues/107))

## [1.1.94] - 2023-09-03

### Added

- Subcommand `schedule explain <crontab>` to explain a given `crontab`
  in plain English
  ([#102](https://github.com/pilosus/dienstplan/issues/102))

## [1.1.90] - 2023-08-03

### Added

- Explicit schedule execution: scheduling worker sends an extra
  message saying explicitly what command is going to be executed
  ([#97](https://github.com/pilosus/dienstplan/issues/97))

### Changed

- Stop reloading default logging config every 30 seconds
  ([#98](https://github.com/pilosus/dienstplan/issues/98))

## [1.1.89] - 2023-07-31

### Fixed

- Jetty server excluded from the schedule runner
- Reflection warnings fixed

### Changed

- Mount states moved to separate namespaces
- Dependencies bumped

## [1.1.86] - 2023-07-31

### Added

- `schedule` command with
  [vixie-cron](https://man7.org/linux/man-pages/man5/crontab.5.html)
  format for scheduling
  ([#55](https://github.com/pilosus/dienstplan/issues/55))

### Changed

- Log level highlighting for plain text logging

## [1.0.83] - 2023-07-29

### Fixed

- Log entries duplicating removed

## [1.0.82] - 2023-07-23

### Changed
- `SERVER__ROOTLEVEL` is `INFO` by default

## [1.0.81] - 2023-07-23

### Added
- Logging config for JSON structured logging added under
  `resources/logback.json.xml` ([#90](https://github.com/pilosus/dienstplan/issues/90))

### Changed
- `SERVER__LOGLEVEL` and `SERVER__ROOTLEVEL` separated to control app
  log level (app namespace only) and root log level (all loggers,
  including DB, Jetty server, etc.) respectively
  ([#91](https://github.com/pilosus/dienstplan/issues/91))
- Access logs to API endpoints are of `INFO` level
- Release versions use git rev-list count for the patch part (see
  `make revcount`)

## [1.0.0] - 2023-06-27

No breaking changes are expected. Some technical debt eliminated,
documentation website added. Project has made it to the version 1.0.0!

### Changed
- JDBC-access to a database migrated from
  [java.jdbc](https://github.com/clojure/java.jdbc) to
  [next.jdbc](https://github.com/seancorfield/next-jdbc)
  ([#67](https://github.com/pilosus/dienstplan/issues/67)).
- Database connection pooling migrated from
  [HikariCP Clojure wrapper](https://github.com/tomekw/hikari-cp) to original
  [HikariCP for Java](https://github.com/brettwooldridge/HikariCP).
- Outdated dependencies bumped

### Fixed
- Addressed usage example for `assign` command
  ([#60](https://github.com/pilosus/dienstplan/issues/60)).

### Added
- Project documentation [added](https://dienstplan.readthedocs.io/) ([#82](https://github.com/pilosus/dienstplan/issues/82))
- Database connection pool options extended with: `minimumIdle`,
  `connectionTimeout`, `maxLifetime`, `keepaliveTime` (see [docs](https://github.com/brettwooldridge/HikariCP) for more details)
- Developer tools improved: `make` task for bumping outdated deps added

## [0.5.0] - 2023-06-16
### Changed
- Moved from `lein` to Clojure CLI tool for project builing & testing
  ([#68](https://github.com/pilosus/dienstplan/issues/68)).

## [0.4.0] - 2023-06-03
### Added
- `update` command to update rotation's list of mentions and
  description. Watch out! The command overrides current on-call person
  even if the new list of mentions is the same as in original
  rotation. Consider the command to be a shortcut to a sequence of
  `delete` and `create` commands for the same rotation name.
  ([#66](https://github.com/pilosus/dienstplan/issues/66))

### Changed
- Local development migrated to `docker-compose` v2.

## [0.3.0] - 2022-10-30
### Changed
- Base docker images for production and testing moved to Linux
  Alpine-based with Eclipse Temurin 17 JRE to reduce container size
  ([#63](https://github.com/pilosus/dienstplan/issues/63))

## [0.2.12] - 2022-05-21

### Added
- `shout` command used to mention current on-call person. The command
  is very much like `who`, but with duties description omitted.

## [0.2.11] - 2022-02-20

### Added
- README badges with
  [lines of code](https://en.wikipedia.org/wiki/Source_lines_of_code)
  and [hits of code](https://www.yegor256.com/2014/11/14/hits-of-code.html)

### Changed
- Move DB functions to
  [HoneySQL](https://github.com/seancorfield/honeysql)
  ([#54](https://github.com/pilosus/dienstplan/issues/54))

## [0.2.10] - 2022-02-19

### Added
- More integration tests for middlewares (unhandled exceptions) and
  commands edge cases (duplicate key).

### Changed
- Integration test refactoring: reuse http request params
  ([#51](https://github.com/pilosus/dienstplan/issues/51))

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

[Unreleased]: https://github.com/pilosus/dienstplan/compare/1.1.112...HEAD
[1.1.112]: https://github.com/pilosus/dienstplan/compare/1.1.109...1.1.112
[1.1.109]: https://github.com/pilosus/dienstplan/compare/1.1.98...1.1.109
[1.1.98]: https://github.com/pilosus/dienstplan/compare/1.0.94...1.1.98
[1.1.94]: https://github.com/pilosus/dienstplan/compare/1.0.90...1.1.94
[1.1.90]: https://github.com/pilosus/dienstplan/compare/1.0.89...1.1.90
[1.1.89]: https://github.com/pilosus/dienstplan/compare/1.0.86...1.1.89
[1.1.86]: https://github.com/pilosus/dienstplan/compare/1.0.83...1.1.86
[1.0.83]: https://github.com/pilosus/dienstplan/compare/1.0.82...1.0.83
[1.0.82]: https://github.com/pilosus/dienstplan/compare/1.0.81...1.0.82
[1.0.81]: https://github.com/pilosus/dienstplan/compare/1.0.0...1.0.81
[1.0.0]: https://github.com/pilosus/dienstplan/compare/0.5.0...1.0.0
[0.5.0]: https://github.com/pilosus/dienstplan/compare/0.4.0...0.5.0
[0.4.0]: https://github.com/pilosus/dienstplan/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/pilosus/dienstplan/compare/0.2.12...0.3.0
[0.2.12]: https://github.com/pilosus/dienstplan/compare/0.2.11...0.2.12
[0.2.11]: https://github.com/pilosus/dienstplan/compare/0.2.10...0.2.11
[0.2.10]: https://github.com/pilosus/dienstplan/compare/0.2.9...0.2.10
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
