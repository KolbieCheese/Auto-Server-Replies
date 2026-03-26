package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnarkServiceTest {
    @Test
    void forcedRepliesBypassDisabledConfig() {
        Server server = mockServer();
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                TestSnarkConfigs.simpleConfig(false, false, false, 60, 20, 1.0D, 1.0D),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );
        Player player = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), GameMode.SURVIVAL);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        assertNull(service.buildAutomaticChatReply(player, "hello there"));
        Component preview = service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "");
        assertNotNull(preview);
    }

    @Test
    void automaticRepliesRespectCooldowns() {
        Server server = mockServer();
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(120, 0)),
                new SnarkFormatter("<white>[Server] <reset>"),
                TestSnarkConfigs.simpleConfig(true, true, true, 120, 0, 1.0D, 1.0D),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );
        Player player = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), GameMode.SURVIVAL);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        Component first = service.buildAutomaticDeathReply(player);
        Component second = service.buildAutomaticDeathReply(player);

        assertNotNull(first);
        assertNull(second);
        assertNotNull(PlainTextComponentSerializer.plainText().serialize(first));
    }

    @Test
    void testRepliesLeaveOptionalPlaceholdersBlank() {
        Server server = mockServer();
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                configWithMessages(
                        List.of("Death {player}|{killer}|{message}"),
                        List.of("Chat {player}|{message}|{killer}")
                ),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        String rendered = PlainTextComponentSerializer.plainText().serialize(
                service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "")
        );

        assertEquals("[Server] Chat Kolbie||", rendered);
    }

    @Test
    void testRepliesRenderTemplatesWithoutPlaceholders() {
        Server server = mockServer();
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                configWithMessages(List.of("Just a death line."), List.of("Just a chat line.")),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        String rendered = PlainTextComponentSerializer.plainText().serialize(
                service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "hello there")
        );

        assertEquals("[Server] Just a chat line.", rendered);
    }

    @Test
    void testRepliesFallBackToBundledDefaultMessagesWhenLiveConfigIsEmpty() {
        Server server = mockServer();
        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                configWithMessages(List.of(), List.of()),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server),
                configWithMessages(
                        List.of("Default death {player}"),
                        List.of("Default chat {player}|{message}")
                ).messages()
        );

        String rendered = PlainTextComponentSerializer.plainText().serialize(
                service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "hello there")
        );

        assertEquals("[Server] Default chat Kolbie|hello there", rendered);
    }

    @Test
    void hiddenChatPlayersDoNotTriggerAutomaticReplies() {
        Server server = mockServer();
        Player player = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174001"), GameMode.SPECTATOR);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                TestSnarkConfigs.simpleConfig(true, true, true, 60, 20, 1.0D, 1.0D),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        assertNull(service.buildAutomaticChatReply(player, "hello there"));
    }

    @Test
    void hiddenDeathPlayersDoNotTriggerAutomaticReplies() {
        Server server = mockServer();
        Player player = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174002"), GameMode.SPECTATOR);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                TestSnarkConfigs.simpleConfig(true, true, true, 60, 20, 1.0D, 1.0D),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        assertNull(service.buildAutomaticDeathReply(player));
    }

    @Test
    void hiddenKillersDoNotExposePvpOrKillerName() {
        Server server = mockServer();
        Player victim = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174003"), GameMode.SURVIVAL);
        Player killer = mockPlayer("Sneaky", UUID.fromString("123e4567-e89b-12d3-a456-426614174004"), GameMode.SURVIVAL);
        MetadataValue vanished = mock(MetadataValue.class);
        EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
        when(vanished.asBoolean()).thenReturn(true);
        when(killer.hasMetadata("vanished")).thenReturn(true);
        when(killer.getMetadata("vanished")).thenReturn(List.of(vanished));
        when(victim.getKiller()).thenReturn(killer);
        when(victim.getLastDamageCause()).thenReturn(damageEvent);
        when(damageEvent.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        doReturn(List.of(victim, killer)).when(server).getOnlinePlayers();

        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkyConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                deathCategoryConfig("Generic {killer}", "Pvp {killer}"),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        String rendered = PlainTextComponentSerializer.plainText().serialize(service.buildAutomaticDeathReply(victim));

        assertEquals("[Server] Generic ", rendered);
    }

    private Server mockServer() {
        return mock(Server.class);
    }

    private Player mockPlayer(String name, UUID uuid, GameMode gameMode) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(player.getGameMode()).thenReturn(gameMode);
        when(player.hasPermission("snarkyserver.bypass")).thenReturn(false);
        when(player.hasMetadata("vanished")).thenReturn(false);
        when(player.getMetadata("vanished")).thenReturn(List.of());
        when(player.canSee(any(Player.class))).thenReturn(true);
        return player;
    }

    private SnarkyConfig configWithMessages(List<String> deathGeneric, List<String> chatGeneric) {
        EnumMap<DeathCategory, Double> deathChances = new EnumMap<>(DeathCategory.class);
        EnumMap<ChatCategory, Double> chatChances = new EnumMap<>(ChatCategory.class);
        EnumMap<DeathCategory, List<String>> deathMessages = new EnumMap<>(DeathCategory.class);
        EnumMap<ChatCategory, List<String>> chatMessages = new EnumMap<>(ChatCategory.class);

        for (DeathCategory category : DeathCategory.values()) {
            deathChances.put(category, 1.0D);
            deathMessages.put(category, deathGeneric);
        }
        for (ChatCategory category : ChatCategory.values()) {
            chatChances.put(category, 1.0D);
            chatMessages.put(category, chatGeneric);
        }

        return new SnarkyConfig(
                true,
                "<white>[Server] <reset>",
                new SnarkyConfig.DeathSnark(true, deathChances),
                new SnarkyConfig.ChatSnark(
                        true,
                        chatChances,
                        1,
                        false,
                        new SnarkyConfig.ChatSnark.SpamBurst(3, 8, 12)
                ),
                new SnarkyConfig.Cooldowns(60, 20),
                new SnarkyConfig.Filters("snarkyserver.bypass", List.of(), List.of()),
                new SnarkyConfig.Messages(deathMessages, chatMessages)
        );
    }

    private SnarkyConfig deathCategoryConfig(String genericDeath, String pvpDeath) {
        SnarkyConfig config = configWithMessages(List.of(genericDeath), List.of("Chat"));
        EnumMap<DeathCategory, List<String>> deathMessages = new EnumMap<>(config.messages().deathMessages());
        deathMessages.put(DeathCategory.GENERIC, List.of(genericDeath));
        deathMessages.put(DeathCategory.PVP, List.of(pvpDeath));
        return new SnarkyConfig(
                config.enabled(),
                config.prefix(),
                config.deathSnark(),
                config.chatSnark(),
                config.cooldowns(),
                config.filters(),
                new SnarkyConfig.Messages(deathMessages, config.messages().chatMessages())
        );
    }
}
