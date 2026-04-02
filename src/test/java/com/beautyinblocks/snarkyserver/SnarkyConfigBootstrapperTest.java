package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnarkyConfigBootstrapperTest {
    @TempDir
    Path tempDir;

    @Test
    void recreatesMissingSplitFilesAndMigratesLegacyConfig() throws Exception {
        Path dataFolder = tempDir.resolve("SnarkyServer");
        Files.createDirectories(dataFolder);
        writeFile(dataFolder.resolve("config.yml"), """
                enabled: false
                prefix: <gray>[Snark] <reset>
                death-snark:
                  enabled: false
                  chance: 0.25
                chat-snark:
                  enabled: true
                  chance: 0.15
                messages:
                  death-generic:
                    - 'legacy death'
                  chat-generic:
                    - 'legacy chat'
                """);

        SnarkyConfigBootstrapper bootstrapper = bootstrapper();

        List<String> recreatedFiles = bootstrapper.prepareDataFolder(dataFolder);

        assertEquals(List.of("messages.yml", "chances.yml", "triggers.yml"), recreatedFiles);
        assertFalse(Files.exists(dataFolder.resolve("config.yml")));

        YamlConfiguration messagesConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("messages.yml").toFile());
        YamlConfiguration chancesConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("chances.yml").toFile());
        YamlConfiguration triggersConfiguration = YamlConfiguration.loadConfiguration(dataFolder.resolve("triggers.yml").toFile());

        assertEquals(List.of("legacy death"), messagesConfiguration.getStringList("messages.death-generic"));
        assertEquals(List.of("legacy chat"), messagesConfiguration.getStringList("messages.chat-generic"));
        assertEquals(0.25D, chancesConfiguration.getDouble("death-snark.chances.generic"));
        assertEquals(0.15D, chancesConfiguration.getDouble("chat-snark.chances.generic"));
        assertFalse(triggersConfiguration.getBoolean("enabled"));
        assertEquals(1, triggersConfiguration.getInt("config-schema-version"));
    }

    @Test
    void recreatesSplitFilesWithoutRecreatingDeprecatedLegacyConfig() throws Exception {
        Path dataFolder = tempDir.resolve("SnarkyServer");

        List<String> recreatedFiles = bootstrapper().prepareDataFolder(dataFolder);

        assertEquals(List.of("messages.yml", "chances.yml", "triggers.yml"), recreatedFiles);
        assertTrue(Files.isRegularFile(dataFolder.resolve("messages.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("chances.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("triggers.yml")));
        assertFalse(Files.exists(dataFolder.resolve("config.yml")));
    }

    private SnarkyConfigBootstrapper bootstrapper() {
        return new SnarkyConfigBootstrapper(
                new SnarkyConfigMigrator(Logger.getLogger("snarky-bootstrapper-test")),
                (resourceName, targetPath) -> writeDefaultResource(resourceName, targetPath),
                Logger.getLogger("snarky-bootstrapper-test")
        );
    }

    private void writeDefaultResource(String resourceName, Path targetPath) throws IOException {
        String contents = switch (resourceName) {
            case "messages.yml" -> """
                    messages:
                      death-generic:
                        - 'default death'
                      chat-generic:
                        - 'default chat'
                    """;
            case "chances.yml" -> """
                    death-snark:
                      chances:
                        generic: 0.025
                    chat-snark:
                      chances:
                        generic: 0.0125
                    """;
            case "triggers.yml" -> """
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
                      ignored-prefixes: []
                    external-outputs: {}
                    """;
            default -> throw new IllegalArgumentException("Unexpected resource: " + resourceName);
        };
        writeFile(targetPath, contents);
    }

    private void writeFile(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
