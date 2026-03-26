package com.beautyinblocks.snarkyserver;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record SnarkyConfig(
        boolean enabled,
        String prefix,
        DeathSnark deathSnark,
        ChatSnark chatSnark,
        Cooldowns cooldowns,
        Filters filters,
        Messages messages
) {
    public record DeathSnark(boolean enabled, Map<DeathCategory, Double> chances) {
        public DeathSnark {
            chances = immutableEnumMap(DeathCategory.class, chances);
        }

        public double chanceFor(DeathCategory category) {
            return chances.getOrDefault(category, chances.getOrDefault(DeathCategory.GENERIC, 0.0D));
        }
    }

    public record ChatSnark(
            boolean enabled,
            Map<ChatCategory, Double> chances,
            int minMessageLength,
            boolean ignoreCommands,
            SpamBurst spamBurst
    ) {
        public ChatSnark {
            chances = immutableEnumMap(ChatCategory.class, chances);
        }

        public double chanceFor(ChatCategory category) {
            return chances.getOrDefault(category, chances.getOrDefault(ChatCategory.GENERIC, 0.0D));
        }

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

    public record Messages(
            Map<DeathCategory, List<String>> deathMessages,
            Map<ChatCategory, List<String>> chatMessages
    ) {
        public Messages {
            deathMessages = copyMessageMap(DeathCategory.class, deathMessages);
            chatMessages = copyMessageMap(ChatCategory.class, chatMessages);
        }

        public List<String> deathMessagesFor(DeathCategory category) {
            return deathMessages.getOrDefault(category, deathMessages.getOrDefault(DeathCategory.GENERIC, List.of()));
        }

        public List<String> chatMessagesFor(ChatCategory category) {
            return chatMessages.getOrDefault(category, chatMessages.getOrDefault(ChatCategory.GENERIC, List.of()));
        }
    }

    private static <E extends Enum<E>> Map<E, Double> immutableEnumMap(Class<E> enumType, Map<E, Double> source) {
        EnumMap<E, Double> copy = new EnumMap<>(enumType);
        copy.putAll(source);
        return Map.copyOf(copy);
    }

    private static <E extends Enum<E>> Map<E, List<String>> copyMessageMap(Class<E> enumType, Map<E, List<String>> source) {
        EnumMap<E, List<String>> copy = new EnumMap<>(enumType);
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }
}
