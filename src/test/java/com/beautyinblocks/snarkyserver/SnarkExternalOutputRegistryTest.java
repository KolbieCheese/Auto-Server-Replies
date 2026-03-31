package com.beautyinblocks.snarkyserver;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnarkExternalOutputRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void startupDiscoveryRegistersLightweightClansOutput() {
        CollectingHandler handler = new CollectingHandler();
        Logger logger = testLogger(handler);
        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        JavaPlugin owner = ownerPlugin(server, logger);
        Plugin externalPlugin = manifestPlugin("LightweightClans", validManifest(eventClassName()));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugins()).thenReturn(new Plugin[]{externalPlugin});
        SnarkExternalOutputRegistry registry = registry(owner, logger);

        registry.discoverLoadedPlugins();

        verify(pluginManager).registerEvent(
                eq(TestClanChatEvent.class),
                any(Listener.class),
                eq(EventPriority.MONITOR),
                any(EventExecutor.class),
                eq(owner),
                eq(true)
        );
        assertEquals(1, registry.listOutputs().size());
        assertFalse(registry.isOutputEnabled("lightweightclans:clan_chat"));
        assertTrue(registry.hasActiveListener("lightweightclans:clan_chat"));
        assertTrue(handler.messages().isEmpty());
    }

    @Test
    void duplicateDiscoveryDoesNotDoubleRegisterListeners() {
        Logger logger = testLogger(new CollectingHandler());
        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        JavaPlugin owner = ownerPlugin(server, logger);
        Plugin externalPlugin = manifestPlugin("LightweightClans", validManifest(eventClassName()));
        when(server.getPluginManager()).thenReturn(pluginManager);
        SnarkExternalOutputRegistry registry = registry(owner, logger);

        registry.discoverPlugin(externalPlugin);
        registry.discoverPlugin(externalPlugin);

        verify(pluginManager, times(1)).registerEvent(
                eq(TestClanChatEvent.class),
                any(Listener.class),
                eq(EventPriority.MONITOR),
                any(EventExecutor.class),
                eq(owner),
                eq(true)
        );
    }

    @Test
    void duplicateIdsFromDifferentPluginsAreSkippedAfterTheFirstWinner() {
        CollectingHandler handler = new CollectingHandler();
        Logger logger = testLogger(handler);
        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        JavaPlugin owner = ownerPlugin(server, logger);
        Plugin firstPlugin = manifestPlugin("LightweightClans", validManifest(eventClassName()));
        Plugin secondPlugin = manifestPlugin("OtherClans", validManifest(eventClassName()));
        when(server.getPluginManager()).thenReturn(pluginManager);
        SnarkExternalOutputRegistry registry = registry(owner, logger);

        registry.discoverPlugin(firstPlugin);
        registry.discoverPlugin(secondPlugin);

        verify(pluginManager, times(1)).registerEvent(
                eq(TestClanChatEvent.class),
                any(Listener.class),
                eq(EventPriority.MONITOR),
                any(EventExecutor.class),
                eq(owner),
                eq(true)
        );
        assertEquals("LightweightClans", registry.listOutputs().getFirst().sourcePlugin());
        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("conflicts")));
    }

    @Test
    void missingEventClassLogsWarningAndSkipsRegistration() {
        CollectingHandler handler = new CollectingHandler();
        Logger logger = testLogger(handler);
        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        JavaPlugin owner = ownerPlugin(server, logger);
        Plugin externalPlugin = manifestPlugin(
                "LightweightClans",
                """
                {
                  "plugin": "LightweightClans",
                  "version": 1,
                  "outputs": [
                    {
                      "id": "lightweightclans:clan_chat",
                      "displayName": "Lightweight Clans - Clan Chat",
                      "eventClass": "missing.ClanChatMessageEvent",
                      "kind": "chat",
                      "description": "Clan chat messages from Lightweight Clans"
                    }
                  ]
                }
                """
        );
        when(server.getPluginManager()).thenReturn(pluginManager);
        SnarkExternalOutputRegistry registry = registry(owner, logger);

        registry.discoverPlugin(externalPlugin);

        verify(pluginManager, never()).registerEvent(any(), any(), any(), any(), any(), any(Boolean.class));
        assertTrue(registry.listOutputs().isEmpty());
        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("could not load event class")));
    }

    private SnarkExternalOutputRegistry registry(JavaPlugin owner, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        SnarkService snarkService = mock(SnarkService.class);
        SnarkExternalChatEventBridge bridge = new SnarkExternalChatEventBridge(owner, snarkService, outputId -> true, logger);
        return new SnarkExternalOutputRegistry(
                owner,
                configuration,
                tempDir.resolve("triggers.yml").toFile(),
                TestSnarkConfigs.simpleConfig(true, true, true, 60, 20, 1.0D, 1.0D).triggersConfig(),
                new SnarkExternalOutputManifestLoader(logger),
                bridge,
                logger
        );
    }

    private JavaPlugin ownerPlugin(Server server, Logger logger) {
        JavaPlugin owner = mock(JavaPlugin.class);
        when(owner.getServer()).thenReturn(server);
        when(owner.getLogger()).thenReturn(logger);
        return owner;
    }

    private Plugin manifestPlugin(String name, String manifest) {
        Plugin plugin = mock(Plugin.class);
        byte[] bytes = manifest.getBytes(StandardCharsets.UTF_8);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getResource(SnarkExternalOutputManifestLoader.MANIFEST_PATH))
                .thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        return plugin;
    }

    private String validManifest(String eventClass) {
        return """
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
                """.formatted(eventClass);
    }

    private String eventClassName() {
        return TestClanChatEvent.class.getName();
    }

    private Logger testLogger(Handler handler) {
        Logger logger = Logger.getLogger("snarky-registry-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        return logger;
    }

    private static final class CollectingHandler extends Handler {
        private final java.util.ArrayList<String> messages = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }
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
