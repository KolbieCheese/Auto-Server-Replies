package com.beautyinblocks.snarkyserver;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum DeathCategory {
    GENERIC("generic"),
    LAVA("lava"),
    FALL("fall"),
    PVP("pvp"),
    DROWNING("drowning"),
    FIRE("fire"),
    VOID("void"),
    EXPLOSION("explosion"),
    SUFFOCATION("suffocation"),
    STARVATION("starvation"),
    PROJECTILE("projectile"),
    FREEZING("freezing");

    private final String configKey;

    DeathCategory(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public String messagePath() {
        return "messages.death-" + configKey;
    }

    public String chancePath() {
        return "death-snark.chances." + configKey;
    }

    public static Optional<DeathCategory> fromKey(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.configKey.equals(normalized))
                .findFirst();
    }
}
