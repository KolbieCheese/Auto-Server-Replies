package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnarkyConfigLoaderTest {
    @Test
    void loadsLegacyFallbacksAndCategoryMaps() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("death-snark.chance", 0.35D);
        configuration.set("death-snark.chances.lava", 0.75D);
        configuration.set("chat-snark.chance", 0.15D);
        configuration.set("chat-snark.chances.question", 0.45D);
        configuration.set("messages.death-generic", List.of("generic death"));
        configuration.set("messages.chat-generic", List.of("generic chat"));

        SnarkyConfig config = SnarkyConfigLoader.load(configuration);

        assertEquals(0.35D, config.deathSnark().chanceFor(DeathCategory.FIRE));
        assertEquals(0.75D, config.deathSnark().chanceFor(DeathCategory.LAVA));
        assertEquals(0.15D, config.chatSnark().chanceFor(ChatCategory.CELEBRATION));
        assertEquals(0.45D, config.chatSnark().chanceFor(ChatCategory.QUESTION));
        assertEquals(List.of("generic death"), config.messages().deathMessagesFor(DeathCategory.EXPLOSION));
        assertEquals(List.of("generic chat"), config.messages().chatMessagesFor(ChatCategory.LAG));
        assertEquals(3, config.chatSnark().spamBurst().threshold());
        assertEquals(8, config.chatSnark().spamBurst().windowSeconds());
        assertEquals(12, config.chatSnark().spamBurst().maxMessageLength());
    }

    @Test
    void leavesGenericMessagePoolsEmptyWhenMissing() {
        YamlConfiguration configuration = new YamlConfiguration();

        SnarkyConfig config = SnarkyConfigLoader.load(configuration);

        assertEquals(List.of(), config.messages().deathMessagesFor(DeathCategory.GENERIC));
        assertEquals(List.of(), config.messages().chatMessagesFor(ChatCategory.GENERIC));
        assertEquals(List.of(), config.messages().deathMessagesFor(DeathCategory.LAVA));
        assertEquals(List.of(), config.messages().chatMessagesFor(ChatCategory.LAG));
    }

    @Test
    void bundledConfigResourceLoadsDefaultMessagePools() throws Exception {
        try (InputStream inputStream = SnarkyConfigLoaderTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(inputStream);
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );

            SnarkyConfig config = SnarkyConfigLoader.load(configuration);

            assertFalse(config.messages().deathMessagesFor(DeathCategory.GENERIC).isEmpty());
            assertFalse(config.messages().chatMessagesFor(ChatCategory.LAG).isEmpty());
            assertEquals("K y s {player}", config.messages().chatMessagesFor(ChatCategory.LAG).get(2));
        }
    }

    @Test
    void bundledConfigMessagesAreExportedAsSingleQuotedScalars() throws Exception {
        try (InputStream inputStream = SnarkyConfigLoaderTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(inputStream);
            String configText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher messagesSectionMatcher = Pattern.compile("(?ms)^messages:\\R(?<body>.*)$").matcher(configText);
            assertTrue(messagesSectionMatcher.find(), "Expected a messages section in config.yml");

            String messagesSection = messagesSectionMatcher.group("body");
            Pattern messageLinePattern = Pattern.compile("(?m)^\\s*-\\s+(.+)$");
            Matcher messageLineMatcher = messageLinePattern.matcher(messagesSection);

            int messageCount = 0;
            while (messageLineMatcher.find()) {
                messageCount++;
                String value = messageLineMatcher.group(1);
                assertTrue(
                        value.startsWith("'") && value.endsWith("'"),
                        () -> "Expected single-quoted message scalar, but got: " + value
                );
            }

            assertTrue(messageCount > 0, "Expected at least one messages.* list item");
            assertTrue(
                    configText.contains("'{killer}, {player} called you a little b*tch'"),
                    "Expected placeholders in exported messages to remain unchanged"
            );
            assertTrue(
                    configText.contains("'K y s {player}'"),
                    "Expected {player} placeholder to remain unchanged"
            );
        }
    }
}
