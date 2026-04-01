package com.beautyinblocks.snarkyserver;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnarkExternalOutputDiscoveryListenerTest {
    @TempDir
    Path tempDir;

    @Test
    void pluginEnableDiscoveryWorksAndReenableDoesNotLeaveDuplicateListeners() {
        Logger logger = Logger.getLogger("snarky-discovery-" + UUID.randomUUID());
        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        JavaPlugin owner = mock(JavaPlugin.class);
        Plugin externalPlugin = manifestPlugin();
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(owner.getServer()).thenReturn(server);
        when(owner.getLogger()).thenReturn(logger);
        SnarkExternalChatEventBridge bridge = new SnarkExternalChatEventBridge(owner, mock(SnarkService.class), outputId -> null, logger);
        SnarkExternalOutputRegistry registry = new SnarkExternalOutputRegistry(
                owner,
                new YamlConfiguration(),
                tempDir.resolve("triggers.yml").toFile(),
                TestSnarkConfigs.simpleConfig(true, true, true, 60, 20, 1.0D, 1.0D).triggersConfig(),
                new SnarkExternalOutputManifestLoader(logger),
                bridge,
                logger
        );
        SnarkExternalOutputDiscoveryListener listener = new SnarkExternalOutputDiscoveryListener(registry);
        PluginEnableEvent enableEvent = mock(PluginEnableEvent.class);
        PluginDisableEvent disableEvent = mock(PluginDisableEvent.class);
        when(enableEvent.getPlugin()).thenReturn(externalPlugin);
        when(disableEvent.getPlugin()).thenReturn(externalPlugin);

        listener.onPluginEnable(enableEvent);
        listener.onPluginDisable(disableEvent);
        listener.onPluginEnable(enableEvent);

        verify(pluginManager, times(2)).registerEvent(
                eq(TestClanChatEvent.class),
                any(Listener.class),
                eq(EventPriority.MONITOR),
                any(EventExecutor.class),
                eq(owner),
                eq(true)
        );
        assertTrue(registry.hasActiveListener("lightweightclans:clan_chat"));
        listener.onPluginDisable(disableEvent);
        assertFalse(registry.hasActiveListener("lightweightclans:clan_chat"));
    }

    private Plugin manifestPlugin() {
        Plugin plugin = mock(Plugin.class);
        byte[] bytes = """
                {
                  "plugin": "LightweightClans",
                  "version": 1,
                  "outputs": [
                    {
                      "id": "lightweightclans:clan_chat",
                      "displayName": "Lightweight Clans - Clan Chat",
                      "eventClass": "%s",
                      "kind": "chat",
                      "description": "Clan chat messages from Lightweight Clans"
                    }
                  ]
                }
                """.formatted(TestClanChatEvent.class.getName()).getBytes(StandardCharsets.UTF_8);
        when(plugin.getName()).thenReturn("LightweightClans");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getResource(SnarkExternalOutputManifestLoader.MANIFEST_PATH))
                .thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        return plugin;
    }

    public static final class TestClanChatEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        public TestClanChatEvent() {
            super(false);
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
