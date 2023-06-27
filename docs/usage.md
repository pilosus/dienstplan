# Usage

To start interacting with the Slack bot, mention its username, provide
a command and its arguments as follows:

```
@dienstplan <command> [<options>]
```

## Definitions

`Rotation`, or `rota` - a named duty with a duties description and a
list of user mentions in the order of their duty schedule.

`Duty`, or `on-call person`, or `duty person` - a user who is currently an on-call person, i.e. on duty.

`Mention` - a user tagged in Slack (username prepended with the `@`
character). Used interchargably with a word `User` or a phrase `User
mention`.

## Commands

The commands work on the Slack channel basis, meaning that a rotation
must be unique for a channel and cannot be global (available for all
the channels) for a workspace.

### Create

Create a new rotation:

```
@dienstplan create <rotation name> <list of user mentions> <duties description>
```

A rotation must have a unique name within the current Slack channel.

Upon the rota creation, current duty is assigned to the first user in
the given list.

### Rotate

Move the currenty duty to a next user:

```
@dienstplan rotate <rotation name>
```

Duty is rotated in a round-robin cyclic manner. E.g. for a newly
created rota with the following user mentions:

```
user1, user2, user3
```

duty rotation is done in the following order:

```
user1 -> user2 -> user3 -> user1 -> user2 -> ...
```

### Who

Show a current duty along with its duties description:

```
@dienstplan who <rotation name>
```

### Shout

Show a current duty:

```
@dienstplan shout <rotation name>
```

The command is an analogue to the `who` command but with the duties
description omitted.

### Assign

Assign a specific user for a duty:

```
@dienstplan assign <rotation name> <user mention>
```

Used to temporarily overcome the natural order of user mentions for
the rota, e.g. when a current duty is on the sick leave or holidays.

### About

Show details about a rota:

```
@dienstplan about <rotation name>
```

### Delete

Delete a rotation:

```
@dienstplan delete <rotation name>
```

### Update

Update a rotation:

```
@dienstplan update <rotation name> <list of user mentions> <duties description>
```

Watch out! The command overwrites the existing rota so that the
current duty will be assigned to the first user mention as if the
rotation was created anew. Consider `update` command to be a a
shortcut to a sequence of `delete` and `create` commands.

### List

List all rotation names along with their dates of creation for the
current channel:

```
@dienstplan list
```

Watch out! The list is limited to 500 rotations.

### Help

Show a help message for the bot:

```
@dienstplan help
```
