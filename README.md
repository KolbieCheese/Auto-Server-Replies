# Auto-Server-Replies

Paper plugin builds from GitHub Actions are published in two places:

- GitHub Releases on default-branch builds when `gradle.properties` has a new version
- The `snarkyserver-plugin` artifact on each workflow run in Actions

GitHub's repository source downloads such as `Source code (zip)` and `Source code (tar.gz)` only contain the source files. They do not include the compiled plugin jar.
