package com.beautyinblocks.snarkyserver;

import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

public final class PlayerVisibilityChecker {
    private final Server server;

    public PlayerVisibilityChecker(Server server) {
        this.server = server;
    }

    public boolean isHidden(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (player.hasMetadata("vanished") && player.getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean)) {
            return true;
        }

        return server.getOnlinePlayers().stream()
                .filter(viewer -> !viewer.getUniqueId().equals(player.getUniqueId()))
                .anyMatch(viewer -> !viewer.canSee(player));
    }
}
