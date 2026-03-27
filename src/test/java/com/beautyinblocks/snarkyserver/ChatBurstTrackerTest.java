package com.beautyinblocks.snarkyserver;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBurstTrackerTest {
    @Test
    void triggersOnlyAfterConfiguredBurst() {
        ChatBurstTracker tracker = new ChatBurstTracker();
        UUID playerId = UUID.randomUUID();
        SnarkTriggersConfig.ChatSnark.SpamBurst spamBurst = new SnarkTriggersConfig.ChatSnark.SpamBurst(3, 8, 12);
        Instant start = Instant.parse("2026-03-25T23:00:00Z");

        assertFalse(tracker.recordAndCheck(playerId, "ok", start, spamBurst));
        assertFalse(tracker.recordAndCheck(playerId, "ok", start.plusSeconds(1), spamBurst));
        assertTrue(tracker.recordAndCheck(playerId, "ok", start.plusSeconds(2), spamBurst));
        assertFalse(tracker.recordAndCheck(playerId, "this message is too long", start.plusSeconds(3), spamBurst));
        assertFalse(tracker.recordAndCheck(playerId, "ok", start.plusSeconds(20), spamBurst));
    }
}
