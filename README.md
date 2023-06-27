# dienstplan: slack duty rotations made easy

[![codecov](https://codecov.io/gh/pilosus/dienstplan/branch/main/graph/badge.svg?token=2ouqzEwhLc)](https://codecov.io/gh/pilosus/dienstplan)
[![Docker version](https://img.shields.io/docker/v/pilosus/dienstplan/1.0.0?logo=docker&label=Docker)](https://hub.docker.com/r/pilosus/dienstplan/tags)

Slack bot for duty rotations.

## Why

- Dead simple: a few commands to manage on-call duty rotations in your team's Slack channels
- Follows the rule "Do One Thing and Do It Well"
- Plays nicely with Slack [reminders](https://slack.com/resources/using-slack/how-to-use-reminders-in-slack) and [workflows](https://slack.com/features/workflow-automation)

## Quick example

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
@user1 -> @user2 ->  @user3 -> @user1 -> @user2 ...
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

## Help

See [documentation](https://dienstplan.readthedocs.io/) for more details on
installation and usage.

## Contributing

See
[Contributing](https://github.com/pilosus/dienstplan/tree/main/CONTRIBUTING.md)
guide.

## License

See [LICENSE](https://github.com/pilosus/dienstplan/tree/main/LICENSE)
