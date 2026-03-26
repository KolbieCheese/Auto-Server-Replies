package com.beautyinblocks.snarkyserver;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ChatCategory {
    GENERIC("generic", "This is some regular nonsense."),
    QUESTION("question", "how do I fix this?"),
    EXCITED("excited", "THIS IS AMAZING!"),
    GREETING("greeting", "hello"),
    LAG("lag", "is the server lagging?"),
    BRAG("brag", "too easy for me"),
    CONFUSION("confusion", "how do I do this"),
    SPAM("spam", "ok"),
    CELEBRATION("celebration", "let's go");

    private final String configKey;
    private final String defaultTestMessage;

    ChatCategory(String configKey, String defaultTestMessage) {
        this.configKey = configKey;
        this.defaultTestMessage = defaultTestMessage;
    }

    public String configKey() {
        return configKey;
    }

    public String defaultTestMessage() {
        return defaultTestMessage;
    }

    public String messagePath() {
        return "messages.chat-" + configKey;
    }

    public String chancePath() {
        return "chat-snark.chances." + configKey;
    }

    public static Optional<ChatCategory> fromKey(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.configKey.equals(normalized))
                .findFirst();
    }
}
