package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SnarkExternalChatEventBridgeTest {
    @Test
    void disabledExternalOutputsDoNotTriggerSnarkyResponses() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        SnarkService snarkService = mock(SnarkService.class);
        when(plugin.getServer()).thenReturn(server);
        SnarkExternalChatEventBridge bridge = new SnarkExternalChatEventBridge(plugin, snarkService, outputId -> false, Logger.getLogger("test"));
        Player sender = mock(Player.class);
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614170010");
        when(sender.getUniqueId()).thenReturn(senderUuid);

        bridge.handleEvent(output(), new ClanChatMessageEvent(sender, senderUuid, "Builders", "BLD", "hello clan", false, false, List.of(sender)));

        verifyNoInteractions(snarkService);
        verify(server, never()).broadcast(any(Component.class));
    }

    @Test
    void enabledExternalOutputsTriggerSnarkyResponsesAndCaptureContext() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        SnarkService snarkService = mock(SnarkService.class);
        Player sender = mock(Player.class);
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614170011");
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(senderUuid)).thenReturn(sender);
        when(sender.isOnline()).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(senderUuid);
        when(snarkService.shouldHandleChatAsync(eq("hello clan"), any(SnarkExternalChatContext.class))).thenReturn(true);
        when(snarkService.buildAutomaticChatReply(eq(sender), eq("hello clan"), any(SnarkExternalChatContext.class)))
                .thenReturn(Component.text("[Server] reply"));
        SnarkExternalChatEventBridge bridge = new SnarkExternalChatEventBridge(plugin, snarkService, outputId -> true, Logger.getLogger("test"));

        bridge.handleEvent(output(), new ClanChatMessageEvent(sender, senderUuid, "Builders", "BLD", "hello clan", true, false, List.of(sender)));

        ArgumentCaptor<SnarkExternalChatContext> contextCaptor = ArgumentCaptor.forClass(SnarkExternalChatContext.class);
        verify(snarkService).buildAutomaticChatReply(eq(sender), eq("hello clan"), contextCaptor.capture());
        verify(server).broadcast(any(Component.class));
        assertEquals("lightweightclans:clan_chat", contextCaptor.getValue().outputId());
        assertEquals("Builders", contextCaptor.getValue().clanName());
        assertEquals("BLD", contextCaptor.getValue().clanTag());
        assertEquals(1, contextCaptor.getValue().recipientCount());
        assertEquals(true, contextCaptor.getValue().toggleRouted());
    }

    @Test
    void cancelledExternalEventsAreIgnored() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        SnarkService snarkService = mock(SnarkService.class);
        when(plugin.getServer()).thenReturn(server);
        SnarkExternalChatEventBridge bridge = new SnarkExternalChatEventBridge(plugin, snarkService, outputId -> true, Logger.getLogger("test"));
        Player sender = mock(Player.class);
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614170012");
        when(sender.getUniqueId()).thenReturn(senderUuid);
        ClanChatMessageEvent event = new ClanChatMessageEvent(sender, senderUuid, "Builders", "BLD", "hello clan", false, false, List.of(sender));
        event.setCancelled(true);

        bridge.handleEvent(output(), event);

        verifyNoInteractions(snarkService);
    }

    @Test
    void asyncExternalEventsScheduleBackToMainThread() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        SnarkService snarkService = mock(SnarkService.class);
        Player sender = mock(Player.class);
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614170013");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(senderUuid)).thenReturn(sender);
        when(sender.isOnline()).thenReturn(true);
        when(sender.getUniqueId()).thenReturn(senderUuid);
        when(snarkService.shouldHandleChatAsync(eq("hello clan"), any(SnarkExternalChatContext.class))).thenReturn(true);
        when(snarkService.buildAutomaticChatReply(eq(sender), eq("hello clan"), any(SnarkExternalChatContext.class)))
                .thenReturn(Component.text("[Server] reply"));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return task;
        });
        SnarkExternalChatEventBridge bridge = new SnarkExternalChatEventBridge(plugin, snarkService, outputId -> true, Logger.getLogger("test"));

        bridge.handleEvent(output(), new ClanChatMessageEvent(sender, senderUuid, "Builders", "BLD", "hello clan", false, true, List.of(sender)));

        verify(scheduler).runTask(eq(plugin), any(Runnable.class));
        verify(server).broadcast(any(Component.class));
    }

    private SnarkExternalOutput output() {
        return new SnarkExternalOutput(
                "lightweightclans:clan_chat",
                "Lightweight Clans - Clan Chat",
                "LightweightClans",
                ClanChatMessageEvent.class.getName(),
                "chat",
                "Clan chat messages from Lightweight Clans"
        );
    }

    public static final class ClanChatMessageEvent extends Event implements org.bukkit.event.Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();

        private final Player sender;
        private final UUID senderUuid;
        private final String clanName;
        private final String clanTag;
        private final String plainMessage;
        private final Component messageComponent;
        private final boolean toggleRouted;
        private final List<Player> recipients;
        private boolean cancelled;

        public ClanChatMessageEvent(
                Player sender,
                UUID senderUuid,
                String clanName,
                String clanTag,
                String plainMessage,
                boolean toggleRouted,
                boolean asynchronous,
                List<Player> recipients
        ) {
            super(asynchronous);
            this.sender = sender;
            this.senderUuid = senderUuid;
            this.clanName = clanName;
            this.clanTag = clanTag;
            this.plainMessage = plainMessage;
            this.messageComponent = Component.text(plainMessage);
            this.toggleRouted = toggleRouted;
            this.recipients = recipients;
        }

        public Player getSender() {
            return sender;
        }

        public UUID getSenderUuid() {
            return senderUuid;
        }

        public String getClanName() {
            return clanName;
        }

        public String getClanTag() {
            return clanTag;
        }

        public String getPlainMessage() {
            return plainMessage;
        }

        public Component getMessageComponent() {
            return messageComponent;
        }

        public boolean isToggleRouted() {
            return toggleRouted;
        }

        public List<Player> getRecipients() {
            return recipients;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            this.cancelled = cancel;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }
}
