package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class SnarkExternalChatEventBridge {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final SnarkService snarkService;
    private final Predicate<String> outputEnabledChecker;
    private final Logger logger;

    public SnarkExternalChatEventBridge(
            JavaPlugin plugin,
            SnarkService snarkService,
            Predicate<String> outputEnabledChecker,
            Logger logger
    ) {
        this.plugin = plugin;
        this.snarkService = snarkService;
        this.outputEnabledChecker = outputEnabledChecker;
        this.logger = logger;
    }

    public EventExecutor createExecutor(SnarkExternalOutput output) {
        return (listener, event) -> handleEvent(output, event);
    }

    void handleEvent(SnarkExternalOutput output, Event event) {
        if (!outputEnabledChecker.test(output.id())) {
            return;
        }
        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }

        ExtractedExternalChat extracted = extractChat(output, event);
        if (extracted == null) {
            return;
        }

        Runnable replyTask = () -> {
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
            plugin.getServer().getScheduler().runTask(plugin, replyTask);
            return;
        }

        replyTask.run();
    }

    private ExtractedExternalChat extractChat(SnarkExternalOutput output, Event event) {
        UUID senderUuid = extractSenderUuid(event);
        String plainMessage = extractPlainMessage(event);
        if (senderUuid == null || plainMessage.isBlank()) {
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

        return new ExtractedExternalChat(senderUuid, plainMessage, context);
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

    private record ExtractedExternalChat(
            UUID senderUuid,
            String plainMessage,
            SnarkExternalChatContext context
    ) {
    }
}
