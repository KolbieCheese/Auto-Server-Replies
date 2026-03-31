package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnarkyConfigMigratorTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyCombinedConfigIntoSplitFiles() throws Exception {
        Path dataFolder = tempDir.resolve("Snarky Server");
        Files.createDirectories(dataFolder);

        writeFile(dataFolder.resolve("messages.yml"), """
                messages:
                  death-generic:
                    - 'default death'
                  death-freezing:
                    - 'default freezing'
                  chat-generic:
                    - 'default chat'
                """);
        writeFile(dataFolder.resolve("chances.yml"), """
                death-snark:
                  chances:
                    generic: 0.10
                    fire: 0.40
                chat-snark:
                  chances:
                    generic: 0.05
                """);
        writeFile(dataFolder.resolve("triggers.yml"), """
                config-schema-version: 0
                enabled: true
                prefix: <white>[Server] <reset>
                death-snark:
                  enabled: true
                chat-snark:
                  enabled: true
                  min-message-length: 6
                  ignore-commands: true
                  spam-burst:
                    threshold: 3
                    window-seconds: 8
                    max-message-length: 12
                cooldowns:
                  per-player-seconds: 120
                  global-seconds: 20
                filters:
                  bypass-permission: snarkyserver.bypass
                  ignored-worlds: []
                  ignored-prefixes:
                    - '!'
                """);
        writeFile(dataFolder.resolve("config.yml"), """
                enabled: false
                prefix: <gray>[Snark] <reset>
                death-snark:
                  enabled: false
                  chance: 0.25
                  chances:
                    lava: 0.75
                chat-snark:
                  enabled: true
                  chance: 0.15
                  min-message-length: 11
                  ignore-commands: false
                  spam-burst:
                    threshold: 6
                    window-seconds: 30
                    max-message-length: 20
                cooldowns:
                  per-player-seconds: 90
                  global-seconds: 15
                filters:
                  bypass-permission: custom.bypass
                  ignored-worlds:
                    - world_nether
                  ignored-prefixes:
                    - '?'
                messages:
                  death-generic:
                    - 'legacy death'
                  chat-generic:
                    - 'legacy chat'
                """);

        SnarkyConfigMigrator migrator = new SnarkyConfigMigrator(Logger.getLogger("snarky-migrator-test"));

        assertTrue(migrator.migrateLegacyCombinedConfig(dataFolder));

        YamlConfiguration messagesConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("messages.yml").toFile());
        YamlConfiguration chancesConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("chances.yml").toFile());
        YamlConfiguration triggersConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("triggers.yml").toFile());

        assertFalse(Files.exists(dataFolder.resolve("config.yml")));
        assertEquals(List.of("legacy death"), messagesConfiguration.getStringList("messages.death-generic"));
        assertEquals(List.of("legacy chat"), messagesConfiguration.getStringList("messages.chat-generic"));
        assertEquals(List.of("default freezing"), messagesConfiguration.getStringList("messages.death-freezing"));
        assertEquals(0.25D, chancesConfiguration.getDouble("death-snark.chances.generic"));
        assertEquals(0.75D, chancesConfiguration.getDouble("death-snark.chances.lava"));
        assertEquals(0.40D, chancesConfiguration.getDouble("death-snark.chances.fire"));
        assertEquals(0.15D, chancesConfiguration.getDouble("chat-snark.chances.generic"));
        assertFalse(triggersConfiguration.getBoolean("enabled"));
        assertEquals("<gray>[Snark] <reset>", triggersConfiguration.getString("prefix"));
        assertFalse(triggersConfiguration.getBoolean("death-snark.enabled"));
        assertEquals(11, triggersConfiguration.getInt("chat-snark.min-message-length"));
        assertFalse(triggersConfiguration.getBoolean("chat-snark.ignore-commands"));
        assertEquals(6, triggersConfiguration.getInt("chat-snark.spam-burst.threshold"));
        assertEquals(90, triggersConfiguration.getInt("cooldowns.per-player-seconds"));
        assertEquals("custom.bypass", triggersConfiguration.getString("filters.bypass-permission"));
        assertEquals(1, triggersConfiguration.getInt("config-schema-version"));
    }

    @Test
    void migratesLegacySnarkyServerDataFolderOnFirstBoot() throws Exception {
        Path pluginsFolder = tempDir.resolve("plugins");
        Path legacyDataFolder = pluginsFolder.resolve("SnarkyServer");
        Path currentDataFolder = pluginsFolder.resolve("Snarky Server");
        Files.createDirectories(legacyDataFolder);

        writeFile(legacyDataFolder.resolve("messages.yml"), """
                messages:
                  death-generic:
                    - 'legacy death'
                """);
        writeFile(legacyDataFolder.resolve("chances.yml"), """
                death-snark:
                  chances:
                    generic: 0.40
                """);
        writeFile(legacyDataFolder.resolve("triggers.yml"), """
                config-schema-version: 1
                prefix: <gold>[Legacy] <reset>
                """);
        writeFile(legacyDataFolder.resolve("config.yml"), "enabled: true");

        SnarkyConfigMigrator migrator = new SnarkyConfigMigrator(Logger.getLogger("snarky-folder-migrator-test"));

        migrator.migrateLegacyDataFolderIfNeeded(currentDataFolder);

        assertFalse(Files.exists(legacyDataFolder));
        assertTrue(Files.exists(currentDataFolder.resolve("messages.yml")));
        assertTrue(Files.exists(currentDataFolder.resolve("chances.yml")));
        assertTrue(Files.exists(currentDataFolder.resolve("triggers.yml")));
        assertTrue(Files.exists(currentDataFolder.resolve("config.yml")));

        YamlConfiguration triggersConfiguration = YamlConfiguration.loadConfiguration(currentDataFolder.resolve("triggers.yml").toFile());
        assertEquals("<gold>[Legacy] <reset>", triggersConfiguration.getString("prefix"));
    }

    @Test
    void leavesLegacyFolderAloneWhenCurrentConfigAlreadyExistsWithSameSchema() throws Exception {
        Path pluginsFolder = tempDir.resolve("plugins");
        Path legacyDataFolder = pluginsFolder.resolve("SnarkyServer");
        Path currentDataFolder = pluginsFolder.resolve("Snarky Server");
        Files.createDirectories(legacyDataFolder);
        Files.createDirectories(currentDataFolder);

        writeFile(legacyDataFolder.resolve("triggers.yml"), """
                config-schema-version: 1
                prefix: <red>[Legacy] <reset>
                """);
        writeFile(currentDataFolder.resolve("triggers.yml"), """
                config-schema-version: 1
                prefix: <green>[Current] <reset>
                """);

        SnarkyConfigMigrator migrator = new SnarkyConfigMigrator(Logger.getLogger("snarky-folder-guard-test"));

        migrator.migrateLegacyDataFolderIfNeeded(currentDataFolder);

        assertTrue(Files.exists(legacyDataFolder));

        YamlConfiguration triggersConfiguration = YamlConfiguration.loadConfiguration(currentDataFolder.resolve("triggers.yml").toFile());
        assertEquals("<green>[Current] <reset>", triggersConfiguration.getString("prefix"));
    }

    @Test
    void stampsSplitConfigSchemaVersionWithoutChangingExistingValues() throws Exception {
        Path dataFolder = tempDir.resolve("Snarky Server");
        Files.createDirectories(dataFolder);

        writeFile(dataFolder.resolve("triggers.yml"), """
                enabled: false
                prefix: <aqua>[KeepMe] <reset>
                """);

        SnarkyConfigMigrator migrator = new SnarkyConfigMigrator(Logger.getLogger("snarky-schema-test"));

        migrator.ensureSplitConfigSchemaVersion(dataFolder);

        YamlConfiguration triggersConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("triggers.yml").toFile());
        assertFalse(triggersConfiguration.getBoolean("enabled"));
        assertEquals("<aqua>[KeepMe] <reset>", triggersConfiguration.getString("prefix"));
        assertEquals(1, triggersConfiguration.getInt("config-schema-version"));
    }

    private void writeFile(Path path, String contents) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
