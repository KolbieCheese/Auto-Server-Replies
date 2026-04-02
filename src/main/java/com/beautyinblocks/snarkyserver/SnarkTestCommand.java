package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class SnarkTestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of("list", "status", "death", "chat", "random", "cooldowns");

    private final Server server;
    private final Supplier<SnarkService> snarkServiceSupplier;
    private final Supplier<CooldownManager> cooldownManagerSupplier;
    private final Supplier<SnarkExternalOutputRegistry> externalOutputRegistrySupplier;

    public SnarkTestCommand(
            Server server,
            Supplier<SnarkService> snarkServiceSupplier,
            Supplier<CooldownManager> cooldownManagerSupplier,
            Supplier<SnarkExternalOutputRegistry> externalOutputRegistrySupplier
    ) {
        this.server = server;
        this.snarkServiceSupplier = snarkServiceSupplier;
        this.cooldownManagerSupplier = cooldownManagerSupplier;
        this.externalOutputRegistrySupplier = externalOutputRegistrySupplier;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("snarkyserver.test")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "list" -> handleList(sender);
            case "status" -> handleStatus(sender, args);
            case "death" -> handleDeath(sender, label, args);
            case "chat" -> handleChat(sender, label, args);
            case "random" -> handleRandom(sender, label, args);
            case "cooldowns" -> handleCooldowns(sender, args);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("snarkyserver.test")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterByPrefix(ROOT_SUBCOMMANDS, args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (subcommand) {
                case "death" -> filterByPrefix(categoryKeys(DeathCategory.values()), args[1]);
                case "chat" -> filterByPrefix(categoryKeys(ChatCategory.values()), args[1]);
                case "random", "cooldowns", "status" -> filterByPrefix(onlinePlayerNames(), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3 && ("death".equals(subcommand) || "chat".equals(subcommand))) {
            return filterByPrefix(onlinePlayerNames(), args[2]);
        }

        if (args.length == 4 && "death".equals(subcommand) && "pvp".equalsIgnoreCase(args[1])) {
            return filterByPrefix(onlinePlayerNames(), args[3]);
        }

        return List.of();
    }

    private boolean handleList(CommandSender sender) {
        SnarkService snarkService = snarkServiceSupplier.get();
        sender.sendMessage(ChatColor.YELLOW + "Death categories: " + String.join(", ", categoryKeys(DeathCategory.values())));
        sender.sendMessage(ChatColor.YELLOW + "Chat categories: " + String.join(", ", categoryKeys(ChatCategory.values())));
        sender.sendMessage(ChatColor.YELLOW + "Outputs:");
        sender.sendMessage(ChatColor.YELLOW + " - death-snark: " + stateLabel(snarkService.config().triggersConfig().deathSnark().enabled()));
        sender.sendMessage(ChatColor.YELLOW + " - chat-snark: " + stateLabel(snarkService.config().triggersConfig().chatSnark().enabled()));

        SnarkExternalOutputRegistry externalOutputRegistry = externalOutputRegistrySupplier.get();
        List<SnarkExternalOutputRegistry.OutputStatus> externalOutputs = externalOutputRegistry == null
                ? List.of()
                : externalOutputRegistry.listOutputs();
        if (externalOutputs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + " - external: none discovered");
            return true;
        }

        for (SnarkExternalOutputRegistry.OutputStatus output : externalOutputs) {
            sender.sendMessage(ChatColor.YELLOW + " - " + output.id() + " (" + output.displayName()
                    + " from " + output.sourcePlugin() + "): " + stateLabel(output.enabled()));
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        SnarkService snarkService = snarkServiceSupplier.get();
        CooldownManager cooldownManager = cooldownManagerSupplier.get();
        SnarkyConfig config = snarkService.config();
        SnarkTriggersConfig triggersConfig = config.triggersConfig();
        Instant now = Instant.now();

        sender.sendMessage(ChatColor.YELLOW + "Snarky Server status:");
        sender.sendMessage(ChatColor.YELLOW + " - global: " + stateLabel(triggersConfig.enabled()));
        sender.sendMessage(ChatColor.YELLOW + " - death-snark: " + stateLabel(triggersConfig.deathSnark().enabled())
                + " | generic chance " + formatChance(config.chancesConfig().deathChanceFor(DeathCategory.GENERIC))
                + " | generic pool " + config.messagesConfig().deathMessagesFor(DeathCategory.GENERIC).size());
        sender.sendMessage(ChatColor.YELLOW + " - chat-snark: " + stateLabel(triggersConfig.chatSnark().enabled())
                + " | generic chance " + formatChance(config.chancesConfig().chatChanceFor(ChatCategory.GENERIC))
                + " | generic pool " + config.messagesConfig().chatMessagesFor(ChatCategory.GENERIC).size()
                + " | min length " + triggersConfig.chatSnark().minMessageLength()
                + " | ignore commands " + yesNo(triggersConfig.chatSnark().ignoreCommands()));
        sender.sendMessage(ChatColor.YELLOW + " - cooldowns: global "
                + triggersConfig.cooldowns().globalSeconds() + "s, per-player "
                + triggersConfig.cooldowns().perPlayerSeconds() + "s, current global "
                + formatDuration(cooldownManager.remainingGlobal(now)));
        sender.sendMessage(ChatColor.YELLOW + " - filters: bypass permission "
                + valueOrNone(triggersConfig.filters().bypassPermission())
                + ", ignored worlds " + formatList(triggersConfig.filters().ignoredWorlds())
                + ", ignored prefixes " + formatList(triggersConfig.filters().ignoredPrefixes()));

        SnarkExternalOutputRegistry externalOutputRegistry = externalOutputRegistrySupplier.get();
        List<SnarkExternalOutputRegistry.OutputStatus> externalOutputs = externalOutputRegistry == null
                ? List.of()
                : externalOutputRegistry.listOutputs();
        long enabledOutputs = externalOutputs.stream().filter(SnarkExternalOutputRegistry.OutputStatus::enabled).count();
        long activeOutputs = externalOutputs.stream().filter(SnarkExternalOutputRegistry.OutputStatus::active).count();
        sender.sendMessage(ChatColor.YELLOW + " - external outputs: "
                + externalOutputs.size() + " discovered, "
                + enabledOutputs + " enabled, "
                + activeOutputs + " active listeners");

        Player inspectedPlayer = resolveInspectedPlayer(sender, args);
        if (args.length >= 2 && inspectedPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online, so player-specific status could not be checked.");
        }
        if (inspectedPlayer != null) {
            boolean bypassed = !triggersConfig.filters().bypassPermission().isBlank()
                    && inspectedPlayer.hasPermission(triggersConfig.filters().bypassPermission());
            boolean ignoredWorld = triggersConfig.filters().ignoredWorlds().stream()
                    .anyMatch(world -> world.equalsIgnoreCase(inspectedPlayer.getWorld().getName()));
            sender.sendMessage(ChatColor.YELLOW + " - player check (" + inspectedPlayer.getName() + "): world "
                    + inspectedPlayer.getWorld().getName()
                    + ", bypass permission " + yesNo(bypassed)
                    + ", ignored world " + yesNo(ignoredWorld)
                    + ", personal cooldown "
                    + formatDuration(cooldownManager.remainingForPlayer(inspectedPlayer.getUniqueId(), now)));
        }

        if (config.chancesConfig().deathChanceFor(DeathCategory.GENERIC) <= 0.05D
                || config.chancesConfig().chatChanceFor(ChatCategory.GENERIC) <= 0.02D) {
            sender.sendMessage(ChatColor.GRAY + "Low default chances are configured. Quiet stretches are expected; use /snarktest chat|death to force a preview.");
        }
        return true;
    }

    private boolean handleDeath(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " death <category> <player> [killer]");
            return true;
        }

        DeathCategory category = DeathCategory.fromKey(args[1]).orElse(null);
        if (category == null) {
            sender.sendMessage(ChatColor.RED + "Unknown death category '" + args[1] + "'. Try /" + label + " list.");
            return true;
        }

        String playerName = args[2];
        String killerName = args.length >= 4 ? args[3] : "";
        return broadcastPreview(sender, snarkServiceSupplier.get().buildTestDeathReply(playerName, category, killerName));
    }

    private boolean handleChat(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " chat <category> <player> [message...]");
            return true;
        }

        ChatCategory category = ChatCategory.fromKey(args[1]).orElse(null);
        if (category == null) {
            sender.sendMessage(ChatColor.RED + "Unknown chat category '" + args[1] + "'. Try /" + label + " list.");
            return true;
        }

        String playerName = args[2];
        String message = args.length >= 4
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : "";
        return broadcastPreview(sender, snarkServiceSupplier.get().buildTestChatReply(playerName, category, message));
    }

    private boolean handleRandom(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " random <player>");
            return true;
        }

        return broadcastPreview(sender, snarkServiceSupplier.get().buildRandomTestReply(args[1]));
    }

    private boolean handleCooldowns(CommandSender sender, String[] args) {
        CooldownManager cooldownManager = cooldownManagerSupplier.get();
        Instant now = Instant.now();
        sender.sendMessage(ChatColor.YELLOW + "Global cooldown: " + formatDuration(cooldownManager.remainingGlobal(now)));

        if (args.length >= 2) {
            Player player = server.getPlayerExact(args[1]);
            if (player == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online, so the personal cooldown could not be checked.");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + player.getName() + " cooldown: "
                    + formatDuration(cooldownManager.remainingForPlayer(player.getUniqueId(), now)));
        }

        return true;
    }

    private boolean broadcastPreview(CommandSender sender, Component preview) {
        if (preview == null) {
            sender.sendMessage(ChatColor.RED + "No test message could be generated from the current configuration.");
            return true;
        }

        server.broadcast(preview);
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status [player]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " death <category> <player> [killer]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " chat <category> <player> [message...]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " random <player>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " cooldowns [player]");
    }

    private List<String> onlinePlayerNames() {
        return server.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> categoryKeys(Enum<?>[] categories) {
        return Arrays.stream(categories)
                .map(category -> switch (category) {
                    case DeathCategory deathCategory -> deathCategory.configKey();
                    case ChatCategory chatCategory -> chatCategory.configKey();
                    default -> "";
                })
                .filter(key -> !key.isBlank())
                .toList();
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(Objects::nonNull)
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .collect(Collectors.toList());
    }

    private String formatDuration(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "ready now";
        }

        long seconds = Math.max(1L, duration.toSeconds());
        return seconds + "s remaining";
    }

    private String formatChance(double chance) {
        return String.format(Locale.US, "%.2f%%", Math.max(0.0D, Math.min(1.0D, chance)) * 100.0D);
    }

    private String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values);
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private Player resolveInspectedPlayer(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            return server.getPlayerExact(args[1]);
        }
        if (sender instanceof Player player) {
            return player;
        }
        return null;
    }

    private String stateLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
