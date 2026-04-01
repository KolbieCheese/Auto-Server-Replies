package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;

public final class SnarkExternalChatEventBridge {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final SnarkService snarkService;
    private final Function<String, SnarkTriggersConfig.ExternalOutputToggle> toggleLookup;
    private final Logger logger;
    private final AtomicBoolean discordSrvUnavailableWarned = new AtomicBoolean(false);

    public SnarkExternalChatEventBridge(
            JavaPlugin plugin,
            SnarkService snarkService,
            Function<String, SnarkTriggersConfig.ExternalOutputToggle> toggleLookup,
            Logger logger
    ) {
        this.plugin = plugin;
        this.snarkService = snarkService;
        this.toggleLookup = toggleLookup;
        this.logger = logger;
    }

    public EventExecutor createExecutor(SnarkExternalOutput output) {
        return (listener, event) -> handleEvent(output, event);
    }

    void handleEvent(SnarkExternalOutput output, Event event) {
        ExtractedExternalChat extracted = extractChat(output, event);
        if (extracted == null) {
            return;
        }

        boolean cancelled = event instanceof Cancellable cancellable && cancellable.isCancelled();
        Runnable eventTask = () -> {
            relayToDiscordSrvIfConfigured(output.id(), extracted);
            if (cancelled || !isSnarkOutputEnabled(output.id())) {
                return;
            }

            Player player = resolvePlayer(plugin.getServer(), extracted.senderUuid());
            if (player == null || !player.isOnline()) {
                return;
            }
            if (!snarkService.shouldHandleChatAsync(extracted.plainMessage(), extracted.context())) {
                return;
            }

            Component snark = snarkService.buildAutomaticChatReply(player, extracted.plainMessage(), extracted.context());
            if (snark != null) {
                plugin.getServer().broadcast(snark);
            }
        };

        if (event.isAsynchronous()) {
            plugin.getServer().getScheduler().runTask(plugin, eventTask);
            return;
        }

        eventTask.run();
    }

    private ExtractedExternalChat extractChat(SnarkExternalOutput output, Event event) {
        UUID senderUuid = extractSenderUuid(event);
        String senderName = extractSenderName(event);
        String plainMessage = extractPlainMessage(event);
        if (plainMessage.isBlank()) {
            return null;
        }

        SnarkExternalChatContext context = new SnarkExternalChatContext(
                output.id(),
                output.displayName(),
                output.sourcePlugin(),
                output.kind(),
                extractString(event, "getClanName", "clanName"),
                extractString(event, "getClanTag", "clanTag"),
                extractBoolean(event, false, "isToggleRouted", "getToggleRouted", "toggleRouted"),
                extractRecipientCount(event)
        );

        return new ExtractedExternalChat(senderUuid, senderName, plainMessage, context);
    }

    private UUID extractSenderUuid(Event event) {
        UUID senderUuid = extractUuid(event, "getSenderUuid", "senderUuid");
        if (senderUuid != null) {
            return senderUuid;
        }

        Object sender = invokeAccessor(event, "getSender", "sender");
        return extractUuidFromSender(sender);
    }

