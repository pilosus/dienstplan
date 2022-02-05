# Contributing

`dienstplan` is a simple app and always will be. It's been designed
for on-call rotation in a cyclic, round-robin manner. Technially, the
project follows the rule "Do One Thing and Do It Well". So we don't
expect any new big features like calendar or CRM integrations. That's
why contributing principles are simple too:

### Ask first

Before writing any code, please submit a GitHub issue first. Let's
discuss if a proposed feature fits into project plans. Same with bugs:
report first, get assigned to the issue, then submit the code.

### Pull requests

1. Follow a [GitHub flow](https://docs.github.com/en/get-started/quickstart/github-flow)
2. Cover your code with tests
3. Format your code with linters:

```
lein cljfmt fix
```

4. Update `CHANGELOG.md`:

- Write your updates under `Unreleased` section
- Add a link to the GitHub issue your code is solving
- Add a link to your GitHub profile for credits
