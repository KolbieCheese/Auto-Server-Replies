package com.beautyinblocks.snarkyserver;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

public final class DeathCategoryClassifier {
    public DeathCategory classify(Player player) {
        if (player.getKiller() != null) {
            return DeathCategory.PVP;
        }

        EntityDamageEvent lastDamageCause = player.getLastDamageCause();
        if (lastDamageCause == null) {
            return DeathCategory.GENERIC;
        }

        return switch (lastDamageCause.getCause()) {
            case LAVA, HOT_FLOOR -> DeathCategory.LAVA;
            case FALL, FLY_INTO_WALL -> DeathCategory.FALL;
            case DROWNING -> DeathCategory.DROWNING;
            case FIRE, FIRE_TICK -> DeathCategory.FIRE;
            case VOID -> DeathCategory.VOID;
            default -> DeathCategory.GENERIC;
        };
    }
}
