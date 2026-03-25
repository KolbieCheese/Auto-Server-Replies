package com.beautyinblocks.snarkyserver;

import java.util.List;

public record SnarkyConfig(
        boolean enabled,
        String prefix,
        DeathSnark deathSnark,
        ChatSnark chatSnark,
        Cooldowns cooldowns,
        Filters filters,
        Messages messages
) {
    public record DeathSnark(boolean enabled, double chance) {
    }

    public record ChatSnark(boolean enabled, double chance, int minMessageLength, boolean ignoreCommands) {
    }

    public record Cooldowns(int perPlayerSeconds, int globalSeconds) {
    }

    public record Filters(String bypassPermission, List<String> ignoredWorlds, List<String> ignoredPrefixes) {
    }

    public record Messages(
            List<String> deathGeneric,
            List<String> deathLava,
            List<String> deathFall,
            List<String> deathPvp,
            List<String> chatGeneric
    ) {
    }
}
