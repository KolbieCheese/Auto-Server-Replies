package com.beautyinblocks.snarkyserver;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record SnarkMessagesConfig(
        Map<DeathCategory, List<String>> deathMessages,
        Map<ChatCategory, List<String>> chatMessages
) {
    public SnarkMessagesConfig {
        deathMessages = copyMessageMap(DeathCategory.class, deathMessages);
        chatMessages = copyMessageMap(ChatCategory.class, chatMessages);
    }

    public List<String> deathMessagesFor(DeathCategory category) {
        return deathMessages.getOrDefault(category, deathMessages.getOrDefault(DeathCategory.GENERIC, List.of()));
    }

    public List<String> chatMessagesFor(ChatCategory category) {
        return chatMessages.getOrDefault(category, chatMessages.getOrDefault(ChatCategory.GENERIC, List.of()));
    }

    private static <E extends Enum<E>> Map<E, List<String>> copyMessageMap(Class<E> enumType, Map<E, List<String>> source) {
        EnumMap<E, List<String>> copy = new EnumMap<>(enumType);
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }
}
