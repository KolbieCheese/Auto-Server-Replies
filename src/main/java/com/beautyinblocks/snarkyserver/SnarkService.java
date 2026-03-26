package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class SnarkService {
    private static final ReplyGate AUTOMATIC_GATE = new ReplyGate(true, true, true, true);
    private static final ReplyGate FORCED_GATE = new ReplyGate(false, false, false, false);

    private final RandomGenerator random;
    private final CooldownManager cooldownManager;
    private final SnarkFormatter formatter;
    private final SnarkyConfig config;
    private final ChatCategoryClassifier chatCategoryClassifier;
    private final ChatBurstTracker chatBurstTracker;

    public SnarkService(
            RandomGenerator random,
            CooldownManager cooldownManager,
            SnarkFormatter formatter,
            SnarkyConfig config,
            ChatCategoryClassifier chatCategoryClassifier,
            ChatBurstTracker chatBurstTracker
    ) {
        this.random = random;
        this.cooldownManager = cooldownManager;
        this.formatter = formatter;
        this.config = config;
        this.chatCategoryClassifier = chatCategoryClassifier;
        this.chatBurstTracker = chatBurstTracker;
    }

    public Component buildAutomaticDeathReply(Player player, DeathCategory category, String killerName) {
        return buildDeathReply(player, player.getName(), category, killerName, AUTOMATIC_GATE);
    }

    public Component buildAutomaticChatReply(Player player, String messageText) {
        String normalized = normalize(messageText);
        Instant now = Instant.now();
        boolean spamBurstTriggered = chatBurstTracker.recordAndCheck(
                player.getUniqueId(),
                normalized,
                now,
                config.chatSnark().spamBurst()
        );
        ChatCategory category = chatCategoryClassifier.classify(normalized, spamBurstTriggered);
        return buildChatReply(player, player.getName(), category, normalized, AUTOMATIC_GATE, now);
    }

    public Component buildTestDeathReply(String playerName, DeathCategory category, String killerName) {
        return buildDeathReply(null, safeName(playerName), category, killerName, FORCED_GATE);
    }

    public Component buildTestChatReply(String playerName, ChatCategory category, String messageText) {
        String normalized = normalize(messageText);
        String resolvedMessage = normalized.isBlank() ? category.defaultTestMessage() : normalized;
        return buildChatReply(null, safeName(playerName), category, resolvedMessage, FORCED_GATE, Instant.now());
    }

    public Component buildRandomTestReply(String playerName) {
        String resolvedPlayer = safeName(playerName);
        if (random.nextBoolean()) {
            DeathCategory category = DeathCategory.values()[random.nextInt(DeathCategory.values().length)];
            String killerName = category == DeathCategory.PVP ? "someone" : "";
            return buildTestDeathReply(resolvedPlayer, category, killerName);
        }

        ChatCategory category = ChatCategory.values()[random.nextInt(ChatCategory.values().length)];
        return buildTestChatReply(resolvedPlayer, category, category.defaultTestMessage());
    }

    public boolean isChatEnabled() {
        return config.enabled() && config.chatSnark().enabled();
    }

    public boolean shouldHandleChatAsync(String messageText) {
        return isChatEnabled() && !shouldSkipChatMessageLightweight(messageText);
    }

    public boolean shouldSkipChatMessageLightweight(String messageText) {
        String trimmed = normalize(messageText);
        if (trimmed.length() < config.chatSnark().minMessageLength()) {
            return true;
        }
        if (config.chatSnark().ignoreCommands() && trimmed.startsWith("/")) {
            return true;
        }

        return config.filters().ignoredPrefixes().stream().anyMatch(trimmed::startsWith);
    }

    public SnarkyConfig config() {
        return config;
    }

    private Component buildDeathReply(Player player, String playerName, DeathCategory category, String killerName, ReplyGate gate) {
        Instant now = Instant.now();
        if (!passesSharedChecks(player, gate, now, config.deathSnark().enabled())) {
            return null;
        }
        if (gate.checkChance() && !passesChance(config.deathSnark().chanceFor(category))) {
            return null;
        }

        Component component = render(config.messages().deathMessagesFor(category), Map.of(
                "player", playerName,
                "killer", safeNameOrFallback(killerName, "someone"),
                "message", ""
        ));
        if (component != null && gate.checkCooldowns() && player != null) {
            cooldownManager.markResponded(player.getUniqueId(), now);
        }
        return component;
    }

    private Component buildChatReply(
            Player player,
            String playerName,
            ChatCategory category,
            String messageText,
            ReplyGate gate,
            Instant now
    ) {
        if (!passesSharedChecks(player, gate, now, config.chatSnark().enabled())) {
            return null;
        }
        if (gate.checkChance() && !passesChance(config.chatSnark().chanceFor(category))) {
            return null;
        }

        Component component = render(config.messages().chatMessagesFor(category), Map.of(
                "player", playerName,
                "killer", "",
                "message", messageText
        ));
        if (component != null && gate.checkCooldowns() && player != null) {
            cooldownManager.markResponded(player.getUniqueId(), now);
        }
        return component;
    }

    private boolean passesSharedChecks(Player player, ReplyGate gate, Instant now, boolean familyEnabled) {
        if (gate.checkEnabled() && (!config.enabled() || !familyEnabled)) {
            return false;
        }
        if (!gate.checkFilters() && !gate.checkCooldowns()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        if (gate.checkFilters() && !passesPlayerFilters(player)) {
            return false;
        }

        return !gate.checkCooldowns() || cooldownManager.canRespond(player.getUniqueId(), now);
    }

    private boolean passesPlayerFilters(Player player) {
        if (!config.filters().bypassPermission().isBlank() && player.hasPermission(config.filters().bypassPermission())) {
            return false;
        }

        return config.filters().ignoredWorlds().stream()
                .noneMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()));
    }

    private boolean passesChance(double chance) {
        return random.nextDouble() <= Math.max(0.0D, Math.min(1.0D, chance));
    }

    private Component render(List<String> messages, Map<String, String> values) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return formatter.format(messages.get(random.nextInt(messages.size())), values);
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private String safeName(String playerName) {
        return safeNameOrFallback(playerName, "Player");
    }

    private String safeNameOrFallback(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private record ReplyGate(boolean checkEnabled, boolean checkFilters, boolean checkChance, boolean checkCooldowns) {
    }
}
