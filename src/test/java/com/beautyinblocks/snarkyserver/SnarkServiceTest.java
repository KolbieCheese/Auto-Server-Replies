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
import java.util.Map;
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(120, 0)),
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                configWithMessages(List.of(), List.of()),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server),
                configWithMessages(
                        List.of("Default death {player}"),
                        List.of("Default chat {player}|{message}")
                ).messagesConfig()
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
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
    void chatGreetingsDoNotTriggerAutomaticReplies() {
        Server server = mockServer();
        Player player = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174005"), GameMode.SURVIVAL);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                configWithMessages(List.of("Death"), List.of("Chat greeting {player}")),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        assertNull(service.buildAutomaticChatReply(player, "hello"));
    }

    @Test
    void joinsTriggerGreetingReplies() {
        Server server = mockServer();
        Player player = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174006"), GameMode.SURVIVAL);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        SnarkService service = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                configWithMessages(List.of("Death"), List.of("Chat greeting {player}")),
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        Component component = service.buildAutomaticJoinReply(player);

        assertNotNull(component);
        assertEquals("[Server] Chat greeting Kolbie", PlainTextComponentSerializer.plainText().serialize(component));
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
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
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

    @Test
    void externalChatContextUsesTheSameAutomaticPipeline() {
        Server server = mockServer();
        Player playerA = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174101"), GameMode.SURVIVAL);
        Player playerB = mockPlayer("Kolbie", UUID.fromString("123e4567-e89b-12d3-a456-426614174102"), GameMode.SURVIVAL);
        doReturn(List.of(playerA, playerB)).when(server).getOnlinePlayers();

        SnarkyConfig config = configWithMessages(List.of("Death"), List.of("Chat {player}|{message}"));
        SnarkExternalChatContext context = new SnarkExternalChatContext(
                "lightweightclans:clan_chat",
                "Lightweight Clans - Clan Chat",
                "LightweightClans",
                "chat",
                "Builders",
                "BLD",
                true,
                2
        );

        SnarkService builtInService = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                config,
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );
        SnarkService externalService = new SnarkService(
                new Random(0L),
                new CooldownManager(new SnarkTriggersConfig.Cooldowns(60, 20)),
                new SnarkFormatter("<white>[Server] <reset>"),
                config,
                new DeathCategoryClassifier(),
                new ChatCategoryClassifier(),
                new ChatBurstTracker(),
                new PlayerVisibilityChecker(server)
        );

        String builtIn = PlainTextComponentSerializer.plainText().serialize(
                builtInService.buildAutomaticChatReply(playerA, "lag again tonight")
        );
        String external = PlainTextComponentSerializer.plainText().serialize(
                externalService.buildAutomaticChatReply(playerB, "lag again tonight", context)
        );

        assertEquals(builtIn, external);
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

        SnarkMessagesConfig messagesConfig = new SnarkMessagesConfig(deathMessages, chatMessages);
        SnarkChancesConfig chancesConfig = new SnarkChancesConfig(deathChances, chatChances);
        SnarkTriggersConfig triggersConfig = new SnarkTriggersConfig(
                true,
                new SnarkTriggersConfig.DeathSnark(true),
                new SnarkTriggersConfig.ChatSnark(
                        true,
                        1,
                        false,
                        new SnarkTriggersConfig.ChatSnark.SpamBurst(3, 8, 12)
                ),
                new SnarkTriggersConfig.Cooldowns(60, 20),
                new SnarkTriggersConfig.Filters("snarkyserver.bypass", List.of(), List.of()),
                Map.of()
        );

        return new SnarkyConfig(
                "<white>[Server] <reset>",
                messagesConfig,
                chancesConfig,
                triggersConfig
        );
    }

    private SnarkyConfig deathCategoryConfig(String genericDeath, String pvpDeath) {
        SnarkyConfig config = configWithMessages(List.of(genericDeath), List.of("Chat"));
        EnumMap<DeathCategory, List<String>> deathMessages = new EnumMap<>(config.messagesConfig().deathMessages());
        deathMessages.put(DeathCategory.GENERIC, List.of(genericDeath));
        deathMessages.put(DeathCategory.PVP, List.of(pvpDeath));
        return new SnarkyConfig(
                config.prefix(),
                new SnarkMessagesConfig(deathMessages, config.messagesConfig().chatMessages()),
                config.chancesConfig(),
                config.triggersConfig()
        );
    }
}
