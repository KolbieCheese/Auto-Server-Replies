# Snarky Server

Paper plugin builds from GitHub Actions are published in two places:

- GitHub Releases on every default-branch build as `SnarkyServer-<version>.jar`
- A matching `SnarkyServer-<version>` artifact on each workflow run in Actions

GitHub's repository source downloads such as `Source code (zip)` and `Source code (tar.gz)` only contain the source files. They do not include the compiled plugin jar.

## Configuration layout (v0.6+)

Snarky Server now uses **three config files** in the plugin data folder:

- `messages.yml`: all death/chat message pools
- `chances.yml`: per-category chance values
- `triggers.yml`: feature toggles, prefix, cooldowns, filters, and chat trigger behavior

`triggers.yml` also carries `config-schema-version` so startup migration only reruns when the config implementation changes.

### Migration notes for existing servers

If you are upgrading from an older build:

1. If the old plugin data folder is `plugins/Snarky Server`, the current `SnarkyServer` build will copy it into `plugins/SnarkyServer` on first boot.
2. If the old setup still uses a single `config.yml`, startup will split that file into `messages.yml`, `chances.yml`, and `triggers.yml` automatically.
3. If the split-config schema has not changed, startup leaves the old legacy folder alone instead of deleting it.
4. Local development builds default to `pluginBaseVersion-SNAPSHOT`, while GitHub Actions increments the numeric `major.minor.patch` version with rollover at 10 per digit.

## External output discovery

Snarky Server can now discover compatible plugin outputs through a manifest-based integration path.

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

If Lightweight Clans ships the manifest above, Snarky Server will discover `lightweightclans:clan_chat`, register a listener for its exported custom event, and feed clan chat messages into the normal chat snark pipeline only when that external output is enabled.
