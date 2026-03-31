package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

final class SnarkyConfigMigrator {
    static final int CONFIG_SCHEMA_VERSION = 1;

    private static final String LEGACY_DATA_FOLDER_NAME = "SnarkyServer";
    private static final String LEGACY_CONFIG_FILE = "config.yml";
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String CHANCES_FILE = "chances.yml";
    private static final String TRIGGERS_FILE = "triggers.yml";
    private static final List<String> MIGRATED_FILENAMES = List.of(
            MESSAGES_FILE,
            CHANCES_FILE,
            TRIGGERS_FILE,
            LEGACY_CONFIG_FILE
    );

    private final Logger logger;

    SnarkyConfigMigrator(Logger logger) {
        this.logger = logger;
    }

    void migrateLegacyDataFolderIfNeeded(Path currentDataFolder) {
        Path parentFolder = currentDataFolder.getParent();
        if (parentFolder == null) {
            return;
        }

        Path legacyDataFolder = parentFolder.resolve(LEGACY_DATA_FOLDER_NAME);
        if (!Files.isDirectory(legacyDataFolder) || legacyDataFolder.equals(currentDataFolder)) {
            return;
        }

        boolean firstSnarkyServerBoot = Stream.of(
                currentDataFolder.resolve(MESSAGES_FILE),
                currentDataFolder.resolve(CHANCES_FILE),
                currentDataFolder.resolve(TRIGGERS_FILE),
                currentDataFolder.resolve(LEGACY_CONFIG_FILE)
        ).noneMatch(Files::exists);
        int currentSchemaVersion = readConfigSchemaVersion(currentDataFolder.resolve(TRIGGERS_FILE));
        int legacySchemaVersion = readConfigSchemaVersion(legacyDataFolder.resolve(TRIGGERS_FILE));
        boolean requiresSchemaMigration = legacySchemaVersion > 0
                && currentSchemaVersion > 0
                && legacySchemaVersion < currentSchemaVersion;

        if (!firstSnarkyServerBoot && !requiresSchemaMigration) {
            return;
        }

        try {
            Files.createDirectories(currentDataFolder);
            for (String filename : MIGRATED_FILENAMES) {
                Path source = legacyDataFolder.resolve(filename);
                if (!Files.exists(source)) {
                    continue;
                }

                Path target = currentDataFolder.resolve(filename);
                if (Files.exists(target) && !requiresSchemaMigration) {
                    continue;
                }

                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            deleteDirectoryRecursively(legacyDataFolder);
            logger.info("Migrated legacy SnarkyServer data folder into Snarky Server data folder.");
        } catch (IOException ioException) {
            logger.log(Level.WARNING, "Failed to migrate legacy SnarkyServer data folder.", ioException);
        }
    }

    boolean needsLegacyCombinedConfigMigration(Path currentDataFolder) {
        Path legacyConfigPath = currentDataFolder.resolve(LEGACY_CONFIG_FILE);
        return Files.isRegularFile(legacyConfigPath)
                && (!Files.isRegularFile(currentDataFolder.resolve(MESSAGES_FILE))
                || !Files.isRegularFile(currentDataFolder.resolve(CHANCES_FILE))
                || !Files.isRegularFile(currentDataFolder.resolve(TRIGGERS_FILE)));
    }

    boolean migrateLegacyCombinedConfig(Path currentDataFolder) throws IOException, InvalidConfigurationException {
        Path legacyConfigPath = currentDataFolder.resolve(LEGACY_CONFIG_FILE);
        if (!Files.isRegularFile(legacyConfigPath)) {
            return false;
        }

        YamlConfiguration legacyConfiguration = loadYaml(legacyConfigPath);
        YamlConfiguration messagesConfiguration = loadYaml(currentDataFolder.resolve(MESSAGES_FILE));
        YamlConfiguration chancesConfiguration = loadYaml(currentDataFolder.resolve(CHANCES_FILE));
        YamlConfiguration triggersConfiguration = loadYaml(currentDataFolder.resolve(TRIGGERS_FILE));

        boolean migrated = false;
        migrated |= copySectionIfPresent(legacyConfiguration, messagesConfiguration, "messages");
        migrated |= copyLegacyChanceValues(legacyConfiguration, chancesConfiguration);
        migrated |= copyLegacyTriggerValues(legacyConfiguration, triggersConfiguration);

        triggersConfiguration.set("config-schema-version", CONFIG_SCHEMA_VERSION);
        messagesConfiguration.save(currentDataFolder.resolve(MESSAGES_FILE).toFile());
        chancesConfiguration.save(currentDataFolder.resolve(CHANCES_FILE).toFile());
        triggersConfiguration.save(currentDataFolder.resolve(TRIGGERS_FILE).toFile());

        if (migrated) {
            Files.deleteIfExists(legacyConfigPath);
            logger.info("Migrated legacy Snarky Server config.yml into split config files.");
        }

        return migrated;
    }

    void ensureSplitConfigSchemaVersion(Path currentDataFolder) throws IOException, InvalidConfigurationException {
        Path triggersPath = currentDataFolder.resolve(TRIGGERS_FILE);
        if (!Files.isRegularFile(triggersPath)) {
            return;
        }

        YamlConfiguration triggersConfiguration = loadYaml(triggersPath);
        if (triggersConfiguration.getInt("config-schema-version", 0) >= CONFIG_SCHEMA_VERSION) {
            return;
        }

        triggersConfiguration.set("config-schema-version", CONFIG_SCHEMA_VERSION);
        triggersConfiguration.save(triggersPath.toFile());
    }

    int readConfigSchemaVersion(Path configPath) {
        if (!Files.isRegularFile(configPath)) {
            return 0;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configPath.toFile());
        return configuration.getInt("config-schema-version", 0);
    }

    private boolean copyLegacyChanceValues(
            YamlConfiguration legacyConfiguration,
            YamlConfiguration chancesConfiguration
    ) {
        boolean migrated = false;
        migrated |= copyGenericChance(legacyConfiguration, chancesConfiguration, "death-snark");
        migrated |= copySectionIfPresent(legacyConfiguration, chancesConfiguration, "death-snark.chances");
        migrated |= copyGenericChance(legacyConfiguration, chancesConfiguration, "chat-snark");
        migrated |= copySectionIfPresent(legacyConfiguration, chancesConfiguration, "chat-snark.chances");
        return migrated;
    }

    private boolean copyGenericChance(
            YamlConfiguration legacyConfiguration,
            YamlConfiguration chancesConfiguration,
            String rootPath
    ) {
        String currentPath = rootPath + ".chances.generic";
        if (legacyConfiguration.isSet(currentPath)) {
            chancesConfiguration.set(currentPath, legacyConfiguration.getDouble(currentPath));
            return true;
        }

        String legacyPath = rootPath + ".chance";
        if (!legacyConfiguration.isSet(legacyPath)) {
            return false;
        }

        chancesConfiguration.set(currentPath, legacyConfiguration.getDouble(legacyPath));
        return true;
    }

    private boolean copyLegacyTriggerValues(
            YamlConfiguration legacyConfiguration,
            YamlConfiguration triggersConfiguration
    ) {
        boolean migrated = false;
        migrated |= copyValueIfSet(legacyConfiguration, triggersConfiguration, "enabled");
        migrated |= copyValueIfSet(legacyConfiguration, triggersConfiguration, "prefix");
        migrated |= copyValueIfSet(legacyConfiguration, triggersConfiguration, "death-snark.enabled");
        migrated |= copyValueIfSet(legacyConfiguration, triggersConfiguration, "chat-snark.enabled");
        migrated |= copyValueIfSet(legacyConfiguration, triggersConfiguration, "chat-snark.min-message-length");
        migrated |= copyValueIfSet(legacyConfiguration, triggersConfiguration, "chat-snark.ignore-commands");
        migrated |= copySectionIfPresent(legacyConfiguration, triggersConfiguration, "chat-snark.spam-burst");
        migrated |= copySectionIfPresent(legacyConfiguration, triggersConfiguration, "cooldowns");
        migrated |= copySectionIfPresent(legacyConfiguration, triggersConfiguration, "filters");
        migrated |= copySectionIfPresent(legacyConfiguration, triggersConfiguration, "external-outputs");
        return migrated;
    }

    private boolean copySectionIfPresent(
            YamlConfiguration sourceConfiguration,
            YamlConfiguration targetConfiguration,
            String sectionPath
    ) {
        ConfigurationSection section = sourceConfiguration.getConfigurationSection(sectionPath);
        if (section == null) {
            return false;
        }

        boolean copiedAny = false;
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }

            targetConfiguration.set(sectionPath + "." + key, value);
            copiedAny = true;
        }
        return copiedAny;
    }

    private boolean copyValueIfSet(
            YamlConfiguration sourceConfiguration,
            YamlConfiguration targetConfiguration,
            String path
    ) {
        if (!sourceConfiguration.isSet(path)) {
            return false;
        }

        targetConfiguration.set(path, sourceConfiguration.get(path));
        return true;
    }

    private YamlConfiguration loadYaml(Path path) throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing required file: " + path);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(path.toFile());
        return configuration;
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
            });
        } catch (RuntimeException runtimeException) {
            if (runtimeException.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw runtimeException;
        }
    }
}
