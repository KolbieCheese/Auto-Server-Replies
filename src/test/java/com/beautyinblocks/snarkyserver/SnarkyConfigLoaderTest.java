package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
