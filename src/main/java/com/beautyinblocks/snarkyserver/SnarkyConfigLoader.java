package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class SnarkyConfigLoader {
    private static final double DEFAULT_DEATH_CHANCE = 0.20D;
    private static final double DEFAULT_CHAT_CHANCE = 0.05D;

    private SnarkyConfigLoader() {
    }

    public static SnarkyConfig load(FileConfiguration configuration) {
        double deathGenericChance = getChance(
                configuration,
                "death-snark.chances.generic",
                "death-snark.chance",
                DEFAULT_DEATH_CHANCE
        );
        double chatGenericChance = getChance(
                configuration,
                "chat-snark.chances.generic",
                "chat-snark.chance",
                DEFAULT_CHAT_CHANCE
        );

        return new SnarkyConfig(
                configuration.getBoolean("enabled", true),
                configuration.getString("prefix", ""),
                new SnarkyConfig.DeathSnark(
                        configuration.getBoolean("death-snark.enabled", true),
                        new SnarkyConfig.DeathSnark.Chances(
                                deathGenericChance,
                                getChance(configuration, "death-snark.chances.lava", deathGenericChance),
                                getChance(configuration, "death-snark.chances.fall", deathGenericChance),
                                getChance(configuration, "death-snark.chances.pvp", deathGenericChance),
                                getChance(configuration, "death-snark.chances.drowning", deathGenericChance),
                                getChance(configuration, "death-snark.chances.fire", deathGenericChance),
                                getChance(configuration, "death-snark.chances.void", deathGenericChance)
                        )
                ),
                new SnarkyConfig.ChatSnark(
                        configuration.getBoolean("chat-snark.enabled", true),
                        new SnarkyConfig.ChatSnark.Chances(
                                chatGenericChance,
                                getChance(configuration, "chat-snark.chances.question", chatGenericChance),
                                getChance(configuration, "chat-snark.chances.excited", chatGenericChance),
                                getChance(configuration, "chat-snark.chances.greeting", chatGenericChance)
                        ),
                        configuration.getInt("chat-snark.min-message-length", 6),
                        configuration.getBoolean("chat-snark.ignore-commands", true)
                ),
                new SnarkyConfig.Cooldowns(
                        configuration.getInt("cooldowns.per-player-seconds", 120),
                        configuration.getInt("cooldowns.global-seconds", 20)
                ),
                new SnarkyConfig.Filters(
                        configuration.getString("filters.bypass-permission", "snarkyserver.bypass"),
                        configuration.getStringList("filters.ignored-worlds"),
                        configuration.getStringList("filters.ignored-prefixes")
                ),
                new SnarkyConfig.Messages(
                        listOrFallback(configuration, "messages.death-generic", null, "Another glorious death by {player}."),
                        listOrFallback(configuration, "messages.death-lava", "messages.death-generic", "{player} touched lava and regretted it."),
                        listOrFallback(configuration, "messages.death-fall", "messages.death-generic", "{player} discovered terminal velocity."),
                        listOrFallback(configuration, "messages.death-pvp", "messages.death-generic", "{player} lost a duel to {killer}."),
                        listOrFallback(configuration, "messages.death-drowning", null, "{player} forgot how breathing works underwater."),
                        listOrFallback(configuration, "messages.death-fire", null, "{player} is having a rough time with open flames."),
                        listOrFallback(configuration, "messages.death-void", null, "{player} explored the void and found consequences."),
                        listOrFallback(configuration, "messages.chat-generic", null, "{player}: {message}"),
                        listOrFallback(configuration, "messages.chat-question", null, "{player} asked: {message}"),
                        listOrFallback(configuration, "messages.chat-excited", null, "{player} is feeling intense: {message}"),
                        listOrFallback(configuration, "messages.chat-greeting", null, "{player} has entered the conversation: {message}")
                )
        );
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
            String fallbackPath,
            String hardcodedFallback
    ) {
        List<String> primary = configuration.getStringList(path);
        if (primary != null && !primary.isEmpty()) {
            return List.copyOf(primary);
        }

        if (fallbackPath != null) {
            List<String> fallbackValues = configuration.getStringList(fallbackPath);
            if (fallbackValues != null && !fallbackValues.isEmpty()) {
                return List.copyOf(fallbackValues);
            }
        }

        return List.of(hardcodedFallback);
    }
}
