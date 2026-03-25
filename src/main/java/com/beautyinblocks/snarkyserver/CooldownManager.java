package com.beautyinblocks.snarkyserver;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {
    private final Duration perPlayerDuration;
    private final Duration globalDuration;
    private final Map<UUID, Instant> perPlayerCooldowns = new ConcurrentHashMap<>();
    private volatile Instant globalCooldownUntil = Instant.EPOCH;

    public CooldownManager(SnarkyConfig.Cooldowns cooldowns) {
        this.perPlayerDuration = Duration.ofSeconds(Math.max(0, cooldowns.perPlayerSeconds()));
        this.globalDuration = Duration.ofSeconds(Math.max(0, cooldowns.globalSeconds()));
    }

    public boolean isOnCooldown(UUID playerId) {
        Instant now = Instant.now();
        Instant playerCooldown = perPlayerCooldowns.getOrDefault(playerId, Instant.EPOCH);
        return now.isBefore(playerCooldown) || now.isBefore(globalCooldownUntil);
    }

    public void markTriggered(UUID playerId) {
        Instant now = Instant.now();
        perPlayerCooldowns.put(playerId, now.plus(perPlayerDuration));
        globalCooldownUntil = now.plus(globalDuration);
    }

    public void clear() {
        perPlayerCooldowns.clear();
        globalCooldownUntil = Instant.EPOCH;
    }
}
