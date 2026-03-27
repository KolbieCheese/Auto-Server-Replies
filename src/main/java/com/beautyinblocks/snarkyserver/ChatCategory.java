package com.beautyinblocks.snarkyserver;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ChatCategory {
    GENERIC("generic"),
    QUESTION("question"),
    EXCITED("excited"),
    GREETING("greeting"),
    LAG("lag"),
    BRAG("brag"),
    CONFUSION("confusion"),
    SPAM("spam"),
    CELEBRATION("celebration");

    private final String configKey;

    ChatCategory(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public String messageKey() {
        return "chat-" + configKey;
    }

    public String chanceKey() {
        return configKey;
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
