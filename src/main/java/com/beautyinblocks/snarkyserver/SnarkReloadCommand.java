package com.beautyinblocks.snarkyserver;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class SnarkReloadCommand implements CommandExecutor {
    private final SnarkyServerPlugin plugin;

    public SnarkReloadCommand(SnarkyServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("snarkyserver.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        try {
            plugin.reloadPluginState();
            sender.sendMessage(ChatColor.GREEN + "SnarkyServer configuration reloaded.");
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Failed to reload SnarkyServer configuration. Check console for details.");
            plugin.getLogger().severe("Failed to reload SnarkyServer configuration: " + ex.getMessage());
            ex.printStackTrace();
        }

        return true;
    }
}
