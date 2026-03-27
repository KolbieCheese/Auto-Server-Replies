# Auto-Server-Replies

Paper plugin builds from GitHub Actions are published in two places:

- GitHub Releases on default-branch builds when `gradle.properties` has a new version
- The `snarkyserver-plugin` artifact on each workflow run in Actions

GitHub's repository source downloads such as `Source code (zip)` and `Source code (tar.gz)` only contain the source files. They do not include the compiled plugin jar.

## Configuration layout (v0.6+)

SnarkyServer now uses **three config files** in the plugin data folder:

- `messages.yml`: all death/chat message pools
- `chances.yml`: per-category chance values
- `triggers.yml`: feature toggles, prefix, cooldowns, filters, and chat trigger behavior

`config.yml` is now deprecated and no longer loaded at runtime.

### Migration notes for existing servers

If you are upgrading from a version that used `config.yml`:

1. Start the plugin once to let it bootstrap `messages.yml`, `chances.yml`, and `triggers.yml`.
2. Copy your existing `messages.*` entries into `messages.yml`.
3. Copy your `death-snark.chance(s)` and `chat-snark.chance(s)` entries into `chances.yml`.
4. Copy top-level behavior keys (`enabled`, `prefix`, `cooldowns`, `filters`, `death-snark.enabled`, `chat-snark.*`) into `triggers.yml`.
5. Keep `config.yml` only as a temporary migration reference, then remove it when finished.
