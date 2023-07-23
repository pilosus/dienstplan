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
3. Make sure all tests pass, code is auto-formated:

```
make all
```

4. Update `CHANGELOG.md`:

- Write your updates under `Unreleased` section
- Add a link to the GitHub issue your code is solving
- Add a link to your GitHub profile for credits


### Release manager

1. Merge branches to `main`

2. Check the current git rev count with `make revcount`

3. Select the next realease [SemVer](https://semver.org/):

- If a major or a minor part to be changed, change manually in `build.clj`
- Patch part is rev count + 1 (assuming 1 commit to prepare the release)

4. Update `CHANGELOG.md`:

- Move changes from `Unreleased` section to a new release section
- Add a link to `git diff`

5. Update current tag mentions in `README.md`:

- Docker version
- Usage examples

6. After release preparation, add a tag with `git tag X.Y.Z`
