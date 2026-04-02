package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnarkTestCommandTest {
    @Test
    void broadcastsForcedDeathPreview() {
        Server server = mock(Server.class);
        SnarkService service = mock(SnarkService.class);
        CooldownManager cooldownManager = mock(CooldownManager.class);
        SnarkExternalOutputRegistry externalOutputRegistry = mock(SnarkExternalOutputRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager, () -> externalOutputRegistry);
        when(sender.hasPermission("snarkyserver.test")).thenReturn(true);
        when(service.buildTestDeathReply("Kolbie", DeathCategory.LAVA, "")).thenReturn(Component.text("[Server] preview"));

        snarkTestCommand.onCommand(sender, command, "snarktest", new String[]{"death", "lava", "Kolbie"});

        verify(service).buildTestDeathReply("Kolbie", DeathCategory.LAVA, "");
        verify(server).broadcast(any(Component.class));
    }

    @Test
    void reportsCooldownsForOnlinePlayers() {
        Server server = mock(Server.class);
        SnarkService service = mock(SnarkService.class);
        CooldownManager cooldownManager = mock(CooldownManager.class);
        SnarkExternalOutputRegistry externalOutputRegistry = mock(SnarkExternalOutputRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager, () -> externalOutputRegistry);
        when(sender.hasPermission("snarkyserver.test")).thenReturn(true);
        when(server.getPlayerExact("Kolbie")).thenReturn(player);
        when(player.getName()).thenReturn("Kolbie");
        when(player.getUniqueId()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"));
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(cooldownManager.remainingGlobal(any(Instant.class))).thenReturn(java.time.Duration.ofSeconds(5));
        when(cooldownManager.remainingForPlayer(eq(player.getUniqueId()), any(Instant.class))).thenReturn(java.time.Duration.ZERO);

        snarkTestCommand.onCommand(sender, command, "snarktest", new String[]{"cooldowns", "Kolbie"});

        verify(sender).sendMessage(contains("Global cooldown"));
        verify(sender).sendMessage(contains("Kolbie cooldown"));
    }

    @Test
    void chatPreviewUsesBlankMessageWhenOmitted() {
        Server server = mock(Server.class);
        SnarkService service = mock(SnarkService.class);
        CooldownManager cooldownManager = mock(CooldownManager.class);
        SnarkExternalOutputRegistry externalOutputRegistry = mock(SnarkExternalOutputRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager, () -> externalOutputRegistry);
        when(sender.hasPermission("snarkyserver.test")).thenReturn(true);
        when(service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "")).thenReturn(Component.text("[Server] preview"));

        snarkTestCommand.onCommand(sender, command, "snarktest", new String[]{"chat", "greeting", "Kolbie"});

        verify(service).buildTestChatReply("Kolbie", ChatCategory.GREETING, "");
        verify(server).broadcast(any(Component.class));
    }

    @Test
    void listShowsBuiltInAndExternalOutputs() {
        Server server = mock(Server.class);
        SnarkService service = mock(SnarkService.class);
        CooldownManager cooldownManager = mock(CooldownManager.class);
        SnarkExternalOutputRegistry externalOutputRegistry = mock(SnarkExternalOutputRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager, () -> externalOutputRegistry);
        when(sender.hasPermission("snarkyserver.test")).thenReturn(true);
        when(service.config()).thenReturn(TestSnarkConfigs.simpleConfig(true, true, true, 60, 20, 1.0D, 1.0D));
        when(externalOutputRegistry.listOutputs()).thenReturn(List.of(
                new SnarkExternalOutputRegistry.OutputStatus(
                        "lightweightclans:clan_chat",
                        "Lightweight Clans - Clan Chat",
                        "LightweightClans",
                        "chat",
                        "example.ClanChatMessageEvent",
                        "Clan chat messages from Lightweight Clans",
                        false,
                        true
                )
        ));

        snarkTestCommand.onCommand(sender, command, "snarktest", new String[]{"list"});

        verify(sender).sendMessage(contains("death-snark"));
        verify(sender).sendMessage(contains("chat-snark"));
        verify(sender).sendMessage(contains("lightweightclans:clan_chat"));
    }

    @Test
    void statusShowsEffectiveConfigAndPlayerSpecificBlockers() {
        Server server = mock(Server.class);
        SnarkService service = mock(SnarkService.class);
        CooldownManager cooldownManager = mock(CooldownManager.class);
        SnarkExternalOutputRegistry externalOutputRegistry = mock(SnarkExternalOutputRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager, () -> externalOutputRegistry);
        when(sender.hasPermission("snarkyserver.test")).thenReturn(true);
        when(service.config()).thenReturn(TestSnarkConfigs.simpleConfig(true, true, true, 120, 20, 0.025D, 0.0125D));
        when(server.getPlayerExact("Kolbie")).thenReturn(player);
        when(player.getName()).thenReturn("Kolbie");
        when(player.getUniqueId()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174998"));
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(player.hasPermission("snarkyserver.bypass")).thenReturn(true);
        when(cooldownManager.remainingGlobal(any(Instant.class))).thenReturn(java.time.Duration.ofSeconds(4));
        when(cooldownManager.remainingForPlayer(eq(player.getUniqueId()), any(Instant.class))).thenReturn(java.time.Duration.ofSeconds(18));
        when(externalOutputRegistry.listOutputs()).thenReturn(List.of(
                new SnarkExternalOutputRegistry.OutputStatus(
                        "lightweightclans:clan_chat",
                        "Lightweight Clans - Clan Chat",
                        "LightweightClans",
                        "chat",
                        "example.ClanChatMessageEvent",
                        "Clan chat messages from Lightweight Clans",
                        true,
                        true
                )
        ));

        snarkTestCommand.onCommand(sender, command, "snarktest", new String[]{"status", "Kolbie"});

        verify(sender).sendMessage(contains("Snarky Server status"));
        verify(sender).sendMessage(contains("generic chance 2.50%"));
        verify(sender).sendMessage(contains("generic chance 1.25%"));
        verify(sender).sendMessage(contains("1 discovered, 1 enabled, 1 active listeners"));
        verify(sender).sendMessage(contains("player check (Kolbie)"));
        verify(sender).sendMessage(contains("Low default chances are configured"));
        verify(server, never()).broadcast(any(Component.class));
    }
}
