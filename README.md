# dienstplan

[![codecov](https://codecov.io/gh/pilosus/dienstplan/branch/main/graph/badge.svg?token=2ouqzEwhLc)](https://codecov.io/gh/pilosus/dienstplan)
[![Docker Image Version (latest semver)](https://img.shields.io/docker/v/pilosus/dienstplan?label=docker%20image&sort=semver)](https://hub.docker.com/r/pilosus/dienstplan)

Slack bot for duty rotations.


## Why

- Dead simple: a few commands to manage on-call duty rotations in your team's Slack channels
- Follows the rule "Do One Thing and Do It Well"
- Plays nicely with Slack [reminders](https://slack.com/resources/using-slack/how-to-use-reminders-in-slack) and [workflows](https://slack.com/features/workflow-automation)


## Usage example

[![screencast](https://blog.pilosus.org/images/dienstplan.gif "Watch the screencast on YouTube")](https://youtu.be/pZWJYpsT1_w)

Let's create a rotation using `dienstplan`. Just pass in a `create`
command followed by a rotation name, a list of the channel users in a
rotation, and a rotation description:

```
@dienstplan create my-rota @user1 @user2 @user3
On-call engineer's duties:
- Process support team questions queue
- Resolve service alerts
- Check service health metrics
- Casual code refactoring-
 Follow the boy scout rule: always leave the campground cleaner than you found it
```

Once the rota is set up, the first user in the list becomes a current
on-call person. Check it with a `who` command:

```
@dienstplan who my-rota
```

To change the current on-call person to the next one use `rotate`
command:

```
@dienstplan rotate my-rota
```

The bot iterates over the users in the list order:

```
@user1 -> @user2 ->  @user3 -> @user1 ...
```

Now that you know the basics, let's automate rotation and current duty
notifications with Slack's built-in `/remind` command. First, set up a
reminder to rotate users weekly:

```
/remind #my-channel to "@dienstplan rotate my-rota" every Monday at 9AM UTC
```

Second, remind duties to a current on-call person:

```
/remind #my-channel to "@dienstplan who my-rota" every Monday, Tuesday, Wednesday, Thursday, Friday at 10AM UTC
```

### Bot commands

To start interacting with the bot, mention its username, provide a
command and its arguments as follows:

```
@dienstplan <command> [<options>]
```

Commands:

1. Create a rotation
```
@dienstplan create <rotation name> <list of users> <duties description>
```

2. Rotate: move current duty to a next user
```
@dienstplan rotate <rotation name>
```

3. Show a current duty
```
@dienstplan who <rotation name>
```

4. Show details about a rotation
```
@dienstplan about <rotation name>
```

5. Delete a rotation
```
@dienstplan delete <rotation name>
```

6. List channel's rotations
```
@dienstplan list
```

7. Show a help message
```
@dienstplan help
```


## Install

### Server requirements

- Any server with Java 17 or higher (tested with OpenJDK 17)
- PostgreSQL 9.4 or higher (tested with PostgreSQL 13)
- (Optionally) [Sentry account](https://sentry.io/) for error tracking

### Environment variables

The app relies on a bunch of environment variables (envs) to operate:

- `APP__VERSION` - app version, used in Sentry reporting
- `APP__ENV` [default `production`] - app environment (e.g. `test`, `stage`, `production`), used in Sentry reporting
- `SLACK__TOKEN` - Slack Bot User OAuth Token
- `SLACK__SIGN` - Slack Signing Secret key
- `ALERTS__SENTRY_DSN` - Sentry config string
- `SERVER__PORT` [default `8080`] - Jetty application server port
- `SERVER__LOGLEVEL` [default `INFO`] - Server log level
- `SERVER__ACCESS_LOG` [default `true`] - Server basic access logging
- `DB__SERVER_NAME` - PostgreSQL server host name
- `DB__PORT_NUMBER` [default `5432`] - PostgreSQL server port number
- `DB__DATABASE_NAME` - PostgreSQL server database name
- `DB__USERNAME` - PostgreSQL server user name
- `DB__PASSWORD` - PostgreSQL server password
- `DB__POOL_MIN_IDLE` [default `10`] - PostgreSQL connection pool's min number of idle connections
- `DB__POOL_MAX_SIZE` [default `20`] - PostgreSQL connection pool's max number of connections
- `DB__TIMEOUT_MS_CONNECTION` [default `10000`] - PostgreSQL connection timeout in milliseconds

### Up & Running

- Get [leiningen](https://leiningen.org/) to compile the app
- Clone the GitHub repository
- In the repo directory complile a standalone `jar` file with `lein uberjar`
- Run the app (**NB** fix the app version as per `project.clj` file):

```
APP__DEBUG=false \
SLACK__TOKEN="xoxb-Your-Bot-User-OAuth-Token" \
SLACK__SIGN="Your-Signing-Secret" \
ALERTS__SENTRY_DSN="https://your_token@something.sentry.io/project" \
SERVER__PORT=8080 \
SERVER__LOGLEVEL=INFO \
DB__SERVER_NAME=your-postgresql.example.com \
DB__PORT_NUMBER=5432 \
DB__DATABASE_NAME="your-postgresql-db-name" \
DB__USERNAME="your-postgresql-db-username" \
DB__PASSWORD="your-postgresql-db-passwords" \
java -jar /path/to/repo/target/uberjar/dienstplan-0.1.0-standalone.jar
```
- Apply database migrations with the `--mode migrate` option or rollback with `--mode rollback`, e.g.:

```
java -jar /path/to/repo/target/uberjar/dienstplan-0.1.0-standalone.jar --mode migrate
```

Alternatively, use containerized app version as follows:

```
docker pull pilosus/dienstplan

docker run \
  -e APP__VERSION="0.1.0" \
  -e APP__ENV="production" \
  -e APP__DEBUG=false \
  -e SLACK__TOKEN="xoxb-Your-Bot-User-OAuth-Token" \
  -e SLACK__SIGN="Your-Signing-Secret" \
  -e ALERTS__SENTRY_DSN="https://your_token@something.sentry.io/project" \
  -e SERVER__PORT=8080 \
  -e SERVER__LOGLEVEL=INFO \
  -e DB__SERVER_NAME=your-postgresql.example.com \
  -e DB__PORT_NUMBER=5432 \
  -e DB__DATABASE_NAME="your-postgresql-db-name" \
  -e DB__USERNAME="your-postgresql-db-username" \
  -e DB__PASSWORD="your-postgresql-db-passwords" \
  -it --rm pilosus/dienstplan \
  java -jar app.jar --mode server
  # use --mode migrate or --mode rollback for DB migration control
```

Docker compose is also supported with:

```
docker-compose up
```

or use `Makefile` to ease building, running services and applying DB migrations:

```
make all
```

### Server Setup & App Deploy Scripts

You can get a full set of scripts needed to:

- provision a server from scratch
- set up the bot app
- deploy the app

in the [dienstplan-deploy](https://github.com/pilosus/dienstplan-deploy/) repository.

### Install the bot app in Slack

- Sign in to your Slack [Apps Dashboard](https://api.slack.com/apps)
- `Create New App` -> `From an app manifest` -> `Workspace: your workspace`
- Copy and paste the app manifest in YAML format:

```
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
- `OAuth & Permissions` -> copy `OAuth Tokens for Your Workspace` to be used for `SLACK__TOKEN` env
- `Basic Information` -> `App Credentials` -> copy `Signing Secret` to be used for `SLACK__SIGN` env
- Make it looking nice: `Basic Information` -> `Display Information` -> upload an [app icon](https://openclipart.org/detail/233274/circle-arrow) with a public domain license
- Deploy the app with

## License

Copyright (c) 2021-2022 Vitaly Samigullin and contributors. All rights reserved.

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
