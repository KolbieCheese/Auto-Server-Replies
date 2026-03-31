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

## External output discovery

SnarkyServer can now discover compatible plugin outputs through a manifest-based integration path.

- Snarky scans loaded plugins for `META-INF/snarky-outputs.json` at startup.
- Snarky scans again when another plugin is enabled later.
- Only outputs explicitly declared in that manifest are considered compatible.
- Newly discovered external outputs are added to `triggers.yml` under `external-outputs` and default to `enabled: false`.

Example manifest:

```json
{
  "plugin": "LightweightClans",
  "version": 1,
  "outputs": [
    {
      "id": "lightweightclans:clan_chat",
      "displayName": "Lightweight Clans - Clan Chat",
      "eventClass": "fully.qualified.ClanChatMessageEvent",
      "kind": "chat",
      "description": "Clan chat messages from Lightweight Clans"
    }
  ]
}
```

Discovered outputs are mirrored into `triggers.yml` like this:

```yml
external-outputs:
  lightweightclans:clan_chat:
    enabled: false
    display-name: Lightweight Clans - Clan Chat
    source-plugin: LightweightClans
    kind: chat
    event-class: fully.qualified.ClanChatMessageEvent
    description: Clan chat messages from Lightweight Clans
```

You can enable or disable discovered outputs by editing `triggers.yml`. `/snarktest list` also shows built-in outputs plus any discovered external outputs and whether each one is enabled.

### Lightweight Clans

If Lightweight Clans ships the manifest above, SnarkyServer will discover `lightweightclans:clan_chat`, register a listener for its exported custom event, and feed clan chat messages into the normal chat snark pipeline only when that external output is enabled.
