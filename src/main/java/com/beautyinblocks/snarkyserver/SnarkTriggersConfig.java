package com.beautyinblocks.snarkyserver;

import java.util.List;

public record SnarkTriggersConfig(
        boolean enabled,
        DeathSnark deathSnark,
        ChatSnark chatSnark,
        Cooldowns cooldowns,
        Filters filters
) {
    public record DeathSnark(boolean enabled) {
    }

    public record ChatSnark(
            boolean enabled,
            int minMessageLength,
            boolean ignoreCommands,
            SpamBurst spamBurst
    ) {
        public record SpamBurst(int threshold, int windowSeconds, int maxMessageLength) {
        }
    }

    public record Cooldowns(int perPlayerSeconds, int globalSeconds) {
    }

    public record Filters(String bypassPermission, List<String> ignoredWorlds, List<String> ignoredPrefixes) {
        public Filters {
            ignoredWorlds = List.copyOf(ignoredWorlds);
            ignoredPrefixes = List.copyOf(ignoredPrefixes);
        }
    }
}
