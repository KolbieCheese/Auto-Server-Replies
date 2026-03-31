package com.beautyinblocks.snarkyserver;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class SnarkReloadCommand implements CommandExecutor {
    private final Runnable reloadAction;
    private final Logger logger;

    public SnarkReloadCommand(Runnable reloadAction, Logger logger) {
        this.reloadAction = reloadAction;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("snarkyserver.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        try {
            reloadAction.run();
            sender.sendMessage(ChatColor.GREEN + "Snarky Server configuration reloaded.");
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Failed to reload Snarky Server configuration. Check console for details.");
            logger.log(Level.SEVERE, "Failed to reload Snarky Server configuration.", ex);
        }

        return true;
    }
}
