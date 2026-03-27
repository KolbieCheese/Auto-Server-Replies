package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnarkyConfigLoaderTest {
    @Test
    void loadsSplitFilesWithLegacyChanceFallbacksAndCategoryMaps() {
        YamlConfiguration messagesConfiguration = new YamlConfiguration();
        YamlConfiguration chancesConfiguration = new YamlConfiguration();
        YamlConfiguration triggersConfiguration = new YamlConfiguration();

        chancesConfiguration.set("death-snark.chance", 0.35D);
        chancesConfiguration.set("death-snark.chances.lava", 0.75D);
        chancesConfiguration.set("chat-snark.chance", 0.15D);
        chancesConfiguration.set("chat-snark.chances.question", 0.45D);
        messagesConfiguration.set("messages.death-generic", List.of("generic death"));
        messagesConfiguration.set("messages.chat-generic", List.of("generic chat"));

        SnarkyConfig config = SnarkyConfigLoader.load(messagesConfiguration, chancesConfiguration, triggersConfiguration);
        SnarkChancesConfig chancesConfig = config.chancesConfig();
        SnarkMessagesConfig messagesConfig = config.messagesConfig();
        SnarkTriggersConfig triggersConfig = config.triggersConfig();

        assertEquals(0.35D, chancesConfig.deathChanceFor(DeathCategory.FIRE));
        assertEquals(0.75D, chancesConfig.deathChanceFor(DeathCategory.LAVA));
        assertEquals(0.15D, chancesConfig.chatChanceFor(ChatCategory.CELEBRATION));
        assertEquals(0.45D, chancesConfig.chatChanceFor(ChatCategory.QUESTION));
        assertEquals(List.of("generic death"), messagesConfig.deathMessagesFor(DeathCategory.EXPLOSION));
        assertEquals(List.of("generic chat"), messagesConfig.chatMessagesFor(ChatCategory.LAG));
        assertEquals(3, triggersConfig.chatSnark().spamBurst().threshold());
        assertEquals(8, triggersConfig.chatSnark().spamBurst().windowSeconds());
        assertEquals(12, triggersConfig.chatSnark().spamBurst().maxMessageLength());
    }

    @Test
    void leavesGenericMessagePoolsEmptyWhenMissing() {
        YamlConfiguration configuration = new YamlConfiguration();

        SnarkMessagesConfig messagesConfig = SnarkyConfigLoader.loadMessages(configuration);

        assertEquals(List.of(), messagesConfig.deathMessagesFor(DeathCategory.GENERIC));
        assertEquals(List.of(), messagesConfig.chatMessagesFor(ChatCategory.GENERIC));
        assertEquals(List.of(), messagesConfig.deathMessagesFor(DeathCategory.LAVA));
        assertEquals(List.of(), messagesConfig.chatMessagesFor(ChatCategory.LAG));
    }

    @Test
    void bundledMessagesResourceLoadsDefaultMessagePools() throws Exception {
        String messagesText = readBundledMessagesText();
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(messagesText);

        SnarkMessagesConfig messagesConfig = SnarkyConfigLoader.loadMessages(configuration);

        assertFalse(messagesConfig.deathMessagesFor(DeathCategory.GENERIC).isEmpty());
        assertFalse(messagesConfig.chatMessagesFor(ChatCategory.LAG).isEmpty());
        assertEquals("K y s {player}", messagesConfig.chatMessagesFor(ChatCategory.LAG).get(2));
    }

    @Test
    void bundledMessagesAreExportedAsSingleQuotedScalars() throws Exception {
        String messagesText = readBundledMessagesText();
        Matcher messagesSectionMatcher = Pattern.compile("(?ms)^messages:\\R(?<body>.*)$").matcher(messagesText);
        assertTrue(messagesSectionMatcher.find(), "Expected a messages section in messages.yml");

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
                messagesText.contains("'{killer}, {player} called you a little b*tch'"),
                "Expected placeholders in exported messages to remain unchanged"
        );
        assertTrue(
                messagesText.contains("'K y s {player}'"),
                "Expected {player} placeholder to remain unchanged"
        );
    }

    private String readBundledMessagesText() throws IOException {
        Path messagesPath = Path.of("src/main/resources/messages.yml");
        assertTrue(Files.exists(messagesPath), "Expected src/main/resources/messages.yml to exist");
        return Files.readString(messagesPath, StandardCharsets.UTF_8);
    }
}
