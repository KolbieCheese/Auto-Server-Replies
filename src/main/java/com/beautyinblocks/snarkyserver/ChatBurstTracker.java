package com.beautyinblocks.snarkyserver;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatBurstTracker {
    private final Map<UUID, Deque<Instant>> shortMessageBursts = new ConcurrentHashMap<>();

    public boolean recordAndCheck(UUID playerId, String message, Instant now, SnarkyConfig.ChatSnark.SpamBurst spamBurst) {
        int maxMessageLength = Math.max(1, spamBurst.maxMessageLength());
        int threshold = Math.max(2, spamBurst.threshold());
        int windowSeconds = Math.max(1, spamBurst.windowSeconds());
        String normalized = message == null ? "" : message.trim();

        if (normalized.isEmpty() || normalized.length() > maxMessageLength) {
            shortMessageBursts.remove(playerId);
            return false;
        }

        Deque<Instant> instants = shortMessageBursts.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        Instant windowStart = now.minusSeconds(windowSeconds);

        synchronized (instants) {
            while (!instants.isEmpty() && instants.peekFirst().isBefore(windowStart)) {
                instants.removeFirst();
            }

            instants.addLast(now);
            boolean triggered = instants.size() >= threshold;
            if (!triggered && instants.isEmpty()) {
                shortMessageBursts.remove(playerId);
            }
            return triggered;
        }
    }

    public void clear() {
        shortMessageBursts.clear();
    }
}
