package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SnarkyConfigLoader {
    private static final double DEFAULT_DEATH_CHANCE = 0.20D;
    private static final double DEFAULT_CHAT_CHANCE = 0.05D;

    private SnarkyConfigLoader() {
    }

    public static SnarkyConfig load(FileConfiguration configuration) {
        double deathGenericChance = getChance(
                configuration,
                DeathCategory.GENERIC.chancePath(),
                "death-snark.chance",
                DEFAULT_DEATH_CHANCE
        );
        double chatGenericChance = getChance(
                configuration,
                ChatCategory.GENERIC.chancePath(),
                "chat-snark.chance",
                DEFAULT_CHAT_CHANCE
        );

        return new SnarkyConfig(
                configuration.getString("prefix", ""),
                new SnarkMessagesConfig(
                        loadDeathMessages(configuration),
                        loadChatMessages(configuration)
                ),
                new SnarkChancesConfig(
                        loadDeathChances(configuration, deathGenericChance),
                        loadChatChances(configuration, chatGenericChance)
                ),
                new SnarkTriggersConfig(
                        configuration.getBoolean("enabled", true),
                        new SnarkTriggersConfig.DeathSnark(
                                configuration.getBoolean("death-snark.enabled", true)
                        ),
                        new SnarkTriggersConfig.ChatSnark(
                                configuration.getBoolean("chat-snark.enabled", true),
                                configuration.getInt("chat-snark.min-message-length", 6),
                                configuration.getBoolean("chat-snark.ignore-commands", true),
                                new SnarkTriggersConfig.ChatSnark.SpamBurst(
                                        configuration.getInt("chat-snark.spam-burst.threshold", 3),
                                        configuration.getInt("chat-snark.spam-burst.window-seconds", 8),
                                        configuration.getInt("chat-snark.spam-burst.max-message-length", 12)
                                )
                        ),
                        new SnarkTriggersConfig.Cooldowns(
                                configuration.getInt("cooldowns.per-player-seconds", 120),
                                configuration.getInt("cooldowns.global-seconds", 20)
                        ),
                        new SnarkTriggersConfig.Filters(
                                configuration.getString("filters.bypass-permission", "snarkyserver.bypass"),
                                configuration.getStringList("filters.ignored-worlds"),
                                configuration.getStringList("filters.ignored-prefixes")
                        )
                )
        );
    }

    private static Map<DeathCategory, Double> loadDeathChances(FileConfiguration configuration, double defaultChance) {
        EnumMap<DeathCategory, Double> chances = new EnumMap<>(DeathCategory.class);
        for (DeathCategory category : DeathCategory.values()) {
            chances.put(category, getChance(configuration, category.chancePath(), defaultChance));
        }
        return chances;
    }

    private static Map<ChatCategory, Double> loadChatChances(FileConfiguration configuration, double defaultChance) {
        EnumMap<ChatCategory, Double> chances = new EnumMap<>(ChatCategory.class);
        for (ChatCategory category : ChatCategory.values()) {
            chances.put(category, getChance(configuration, category.chancePath(), defaultChance));
        }
        return chances;
    }

    private static Map<DeathCategory, List<String>> loadDeathMessages(FileConfiguration configuration) {
        EnumMap<DeathCategory, List<String>> messages = new EnumMap<>(DeathCategory.class);
        List<String> generic = listOrFallback(
                configuration,
                DeathCategory.GENERIC.messagePath(),
                null
        );
        messages.put(DeathCategory.GENERIC, generic);

        for (DeathCategory category : DeathCategory.values()) {
            if (category == DeathCategory.GENERIC) {
                continue;
            }
            messages.put(category, listOrFallback(configuration, category.messagePath(), DeathCategory.GENERIC.messagePath()));
        }

        return messages;
    }

    private static Map<ChatCategory, List<String>> loadChatMessages(FileConfiguration configuration) {
        EnumMap<ChatCategory, List<String>> messages = new EnumMap<>(ChatCategory.class);
        List<String> generic = listOrFallback(
                configuration,
                ChatCategory.GENERIC.messagePath(),
                null
        );
        messages.put(ChatCategory.GENERIC, generic);

        for (ChatCategory category : ChatCategory.values()) {
            if (category == ChatCategory.GENERIC) {
                continue;
            }
            messages.put(category, listOrFallback(configuration, category.messagePath(), ChatCategory.GENERIC.messagePath()));
        }

        return messages;
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
}
