package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

final class SnarkyConfigBootstrapper {
    static final String MESSAGES_FILE = "messages.yml";
    static final String CHANCES_FILE = "chances.yml";
    static final String TRIGGERS_FILE = "triggers.yml";
    static final List<String> SPLIT_CONFIG_FILES = List.of(
            MESSAGES_FILE,
            CHANCES_FILE,
            TRIGGERS_FILE
    );

    private final SnarkyConfigMigrator configMigrator;
    private final ResourceWriter resourceWriter;
    private final Logger logger;

    SnarkyConfigBootstrapper(
            SnarkyConfigMigrator configMigrator,
            ResourceWriter resourceWriter,
            Logger logger
    ) {
        this.configMigrator = configMigrator;
        this.resourceWriter = resourceWriter;
        this.logger = logger;
    }

    List<String> prepareDataFolder(Path dataFolder) throws IOException, InvalidConfigurationException {
        configMigrator.migrateLegacyDataFolderIfNeeded(dataFolder);
        Files.createDirectories(dataFolder);

        boolean hasLegacyCombinedConfig = Files.isRegularFile(dataFolder.resolve("config.yml"));
        boolean missingSplitFileAtStart = false;
        List<String> recreatedFiles = new ArrayList<>();
        for (String fileName : SPLIT_CONFIG_FILES) {
            Path filePath = dataFolder.resolve(fileName);
            if (Files.isRegularFile(filePath)) {
                continue;
            }

            missingSplitFileAtStart = true;
            resourceWriter.writeMissing(fileName, filePath);
            recreatedFiles.add(fileName);
        }

        if (!recreatedFiles.isEmpty()) {
            logger.info("Recreated missing Snarky Server config file(s): " + String.join(", ", recreatedFiles));
        }

        if (hasLegacyCombinedConfig && missingSplitFileAtStart) {
            configMigrator.migrateLegacyCombinedConfig(dataFolder);
        }

        configMigrator.ensureSplitConfigSchemaVersion(dataFolder);
        return List.copyOf(recreatedFiles);
    }

    @FunctionalInterface
    interface ResourceWriter {
        void writeMissing(String resourceName, Path targetPath) throws IOException;
    }
}
