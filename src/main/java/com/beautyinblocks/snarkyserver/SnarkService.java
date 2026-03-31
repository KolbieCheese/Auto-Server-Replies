package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class SnarkService {
    private static final ReplyGate AUTOMATIC_GATE = new ReplyGate(true, true, true, true);
    private static final ReplyGate FORCED_GATE = new ReplyGate(false, false, false, false);

    private final RandomGenerator random;
    private final CooldownManager cooldownManager;
    private final SnarkFormatter formatter;
    private final SnarkyConfig config;
    private final DeathCategoryClassifier deathCategoryClassifier;
    private final ChatCategoryClassifier chatCategoryClassifier;
    private final ChatBurstTracker chatBurstTracker;
    private final PlayerVisibilityChecker playerVisibilityChecker;
    private final SnarkMessagesConfig testMessageFallbacks;

    public SnarkService(
            RandomGenerator random,
            CooldownManager cooldownManager,
            SnarkFormatter formatter,
            SnarkyConfig config,
            DeathCategoryClassifier deathCategoryClassifier,
            ChatCategoryClassifier chatCategoryClassifier,
            ChatBurstTracker chatBurstTracker,
            PlayerVisibilityChecker playerVisibilityChecker
    ) {
        this(
                random,
                cooldownManager,
                formatter,
                config,
                deathCategoryClassifier,
                chatCategoryClassifier,
                chatBurstTracker,
                playerVisibilityChecker,
                config.messagesConfig()
        );
    }

    public SnarkService(
            RandomGenerator random,
            CooldownManager cooldownManager,
            SnarkFormatter formatter,
            SnarkyConfig config,
            DeathCategoryClassifier deathCategoryClassifier,
            ChatCategoryClassifier chatCategoryClassifier,
            ChatBurstTracker chatBurstTracker,
            PlayerVisibilityChecker playerVisibilityChecker,
            SnarkMessagesConfig testMessageFallbacks
    ) {
        this.random = random;
        this.cooldownManager = cooldownManager;
        this.formatter = formatter;
        this.config = config;
        this.deathCategoryClassifier = deathCategoryClassifier;
        this.chatCategoryClassifier = chatCategoryClassifier;
        this.chatBurstTracker = chatBurstTracker;
        this.playerVisibilityChecker = playerVisibilityChecker;
        this.testMessageFallbacks = testMessageFallbacks;
    }

    public Component buildAutomaticDeathReply(Player player) {
        if (playerVisibilityChecker.isHidden(player)) {
            return null;
        }

        Player killer = player.getKiller();
        boolean shouldExposeKiller = killer != null && !playerVisibilityChecker.isHidden(killer);
        EntityDamageEvent lastDamageCause = player.getLastDamageCause();
        DeathCategory category = deathCategoryClassifier.classify(
                shouldExposeKiller,
                lastDamageCause == null ? null : lastDamageCause.getCause()
        );
        String killerName = shouldExposeKiller ? killer.getName() : "";
        return buildDeathReply(player, player.getName(), category, killerName, AUTOMATIC_GATE);
    }

    public Component buildAutomaticChatReply(Player player, String messageText) {
        return buildAutomaticChatReply(player, messageText, null);
    }

    public Component buildAutomaticChatReply(Player player, String messageText, SnarkExternalChatContext chatContext) {
        if (playerVisibilityChecker.isHidden(player)) {
            return null;
        }

        String normalized = normalize(messageText);
        Instant now = Instant.now();
        boolean spamBurstTriggered = chatBurstTracker.recordAndCheck(
                player.getUniqueId(),
                normalized,
                now,
                config.triggersConfig().chatSnark().spamBurst()
        );
        ChatCategory category = chatCategoryClassifier.classify(normalized, spamBurstTriggered);
        if (category == ChatCategory.GREETING) {
            return null;
        }
        return buildChatReply(player, player.getName(), category, normalized, AUTOMATIC_GATE, now);
    }

    public Component buildAutomaticJoinReply(Player player) {
        if (playerVisibilityChecker.isHidden(player)) {
            return null;
        }

        Instant now = Instant.now();
        return buildChatReply(player, player.getName(), ChatCategory.GREETING, "", AUTOMATIC_GATE, now);
    }

    public Component buildTestDeathReply(String playerName, DeathCategory category, String killerName) {
        return buildDeathReply(
                null,
                safeName(playerName),
                category,
                killerName,
                FORCED_GATE,
                testMessageFallbacks.deathMessagesFor(category)
        );
    }

    public Component buildTestChatReply(String playerName, ChatCategory category, String messageText) {
        return buildChatReply(
                null,
                safeName(playerName),
                category,
                normalize(messageText),
                FORCED_GATE,
                Instant.now(),
                testMessageFallbacks.chatMessagesFor(category)
        );
    }

    public Component buildRandomTestReply(String playerName) {
        String resolvedPlayer = safeName(playerName);
        if (random.nextBoolean()) {
            DeathCategory category = DeathCategory.values()[random.nextInt(DeathCategory.values().length)];
            return buildTestDeathReply(resolvedPlayer, category, "");
        }

        ChatCategory category = ChatCategory.values()[random.nextInt(ChatCategory.values().length)];
        return buildTestChatReply(resolvedPlayer, category, "");
    }

    public boolean isChatEnabled() {
        return config.triggersConfig().enabled() && config.triggersConfig().chatSnark().enabled();
    }

    public boolean shouldHandleChatAsync(String messageText) {
        return shouldHandleChatAsync(messageText, null);
    }

    public boolean shouldHandleChatAsync(String messageText, SnarkExternalChatContext chatContext) {
        return isChatEnabled() && !shouldSkipChatMessageLightweight(messageText);
    }

    public boolean shouldSkipChatMessageLightweight(String messageText) {
        String trimmed = normalize(messageText);
        if (trimmed.length() < config.triggersConfig().chatSnark().minMessageLength()) {
            return true;
        }
        if (config.triggersConfig().chatSnark().ignoreCommands() && trimmed.startsWith("/")) {
            return true;
        }

        return config.triggersConfig().filters().ignoredPrefixes().stream().anyMatch(trimmed::startsWith);
    }

    public SnarkyConfig config() {
        return config;
    }

    private Component buildDeathReply(Player player, String playerName, DeathCategory category, String killerName, ReplyGate gate) {
        return buildDeathReply(player, playerName, category, killerName, gate, List.of());
    }

    private Component buildDeathReply(
            Player player,
            String playerName,
            DeathCategory category,
            String killerName,
            ReplyGate gate,
            List<String> fallbackMessages
    ) {
        Instant now = Instant.now();
        if (!passesSharedChecks(player, gate, now, config.triggersConfig().deathSnark().enabled())) {
            return null;
        }
        if (gate.checkChance() && !passesChance(config.chancesConfig().deathChanceFor(category))) {
            return null;
        }

        Component component = render(config.messagesConfig().deathMessagesFor(category), fallbackMessages, Map.of(
                "player", playerName,
                "killer", normalize(killerName),
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
        return buildChatReply(player, playerName, category, messageText, gate, now, List.of());
    }

    private Component buildChatReply(
            Player player,
            String playerName,
            ChatCategory category,
            String messageText,
            ReplyGate gate,
            Instant now,
            List<String> fallbackMessages
    ) {
        if (!passesSharedChecks(player, gate, now, config.triggersConfig().chatSnark().enabled())) {
            return null;
        }
        if (gate.checkChance() && !passesChance(config.chancesConfig().chatChanceFor(category))) {
            return null;
        }

        Component component = render(config.messagesConfig().chatMessagesFor(category), fallbackMessages, Map.of(
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
        if (gate.checkEnabled() && (!config.triggersConfig().enabled() || !familyEnabled)) {
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
        if (!config.triggersConfig().filters().bypassPermission().isBlank() && player.hasPermission(config.triggersConfig().filters().bypassPermission())) {
            return false;
        }

        return config.triggersConfig().filters().ignoredWorlds().stream()
                .noneMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()));
    }

    private boolean passesChance(double chance) {
        return random.nextDouble() <= Math.max(0.0D, Math.min(1.0D, chance));
    }

    private Component render(List<String> messages, Map<String, String> values) {
        return render(messages, List.of(), values);
    }

    private Component render(List<String> messages, List<String> fallbackMessages, Map<String, String> values) {
        List<String> resolvedMessages = resolveMessages(messages, fallbackMessages);
        if (resolvedMessages.isEmpty()) {
            return null;
        }

        return formatter.format(resolvedMessages.get(random.nextInt(resolvedMessages.size())), values);
    }

    private List<String> resolveMessages(List<String> messages, List<String> fallbackMessages) {
        if (messages != null && !messages.isEmpty()) {
            return messages;
        }
        if (fallbackMessages != null && !fallbackMessages.isEmpty()) {
            return fallbackMessages;
        }
        return List.of();
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
