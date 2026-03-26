package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnarkServiceTest {
    @Test
    void forcedRepliesBypassDisabledConfig() {
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                TestSnarkConfigs.simpleConfig(false, false, false, 60, 20, 1.0D, 1.0D),
                new ChatCategoryClassifier(),
                new ChatBurstTracker()
        );
        Player player = mockPlayer("Kolbie");

        assertNull(service.buildAutomaticChatReply(player, "hello there"));
        Component preview = service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "");
        assertNotNull(preview);
    }

    @Test
    void automaticRepliesRespectCooldowns() {
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(120, 0)),
                new SnarkFormatter("<white>[Server] <reset>"),
                TestSnarkConfigs.simpleConfig(true, true, true, 120, 0, 1.0D, 1.0D),
                new ChatCategoryClassifier(),
                new ChatBurstTracker()
        );
        Player player = mockPlayer("Kolbie");

        Component first = service.buildAutomaticDeathReply(player, DeathCategory.GENERIC, null);
        Component second = service.buildAutomaticDeathReply(player, DeathCategory.GENERIC, null);

        assertNotNull(first);
        assertNull(second);
        assertNotNull(PlainTextComponentSerializer.plainText().serialize(first));
    }

    private Player mockPlayer(String name) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(player.hasPermission("snarkyserver.bypass")).thenReturn(false);
        return player;
    }
}
