package com.beautyinblocks.snarkyserver;

import java.util.List;
import java.util.Map;

public record SnarkTriggersConfig(
        boolean enabled,
        DeathSnark deathSnark,
        ChatSnark chatSnark,
        Cooldowns cooldowns,
        Filters filters,
        Map<String, ExternalOutputToggle> externalOutputs
) {
    public SnarkTriggersConfig {
        externalOutputs = Map.copyOf(externalOutputs);
    }

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

    public record ExternalOutputToggle(
            boolean enabled,
            String displayName,
            String sourcePlugin,
            String kind,
            String eventClass,
            String description
    ) {
        public ExternalOutputToggle {
            displayName = normalize(displayName);
            sourcePlugin = normalize(sourcePlugin);
            kind = normalize(kind);
            eventClass = normalize(eventClass);
            description = normalize(description);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
