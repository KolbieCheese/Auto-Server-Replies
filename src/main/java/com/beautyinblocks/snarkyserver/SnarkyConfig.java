package com.beautyinblocks.snarkyserver;

public record SnarkyConfig(
        String prefix,
        SnarkMessagesConfig messagesConfig,
        SnarkChancesConfig chancesConfig,
        SnarkTriggersConfig triggersConfig
) {
}
