package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnarkyConfigLoader {
    private static final double DEFAULT_DEATH_CHANCE = 0.20D;
    private static final double DEFAULT_CHAT_CHANCE = 0.05D;
    private static final String MESSAGE_ROOT_PATH = "messages";
    private static final String DEATH_CHANCES_ROOT_PATH = "death-snark.chances";
    private static final String CHAT_CHANCES_ROOT_PATH = "chat-snark.chances";

    private SnarkyConfigLoader() {
    }

    public static SnarkyConfig load(FileConfiguration configuration) {
        return load(configuration, configuration, configuration);
    }

    public static SnarkyConfig load(
            FileConfiguration messagesConfiguration,
            FileConfiguration chancesConfiguration,
            FileConfiguration triggersConfiguration
    ) {
        double deathGenericChance = getChance(
                chancesConfiguration,
                deathChancePath(DeathCategory.GENERIC),
                "death-snark.chance",
                DEFAULT_DEATH_CHANCE
        );
        double chatGenericChance = getChance(
                chancesConfiguration,
                chatChancePath(ChatCategory.GENERIC),
                "chat-snark.chance",
                DEFAULT_CHAT_CHANCE
        );

        return new SnarkyConfig(
                triggersConfiguration.getString("prefix", ""),
                loadMessages(messagesConfiguration),
                new SnarkChancesConfig(
                        loadDeathChances(chancesConfiguration, deathGenericChance),
                        loadChatChances(chancesConfiguration, chatGenericChance)
                ),
                new SnarkTriggersConfig(
                        triggersConfiguration.getBoolean("enabled", true),
                        new SnarkTriggersConfig.DeathSnark(
                                triggersConfiguration.getBoolean("death-snark.enabled", true)
                        ),
                        new SnarkTriggersConfig.ChatSnark(
                                triggersConfiguration.getBoolean("chat-snark.enabled", true),
                                triggersConfiguration.getInt("chat-snark.min-message-length", 6),
                                triggersConfiguration.getBoolean("chat-snark.ignore-commands", true),
                                new SnarkTriggersConfig.ChatSnark.SpamBurst(
                                        triggersConfiguration.getInt("chat-snark.spam-burst.threshold", 3),
                                        triggersConfiguration.getInt("chat-snark.spam-burst.window-seconds", 8),
                                        triggersConfiguration.getInt("chat-snark.spam-burst.max-message-length", 12)
                                )
                        ),
                        new SnarkTriggersConfig.Cooldowns(
                                triggersConfiguration.getInt("cooldowns.per-player-seconds", 120),
                                triggersConfiguration.getInt("cooldowns.global-seconds", 20)
                        ),
                        new SnarkTriggersConfig.Filters(
                                triggersConfiguration.getString("filters.bypass-permission", "snarkyserver.bypass"),
                                triggersConfiguration.getStringList("filters.ignored-worlds"),
                                triggersConfiguration.getStringList("filters.ignored-prefixes")
                        ),
                        loadExternalOutputs(triggersConfiguration)
                )
        );
    }

    public static SnarkMessagesConfig loadMessages(FileConfiguration configuration) {
        return new SnarkMessagesConfig(
                loadDeathMessages(configuration),
                loadChatMessages(configuration)
        );
    }

    private static Map<DeathCategory, Double> loadDeathChances(FileConfiguration configuration, double defaultChance) {
        EnumMap<DeathCategory, Double> chances = new EnumMap<>(DeathCategory.class);
        for (DeathCategory category : DeathCategory.values()) {
            chances.put(category, getChance(configuration, deathChancePath(category), defaultChance));
        }
        return chances;
    }

    private static Map<ChatCategory, Double> loadChatChances(FileConfiguration configuration, double defaultChance) {
        EnumMap<ChatCategory, Double> chances = new EnumMap<>(ChatCategory.class);
        for (ChatCategory category : ChatCategory.values()) {
            chances.put(category, getChance(configuration, chatChancePath(category), defaultChance));
        }
        return chances;
    }

    private static Map<DeathCategory, List<String>> loadDeathMessages(FileConfiguration configuration) {
        EnumMap<DeathCategory, List<String>> messages = new EnumMap<>(DeathCategory.class);
        List<String> generic = listOrFallback(
                configuration,
                deathMessagePath(DeathCategory.GENERIC),
                null
        );
        messages.put(DeathCategory.GENERIC, generic);

        for (DeathCategory category : DeathCategory.values()) {
            if (category == DeathCategory.GENERIC) {
                continue;
            }
            messages.put(
                    category,
                    listOrFallback(
                            configuration,
                            deathMessagePath(category),
                            deathMessagePath(DeathCategory.GENERIC)
                    )
            );
        }

        return messages;
    }

    private static Map<ChatCategory, List<String>> loadChatMessages(FileConfiguration configuration) {
        EnumMap<ChatCategory, List<String>> messages = new EnumMap<>(ChatCategory.class);
        List<String> generic = listOrFallback(
                configuration,
                chatMessagePath(ChatCategory.GENERIC),
                null
        );
        messages.put(ChatCategory.GENERIC, generic);

        for (ChatCategory category : ChatCategory.values()) {
            if (category == ChatCategory.GENERIC) {
                continue;
            }
            messages.put(
                    category,
                    listOrFallback(
                            configuration,
                            chatMessagePath(category),
                            chatMessagePath(ChatCategory.GENERIC)
                    )
            );
        }

        return messages;
    }

    private static String deathMessagePath(DeathCategory category) {
        return MESSAGE_ROOT_PATH + "." + category.messageKey();
    }

    private static String chatMessagePath(ChatCategory category) {
        return MESSAGE_ROOT_PATH + "." + category.messageKey();
    }

    private static String deathChancePath(DeathCategory category) {
        return DEATH_CHANCES_ROOT_PATH + "." + category.chanceKey();
    }

    private static String chatChancePath(ChatCategory category) {
        return CHAT_CHANCES_ROOT_PATH + "." + category.chanceKey();
    }

    private static double getChance(FileConfiguration configuration, String path, double fallback) {
        return configuration.isSet(path) ? configuration.getDouble(path, fallback) : fallback;
    }

    private static double getChance(
            FileConfiguration configuration,
            String path,
            String legacyPath,
            double fallback
    ) {
        if (configuration.isSet(path)) {
            return configuration.getDouble(path, fallback);
        }

        if (configuration.isSet(legacyPath)) {
            return configuration.getDouble(legacyPath, fallback);
        }

        return fallback;
    }

    private static List<String> listOrFallback(
            FileConfiguration configuration,
            String path,
            String fallbackPath
    ) {
        List<String> primary = configuration.getStringList(path);
        if (!primary.isEmpty()) {
            return List.copyOf(primary);
        }

        if (fallbackPath != null) {
            List<String> fallbackValues = configuration.getStringList(fallbackPath);
            if (!fallbackValues.isEmpty()) {
                return List.copyOf(fallbackValues);
            }
        }

        return List.of();
    }

    private static Map<String, SnarkTriggersConfig.ExternalOutputToggle> loadExternalOutputs(FileConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("external-outputs");
        if (section == null) {
            return Map.of();
        }

        Map<String, SnarkTriggersConfig.ExternalOutputToggle> toggles = new LinkedHashMap<>();
        for (String outputId : section.getKeys(false)) {
            String basePath = "external-outputs." + outputId;
            toggles.put(outputId, new SnarkTriggersConfig.ExternalOutputToggle(
                    configuration.getBoolean(basePath + ".enabled", false),
                    configuration.getString(basePath + ".display-name", ""),
                    configuration.getString(basePath + ".source-plugin", ""),
                    configuration.getString(basePath + ".kind", ""),
                    configuration.getString(basePath + ".event-class", ""),
                    configuration.getString(basePath + ".description", "")
            ));
        }

        return toggles;
    }
}
