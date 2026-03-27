package com.beautyinblocks.snarkyserver;

import java.util.EnumMap;
import java.util.Map;

public record SnarkChancesConfig(
        Map<DeathCategory, Double> deathChances,
        Map<ChatCategory, Double> chatChances
) {
    public SnarkChancesConfig {
        deathChances = immutableEnumMap(DeathCategory.class, deathChances);
        chatChances = immutableEnumMap(ChatCategory.class, chatChances);
    }

    public double deathChanceFor(DeathCategory category) {
        return deathChances.getOrDefault(category, deathChances.getOrDefault(DeathCategory.GENERIC, 0.0D));
    }

    public double chatChanceFor(ChatCategory category) {
        return chatChances.getOrDefault(category, chatChances.getOrDefault(ChatCategory.GENERIC, 0.0D));
    }

    private static <E extends Enum<E>> Map<E, Double> immutableEnumMap(Class<E> enumType, Map<E, Double> source) {
        EnumMap<E, Double> copy = new EnumMap<>(enumType);
        copy.putAll(source);
        return Map.copyOf(copy);
    }
}
