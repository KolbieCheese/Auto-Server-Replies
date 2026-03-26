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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnarkTestCommandTest {
    @Test
    void broadcastsForcedDeathPreview() {
        Server server = mock(Server.class);
        SnarkService service = mock(SnarkService.class);
        CooldownManager cooldownManager = mock(CooldownManager.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager);
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
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager);
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
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        SnarkTestCommand snarkTestCommand = new SnarkTestCommand(server, () -> service, () -> cooldownManager);
        when(sender.hasPermission("snarkyserver.test")).thenReturn(true);
        when(service.buildTestChatReply("Kolbie", ChatCategory.GREETING, "")).thenReturn(Component.text("[Server] preview"));

        snarkTestCommand.onCommand(sender, command, "snarktest", new String[]{"chat", "greeting", "Kolbie"});

        verify(service).buildTestChatReply("Kolbie", ChatCategory.GREETING, "");
        verify(server).broadcast(any(Component.class));
    }
}
