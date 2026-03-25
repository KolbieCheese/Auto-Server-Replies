package com.beautyinblocks.snarkyserver;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class SnarkyListener implements Listener {
    private final SnarkService snarkService;

    public SnarkyListener(SnarkService snarkService) {
        this.snarkService = snarkService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String causeKey = resolveCause(player);
        String killerName = player.getKiller() == null ? null : player.getKiller().getName();

        String snark = snarkService.buildDeathReply(
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getName(),
                causeKey,
                killerName
        );

        if (snark != null) {
            event.deathMessage(snark);
        }
    }

    private String resolveCause(Player player) {
        if (player.getKiller() != null) {
            return "pvp";
        }

        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) {
            return "generic";
        }

        return switch (lastDamage.getCause()) {
            case LAVA, HOT_FLOOR, FIRE, FIRE_TICK -> "lava";
            case FALL, FLY_INTO_WALL -> "fall";
            default -> "generic";
        };
    }
}
