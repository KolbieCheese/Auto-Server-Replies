package com.beautyinblocks.snarkyserver;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnarkExternalOutputManifestLoaderTest {
    @Test
    void loadsValidManifest() {
        CollectingHandler handler = new CollectingHandler();
        Logger logger = testLogger(handler);
        Plugin plugin = pluginWithManifest(
                "LightweightClans",
                """
                {
                  "plugin": "LightweightClans",
                  "version": 1,
                  "outputs": [
                    {
                      "id": "lightweightclans:clan_chat",
                      "displayName": "Lightweight Clans - Clan Chat",
                      "eventClass": "example.ClanChatMessageEvent",
                      "kind": "chat",
                      "description": "Clan chat messages from Lightweight Clans"
                    }
                  ]
                }
                """
        );
        SnarkExternalOutputManifestLoader loader = new SnarkExternalOutputManifestLoader(logger);

        Optional<SnarkExternalOutputManifest> manifest = loader.load(plugin);

        assertTrue(manifest.isPresent());
        assertEquals("LightweightClans", manifest.get().plugin());
        assertEquals(1, manifest.get().outputs().size());
        assertEquals("lightweightclans:clan_chat", manifest.get().outputs().getFirst().id());
        assertTrue(handler.messages().isEmpty());
    }

    @Test
    void malformedManifestLogsWarningAndReturnsEmpty() {
        CollectingHandler handler = new CollectingHandler();
        Logger logger = testLogger(handler);
        Plugin plugin = pluginWithManifest("LightweightClans", "{ this is not valid json");
        SnarkExternalOutputManifestLoader loader = new SnarkExternalOutputManifestLoader(logger);

        Optional<SnarkExternalOutputManifest> manifest = loader.load(plugin);

        assertTrue(manifest.isEmpty());
        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("Failed to parse manifest JSON")));
    }

    @Test
    void ignoresUnknownManifestVersionSafely() {
        CollectingHandler handler = new CollectingHandler();
        Logger logger = testLogger(handler);
        Plugin plugin = pluginWithManifest(
                "LightweightClans",
                """
                {
                  "plugin": "LightweightClans",
                  "version": 2,
                  "outputs": []
                }
                """
        );
        SnarkExternalOutputManifestLoader loader = new SnarkExternalOutputManifestLoader(logger);

        Optional<SnarkExternalOutputManifest> manifest = loader.load(plugin);

        assertTrue(manifest.isEmpty());
        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("Unsupported manifest version")));
    }

    private Plugin pluginWithManifest(String name, String manifestJson) {
        Plugin plugin = mock(Plugin.class);
        byte[] bytes = manifestJson.getBytes(StandardCharsets.UTF_8);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getResource(SnarkExternalOutputManifestLoader.MANIFEST_PATH))
                .thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        return plugin;
    }

    private Logger testLogger(Handler handler) {
        Logger logger = Logger.getLogger("snarky-manifest-" + UUID.randomUUID());
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
}
