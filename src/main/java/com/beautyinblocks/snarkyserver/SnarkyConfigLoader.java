package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class SnarkyConfigLoader {
    private SnarkyConfigLoader() {
    }

    public static SnarkyConfig load(FileConfiguration configuration) {
        double legacyDeathChance = configuration.getDouble("death-snark.chance", 0.20);
        double legacyChatChance = configuration.getDouble("chat-snark.chance", 0.05);

        return new SnarkyConfig(
                configuration.getBoolean("enabled", true),
                configuration.getString("prefix", ""),
                new SnarkyConfig.DeathSnark(
                        configuration.getBoolean("death-snark.enabled", true),
                        new SnarkyConfig.DeathSnark.Chances(
                                getChance(configuration, "death-snark.chances.generic", legacyDeathChance),
                                getChance(configuration, "death-snark.chances.lava", legacyDeathChance),
                                getChance(configuration, "death-snark.chances.fall", legacyDeathChance),
                                getChance(configuration, "death-snark.chances.pvp", legacyDeathChance)
                        )
                ),
                new SnarkyConfig.ChatSnark(
                        configuration.getBoolean("chat-snark.enabled", true),
                        new SnarkyConfig.ChatSnark.Chances(
                                getChance(configuration, "chat-snark.chances.generic", legacyChatChance)
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
                        listOrFallback(configuration.getStringList("messages.death-generic"), "Another glorious death by {player}."),
                        listOrFallback(configuration.getStringList("messages.death-lava"), "{player} touched lava and regretted it."),
                        listOrFallback(configuration.getStringList("messages.death-fall"), "{player} discovered terminal velocity."),
                        listOrFallback(configuration.getStringList("messages.death-pvp"), "{player} lost a duel to {killer}."),
                        listOrFallback(configuration.getStringList("messages.chat-generic"), "{player}: {message}")
                )
        );
    }

    private static double getChance(FileConfiguration configuration, String path, double fallback) {
        return configuration.isSet(path) ? configuration.getDouble(path, fallback) : fallback;
    }

    private static List<String> listOrFallback(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? List.of(fallback) : List.copyOf(values);
    }
}