    private UUID extractUuid(Object target, String... accessors) {
        Object value = invokeAccessor(target, accessors);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String stringValue) {
            try {
                return UUID.fromString(stringValue.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private UUID extractUuidFromSender(Object sender) {
        if (sender == null) {
            return null;
        }
        if (sender instanceof UUID uuid) {
            return uuid;
        }
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        if (sender instanceof OfflinePlayer offlinePlayer) {
            return offlinePlayer.getUniqueId();
        }

        return extractUuid(sender, "getUniqueId", "uniqueId");
    }

    private String extractSenderName(Event event) {
        String senderName = extractString(event, "getSenderName", "senderName", "getPlayerName", "playerName");
        if (!senderName.isBlank()) {
            return senderName;
        }

        Object sender = invokeAccessor(event, "getSender", "sender");
        if (sender instanceof Player player) {
            return player.getName();
        }
        if (sender instanceof OfflinePlayer offlinePlayer && offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        return extractString(sender, "getName", "name");
    }

    private String extractPlainMessage(Event event) {
        String plainMessage = extractString(event, "getPlainMessage", "plainMessage");
        if (!plainMessage.isBlank()) {
            return plainMessage;
        }

        Object componentValue = invokeAccessor(event, "getMessageComponent", "messageComponent");
        if (componentValue instanceof Component component) {
            return PLAIN_TEXT.serialize(component).trim();
        }

        return "";
    }

    private int extractRecipientCount(Event event) {
        Object recipients = invokeAccessor(event, "getRecipients", "recipients");
        if (recipients == null) {
            return 0;
        }
        if (recipients instanceof Collection<?> collection) {
            return collection.size();
        }
        if (recipients.getClass().isArray()) {
            return Array.getLength(recipients);
        }
        return 0;
    }

    private String extractString(Object target, String... accessors) {
        Object value = invokeAccessor(target, accessors);
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private boolean extractBoolean(Object target, boolean fallback, String... accessors) {
        Object value = invokeAccessor(target, accessors);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return fallback;
    }

    private Object invokeAccessor(Object target, String... accessors) {
        if (target == null) {
            return null;
        }

        for (String accessor : accessors) {
            try {
                Method method = target.getClass().getMethod(accessor);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (IllegalAccessException | InvocationTargetException exception) {
                logger.fine("Failed to invoke accessor '" + accessor + "' on "
                        + target.getClass().getName() + ": " + exception.getMessage());
                return null;
            }
        }

        return null;
    }

    private Player resolvePlayer(Server server, UUID senderUuid) {
        if (senderUuid == null) {
            return null;
        }
        return server.getPlayer(senderUuid);
    }

    private boolean isSnarkOutputEnabled(String outputId) {
        SnarkTriggersConfig.ExternalOutputToggle toggle = toggleLookup.apply(outputId);
        return toggle != null && toggle.enabled();
    }

    private void relayToDiscordSrvIfConfigured(String outputId, ExtractedExternalChat extracted) {
        SnarkTriggersConfig.ExternalOutputToggle toggle = toggleLookup.apply(outputId);
        if (toggle == null || !toggle.discordsrv().enabled()) {
            return;
        }

        Server server = plugin.getServer();
        String commandRoot = resolveDiscordSrvCommandRoot(server);
        if (commandRoot == null) {
            return;
        }

        String channel = normalizeDiscordSrvChannel(toggle.discordsrv().channel());
        if (channel.isBlank()) {
            return;
        }

        String message = buildDiscordSrvMessage(extracted);
        if (message.isBlank()) {
            return;
        }

        server.dispatchCommand(server.getConsoleSender(), commandRoot + " broadcast " + channel + " " + message);
    }

    private String resolveDiscordSrvCommandRoot(Server server) {
        Plugin discordSrv = server.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrv == null || !discordSrv.isEnabled()) {
            warnOnceDiscordSrvUnavailable("DiscordSRV is not installed or not enabled.");
            return null;
        }

        PluginCommand discordSrvCommand = server.getPluginCommand("discordsrv");
        if (discordSrvCommand != null) {
            return "discordsrv";
        }

        PluginCommand discordCommand = server.getPluginCommand("discord");
        if (discordCommand != null) {
            return "discord";
        }

        warnOnceDiscordSrvUnavailable("DiscordSRV command '/discordsrv' is unavailable.");
        return null;
    }

    private void warnOnceDiscordSrvUnavailable(String message) {
        if (discordSrvUnavailableWarned.compareAndSet(false, true)) {
            logger.warning(message);
        }
    }

    private String normalizeDiscordSrvChannel(String channel) {
        String normalized = normalizeSingleLine(channel);
        if (normalized.isBlank()) {
            return "";
        }

        return normalized.startsWith("#") ? normalized : "#" + normalized;
    }

    private String buildDiscordSrvMessage(ExtractedExternalChat extracted) {
        String scope = normalizeSingleLine(extracted.context().clanTag());
        if (scope.isBlank()) {
            scope = normalizeSingleLine(extracted.context().clanName());
        }
        if (scope.isBlank()) {
            scope = "Clan";
        }

        String senderName = normalizeSingleLine(extracted.senderName());
        if (senderName.isBlank()) {
            senderName = "Unknown";
        }

        String message = normalizeSingleLine(extracted.plainMessage());
        if (message.isBlank()) {
            return "";
        }

        return "[" + scope + "] " + senderName + ": " + message;
    }

    private String normalizeSingleLine(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ExtractedExternalChat(
            UUID senderUuid,
            String senderName,
            String plainMessage,
            SnarkExternalChatContext context
    ) {
    }
}
