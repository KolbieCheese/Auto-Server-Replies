package com.beautyinblocks.snarkyserver;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class CooldownManager {
    private final Duration perPlayerDuration;
    private final Duration globalDuration;
    private final Map<UUID, Instant> lastResponseByPlayer = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> globalLastResponse = new AtomicReference<>(Instant.EPOCH);

    public CooldownManager(SnarkyConfig.Cooldowns cooldowns) {
        this.perPlayerDuration = Duration.ofSeconds(Math.max(0, cooldowns.perPlayerSeconds()));
        this.globalDuration = Duration.ofSeconds(Math.max(0, cooldowns.globalSeconds()));
    }

    public synchronized boolean canRespond(UUID playerId, Instant now) {
        Instant lastPlayerResponse = lastResponseByPlayer.getOrDefault(playerId, Instant.EPOCH);
        Instant lastGlobalResponse = globalLastResponse.get();

        return !now.isBefore(lastPlayerResponse.plus(perPlayerDuration))
                && !now.isBefore(lastGlobalResponse.plus(globalDuration));
    }

    public synchronized void markResponded(UUID playerId, Instant now) {
        lastResponseByPlayer.put(playerId, now);
        globalLastResponse.set(now);
    }

    public void clear() {
        lastResponseByPlayer.clear();
        globalLastResponse.set(Instant.EPOCH);
    }
}
