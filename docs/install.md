# Installation

## Server requirements

- `Docker` **or** `Java 17` or higher
- PostgreSQL 9.4 or higher
- (Optionally) [Sentry account](https://sentry.io/) for error tracking

NB! Lower versions may work too, but never tested.

## Environment variables

The app relies on the following environment variables (envs) to operate:

- `APP__VERSION` - app version, used in Sentry reporting
- `APP__ENV` [default `production`] - app environment (e.g. `test`, `stage`, `production`), used in Sentry reporting
- `SLACK__TOKEN` [default `Token`] - Slack Bot User OAuth Token (see details in the `Slack settings` section)
- `SLACK__SIGN` [default `Secret`] - Slack Signing Secret key (see details in the `Slack settings` section)
- `ALERTS__SENTRY_DSN` - Sentry [data source name](https://docs.sentry.io/product/sentry-basics/dsn-explainer/) [default `https://public:private@localhost/1`]
- `SERVER__PORT` [default `8080`] - Jetty application server port
- `SERVER__LOGLEVEL` [default `INFO`] - App log level (`dienstplan` logger only)
- `SERVER__ROOTLEVEL` [default `INFO`] - Root log level (all loggers, including DB, Jetty server, etc.)
- `SERVER__ACCESS_LOG` [default `true`] - Enable access logging? Access logs have `INFO` level.
- `DB__SERVER_NAME` [default `localhost`] - PostgreSQL server host name
- `DB__PORT_NUMBER` [default `5432`] - PostgreSQL server port number
- `DB__DATABASE_NAME` [default `dienstplan`] - PostgreSQL server database name
- `DB__USERNAME` [default `dienstplan`] - PostgreSQL server user name
- `DB__PASSWORD` [default `dienstplan`] - PostgreSQL server password
- `DB__POOL_MIN_IDLE` [default `20`] - PostgreSQL connection pool's min number of idle connections
- `DB__POOL_MAX_SIZE` [default `20`] - PostgreSQL connection pool's max number of connections
- `DB__TIMEOUT_MS_CONNECTION` [default `10000`] - PostgreSQL connection timeout in milliseconds
- `DB__LIFETIME_MAX_MS_CONNECTION` [default `1800000`] - Maximum lifetime of a connection in the pool in milliseconds
- `DB__LIFETIME_KEEPALIVE_MS_CONNECTION` [default `0`] - Keep alive in milliseconds for idle connections in the pool

## Deployment to production

`dienstplan` is a Clojure program that can be deployed as:

- a `Docker` container
- `jar` or `uberjar` file

### Docker

The easiest option to run an app is by using a [Docker
image](https://hub.docker.com/r/pilosus/dienstplan/):

```bash
$ docker pull pilosus/dienstplan:X.Y.Z

$ docker run \
  -e APP__VERSION="X.Y.Z" \
  -e APP__ENV="production" \
  -e APP__DEBUG=false \
  -e SLACK__TOKEN="xoxb-Your-Bot-User-OAuth-Token" \
  -e SLACK__SIGN="Your-Signing-Secret" \
  -e ALERTS__SENTRY_DSN="https://public:private@localhost/1" \
  -e SERVER__PORT=8080 \
  -e SERVER__LOGLEVEL=INFO \
  -e DB__SERVER_NAME=your-postgresql.example.com \
  -e DB__PORT_NUMBER=5432 \
  -e DB__DATABASE_NAME="your-postgresql-db-name" \
  -e DB__USERNAME="your-postgresql-db-username" \
  -e DB__PASSWORD="your-postgresql-db-passwords" \
  -it --rm pilosus/dienstplan:X.Y.Z \
  java -jar app.jar --mode server
```

For database migrations and rollbacks instead of `java -jar app.jar
--mode server` entypoint use:

- `java -jar app.jar --mode migrate`
- `java -jar app.jar --mode rollback`

It's recommended to use a SemVer tag matching the [latest
release](https://github.com/pilosus/dienstplan/releases) for a Docker
image (e.g. `pilosus/dienstplan:X.Y.Z`). Do not rely on the
`pilosus/dienstplan:latest` unless you know what you are doing!

### Jar file

- Get [Clojure](https://www.clojure.org/guides/install_clojure) to compile a standalone `jar` file
- Clone the [GitHub repository](https://github.com/pilosus/dienstplan) with `git clone git@github.com:pilosus/dienstplan.git`
- In the repo directory complile a standalone `jar` file with `make uberjar`
- Run the app:

```bash
$ APP__DEBUG=false \
  SLACK__TOKEN="xoxb-Your-Bot-User-OAuth-Token" \
  SLACK__SIGN="Your-Signing-Secret" \
  ALERTS__SENTRY_DSN="https://public:private@localhost/1" \
  SERVER__PORT=8080 \
  SERVER__LOGLEVEL=INFO \
  DB__SERVER_NAME=your-postgresql.example.com \
  DB__PORT_NUMBER=5432 \
  DB__DATABASE_NAME="your-postgresql-db-name" \
  DB__USERNAME="your-postgresql-db-username" \
  DB__PASSWORD="your-postgresql-db-passwords" \
  java -jar /path/to/repo/target/uberjar/dienstplan-X.Y.Z-standalone.jar
```

For database migrations and rollbacks instead of `java -jar dienstplan-X.Y.Z-standalone.jar` entypoint use:

- `java -jar dienstplan-X.Y.Z-standalone.jar --mode migrate`
- `java -jar dienstplan-X.Y.Z-standalone.jar --mode rollback`

### Ansible Playbook

You can get a full set of installation scripts needed to:

- Provision a GNU/Linux server from scratch
- Set up the `dienstplan` bot app as a `systemd` service
- Apply database migrations automatically as a `systemd` service
- Run the app

in the [dienstplan-deploy](https://github.com/pilosus/dienstplan-deploy/) repository.

## Running locally

The app can be run locally with `Docker Compose` with:

```bash
make build
make up
make migrate
```

These will build a Docker container, start the app and the database
locally, and apply database migrations.

Another way to run the app is with Clojure CLI:

```bash
clojure M:run
```

Don't forget to use envs to configure the app properly.

## Extra configs

Java system properties allow to configure some extra behaviours of the
app.  System properties can be passed as an option
`-Dproperty.name=property.value` in `java` invocation:

```bash
java -Dproperty.name=property.value -jar app.jar
```

including in Docker entrypoints:

```bash
docker run -it --rm pilosus/dienstplan:X.Y.Z java -Dproperty.name=property.value -jar app.jar
```

### Logging

By default, logs are printed to the standard output in plain text
format. It may be optimal for local development or staging
environment, but structured logging with JSON stream of events suits
best production grade installations. To enable logging in JSON format
use the following system props:

```bash
java -Dlogback.configurationFile=resources/logback.json.xml ...
```

Custom logging configs can be written and passed in to the app using
the same system property. See the [logback
manual](https://logback.qos.ch/manual/configuration.html) for more
details.

### Socket Server

Clojure allows to start a [socket
server](https://clojure.org/reference/repl_and_main#_launching_a_socket_server)
at initialization using system properties:

```bash
java -Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl} ..."
```

See the
[guide](https://blog.pilosus.org/posts/2023/07/07/debugging-and-patching-clojure-code-running-in-production-using-socket-server-and-repl/)
for more details.


## Slack settings

In order to install the app in your Slack workspace, do the following:

- Sign in to your Slack [Apps Dashboard](https://api.slack.com/apps)
- `Create New App` -> `From an app manifest` -> `Workspace: your workspace`
- Copy and paste the app manifest in YAML format:

```yaml
_metadata:
  major_version: 1
  minor_version: 1
display_information:
  name: dienstplan
  description: Slack bot for duty rotations
  background_color: "#002087"
features:
  bot_user:
    display_name: dienstplan
    always_online: false
oauth_config:
  scopes:
    bot:
      - app_mentions:read
      - channels:read
      - chat:write
      - chat:write.customize
settings:
  event_subscriptions:
    request_url: https://YOUR-DOMAIN/api/events
    bot_events:
      - app_mention
  org_deploy_enabled: false
  socket_mode_enabled: false
  token_rotation_enabled: false
```

- Fix `settings -> event_subscriptions -> request_url` to match your server's public url (`/api/events` url path is hardcoded in the app)
- `Basic Information` -> `Install your app` -> `Install to Workspace`
- `OAuth & Permissions` -> copy `OAuth Tokens for Your Workspace` to be used for `SLACK__TOKEN` environment variable
- `Basic Information` -> `App Credentials` -> copy `Signing Secret` to be used for `SLACK__SIGN` environment variable
- Make it looking nice: `Basic Information` -> `Display Information` -> upload an [app icon](https://openclipart.org/detail/233274/circle-arrow) with a public domain license
- Deploy the app
