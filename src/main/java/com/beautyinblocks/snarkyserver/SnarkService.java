package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class SnarkService {
    private static final String GENERIC = "generic";
    private static final String LAVA = "lava";
    private static final String FALL = "fall";
    private static final String PVP = "pvp";

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

    public Component buildDeathReply(Player player, String causeKey, String killerName) {
        if (!canRespondForPlayer(player) || !config.deathSnark().enabled()) {
            return null;
        }

        double chance = deathChanceForCause(causeKey);
        if (!passesChance(chance)) {
            return null;
        }

        List<String> messages = switch (causeKey) {
            case LAVA -> config.messages().deathLava();
            case FALL -> config.messages().deathFall();
            case PVP -> config.messages().deathPvp();
            default -> config.messages().deathGeneric();
        };

        String message = pickRandom(messages);
        cooldownManager.markResponded(player.getUniqueId(), Instant.now());
        return formatter.format(message, Map.of(
                "player", player.getName(),
                "killer", killerName == null ? "someone" : killerName,
                "message", ""
        ));
    }

    public Component buildChatReply(Player player, String messageText) {
        if (!canRespondForPlayer(player)
                || !config.chatSnark().enabled()
                || shouldSkipChatMessageLightweight(messageText)
                || !passesChance(config.chatSnark().chances().generic())) {
            return null;
        }

        String template = pickRandom(config.messages().chatGeneric());
        cooldownManager.markResponded(player.getUniqueId(), Instant.now());
        return formatter.format(template, Map.of(
                "player", player.getName(),
                "killer", "",
                "message", messageText
        ));
    }

    public boolean isChatEnabled() {
        return config.enabled() && config.chatSnark().enabled();
    }

    public boolean shouldSkipChatMessageLightweight(String messageText) {
        String trimmed = messageText == null ? "" : messageText.trim();
        if (trimmed.length() < config.chatSnark().minMessageLength()) {
            return true;
        }
        if (config.chatSnark().ignoreCommands() && trimmed.startsWith("/")) {
            return true;
        }

        return config.filters().ignoredPrefixes().stream().anyMatch(trimmed::startsWith);
    }

    public String classifyDeath(Player player) {
        if (player.getKiller() != null) {
            return PVP;
        }

        if (player.getLastDamageCause() == null) {
            return GENERIC;
        }

        return switch (player.getLastDamageCause().getCause()) {
            case LAVA, HOT_FLOOR, FIRE, FIRE_TICK -> LAVA;
            case FALL, FLY_INTO_WALL -> FALL;
            default -> GENERIC;
        };
    }

    private boolean canRespondForPlayer(Player player) {
        if (!config.enabled()) {
            return false;
        }

        if (!config.filters().bypassPermission().isBlank() && player.hasPermission(config.filters().bypassPermission())) {
            return false;
        }

        if (isIgnoredWorld(player.getWorld().getName())) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        return cooldownManager.canRespond(playerId, Instant.now());
    }

    private boolean passesChance(double chance) {
        return random.nextDouble() <= Math.max(0.0D, Math.min(1.0D, chance));
    }

    private double deathChanceForCause(String causeKey) {
        SnarkyConfig.DeathSnark.Chances chances = config.deathSnark().chances();
        return switch (causeKey) {
            case LAVA -> chances.lava();
            case FALL -> chances.fall();
            case PVP -> chances.pvp();
            default -> chances.generic();
        };
    }

    private String pickRandom(List<String> messages) {
        return messages.get(random.nextInt(messages.size()));
    }

    private boolean isIgnoredWorld(String worldName) {
        return config.filters().ignoredWorlds().stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
    }
}
