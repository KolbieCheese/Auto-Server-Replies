package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class SnarkService {
    private final RandomGenerator random;
    private final CooldownManager cooldownManager;
    private final SnarkFormatter formatter;
    private final SnarkyConfig config;
    private final ChatCategoryClassifier chatCategoryClassifier;

    public SnarkService(
            RandomGenerator random,
            CooldownManager cooldownManager,
            SnarkFormatter formatter,
            SnarkyConfig config,
            ChatCategoryClassifier chatCategoryClassifier
    ) {
        this.random = random;
        this.cooldownManager = cooldownManager;
        this.formatter = formatter;
        this.config = config;
        this.chatCategoryClassifier = chatCategoryClassifier;
    }

    public Component buildDeathReply(Player player, DeathCategory category, String killerName) {
        if (!canRespondForPlayer(player) || !config.deathSnark().enabled()) {
            return null;
        }

        double chance = deathChanceForCategory(category);
        if (!passesChance(chance)) {
            return null;
        }

        List<String> messages = switch (category) {
            case LAVA -> config.messages().deathLava();
            case FALL -> config.messages().deathFall();
            case PVP -> config.messages().deathPvp();
            case DROWNING -> config.messages().deathDrowning();
            case FIRE -> config.messages().deathFire();
            case VOID -> config.messages().deathVoid();
            case GENERIC -> config.messages().deathGeneric();
            default -> config.messages().deathGeneric();
        };

        String message = pickRandom(messages);
        cooldownManager.markResponded(player.getUniqueId(), Instant.now());
        return formatter.format(message, Map.of(
                "player", player.getName(),
                "killer", killerName == null ? "someone" : killerName,
                "message", ""
        ));
    }

    public Component buildChatReply(Player player, String messageText) {
        if (!canRespondForPlayer(player)
                || !config.chatSnark().enabled()) {
            return null;
        }

        ChatCategory category = chatCategoryClassifier.classify(messageText);
        if (!passesChance(chatChanceForCategory(category))) {
            return null;
        }

        String template = pickRandom(chatMessagesForCategory(category));
        cooldownManager.markResponded(player.getUniqueId(), Instant.now());
        return formatter.format(template, Map.of(
                "player", player.getName(),
                "killer", "",
                "message", messageText
        ));
    }

    public boolean isChatEnabled() {
        return config.enabled() && config.chatSnark().enabled();
    }

    public boolean shouldHandleChatAsync(String messageText) {
        return isChatEnabled() && !shouldSkipChatMessageLightweight(messageText);
    }

    public boolean shouldSkipChatMessageLightweight(String messageText) {
        String trimmed = messageText == null ? "" : messageText.trim();
        if (trimmed.length() < config.chatSnark().minMessageLength()) {
            return true;
        }
        if (config.chatSnark().ignoreCommands() && trimmed.startsWith("/")) {
            return true;
        }

        return config.filters().ignoredPrefixes().stream().anyMatch(trimmed::startsWith);
    }

    private boolean canRespondForPlayer(Player player) {
        if (!config.enabled()) {
            return false;
        }

        if (!config.filters().bypassPermission().isBlank() && player.hasPermission(config.filters().bypassPermission())) {
            return false;
        }

        if (isIgnoredWorld(player.getWorld().getName())) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        return cooldownManager.canRespond(playerId, Instant.now());
    }

    private boolean passesChance(double chance) {
        return random.nextDouble() <= Math.max(0.0D, Math.min(1.0D, chance));
    }

    private double chatChanceForCategory(ChatCategory category) {
        SnarkyConfig.ChatSnark.Chances chances = config.chatSnark().chances();
        return switch (category) {
            case QUESTION -> chances.question();
            case EXCITED -> chances.excited();
            case GREETING -> chances.greeting();
            case GENERIC -> chances.generic();
            default -> chances.generic();
        };
    }

    private List<String> chatMessagesForCategory(ChatCategory category) {
        return switch (category) {
            case QUESTION -> config.messages().chatQuestion();
            case EXCITED -> config.messages().chatExcited();
            case GREETING -> config.messages().chatGreeting();
            case GENERIC -> config.messages().chatGeneric();
            default -> config.messages().chatGeneric();
        };
    }

    private double deathChanceForCategory(DeathCategory category) {
        SnarkyConfig.DeathSnark.Chances chances = config.deathSnark().chances();
        return switch (category) {
            case LAVA -> chances.lava();
            case FALL -> chances.fall();
            case PVP -> chances.pvp();
            case DROWNING -> chances.drowning();
            case FIRE -> chances.fire();
            case VOID -> chances.voidDeath();
            case GENERIC -> chances.generic();
            default -> chances.generic();
        };
    }

    private String pickRandom(List<String> messages) {
        return messages.get(random.nextInt(messages.size()));
    }

    private boolean isIgnoredWorld(String worldName) {
        return config.filters().ignoredWorlds().stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
    }
}
