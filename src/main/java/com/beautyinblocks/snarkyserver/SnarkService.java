package com.beautyinblocks.snarkyserver;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class SnarkService {
    private final RandomGenerator random;
    private final CooldownManager cooldownManager;
    private final SnarkFormatter formatter;
    private final SnarkyConfig config;

    public SnarkService(
            RandomGenerator random,
            CooldownManager cooldownManager,
            SnarkFormatter formatter,
            SnarkyConfig config
    ) {
        this.random = random;
        this.cooldownManager = cooldownManager;
        this.formatter = formatter;
        this.config = config;
    }

    public String buildDeathReply(UUID playerId, String playerName, String worldName, String causeKey, String killerName) {
        if (!config.enabled() || !config.deathSnark().enabled()) {
            return null;
        }
        if (isIgnoredWorld(worldName) || cooldownManager.isOnCooldown(playerId) || random.nextDouble() > config.deathSnark().chance()) {
            return null;
        }

        List<String> messages = switch (causeKey) {
            case "lava" -> config.messages().deathLava();
            case "fall" -> config.messages().deathFall();
            case "pvp" -> config.messages().deathPvp();
            default -> config.messages().deathGeneric();
        };

        String message = pickRandom(messages);
        cooldownManager.markTriggered(playerId);
        return formatter.format(message, Map.of(
                "player", playerName,
                "killer", killerName == null ? "someone" : killerName
        ));
    }

    private String pickRandom(List<String> messages) {
        return messages.get(random.nextInt(messages.size()));
    }

    private boolean isIgnoredWorld(String worldName) {
        return config.filters().ignoredWorlds().stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
    }
}
