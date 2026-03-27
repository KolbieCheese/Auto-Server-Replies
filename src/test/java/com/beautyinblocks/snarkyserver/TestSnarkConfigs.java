package com.beautyinblocks.snarkyserver;

import java.util.EnumMap;
import java.util.List;

final class TestSnarkConfigs {
    private TestSnarkConfigs() {
    }

    static SnarkyConfig simpleConfig(
            boolean enabled,
            boolean deathEnabled,
            boolean chatEnabled,
            int perPlayerCooldownSeconds,
            int globalCooldownSeconds,
            double deathChance,
            double chatChance
    ) {
        EnumMap<DeathCategory, Double> deathChances = new EnumMap<>(DeathCategory.class);
        EnumMap<ChatCategory, Double> chatChances = new EnumMap<>(ChatCategory.class);
        EnumMap<DeathCategory, List<String>> deathMessages = new EnumMap<>(DeathCategory.class);
        EnumMap<ChatCategory, List<String>> chatMessages = new EnumMap<>(ChatCategory.class);

        for (DeathCategory category : DeathCategory.values()) {
            deathChances.put(category, deathChance);
            deathMessages.put(category, List.of("Death " + category.configKey() + " {player} {killer}"));
        }
        for (ChatCategory category : ChatCategory.values()) {
            chatChances.put(category, chatChance);
            chatMessages.put(category, List.of("Chat " + category.configKey() + " {player} {message}"));
        }

        SnarkMessagesConfig messagesConfig = new SnarkMessagesConfig(deathMessages, chatMessages);
        SnarkChancesConfig chancesConfig = new SnarkChancesConfig(deathChances, chatChances);
        SnarkTriggersConfig triggersConfig = new SnarkTriggersConfig(
                enabled,
                new SnarkTriggersConfig.DeathSnark(deathEnabled),
                new SnarkTriggersConfig.ChatSnark(
                        chatEnabled,
                        6,
                        true,
                        new SnarkTriggersConfig.ChatSnark.SpamBurst(3, 8, 12)
                ),
                new SnarkTriggersConfig.Cooldowns(perPlayerCooldownSeconds, globalCooldownSeconds),
                new SnarkTriggersConfig.Filters("snarkyserver.bypass", List.of(), List.of())
        );

        return new SnarkyConfig(
                "<white>[Server] <reset>",
                messagesConfig,
                chancesConfig,
                triggersConfig
        );
    }
}
