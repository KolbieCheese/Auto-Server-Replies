package com.beautyinblocks.snarkyserver;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

public final class DeathCategoryClassifier {
    public DeathCategory classify(Player player) {
        return classify(
                player.getKiller() != null,
                player.getLastDamageCause() == null ? null : player.getLastDamageCause().getCause()
        );
    }

    public DeathCategory classify(boolean killedByPlayer, EntityDamageEvent.DamageCause cause) {
        if (killedByPlayer) {
            return DeathCategory.PVP;
        }

        if (cause == null) {
            return DeathCategory.GENERIC;
        }

        return switch (cause) {
            case LAVA, HOT_FLOOR -> DeathCategory.LAVA;
            case FALL, FLY_INTO_WALL -> DeathCategory.FALL;
            case DROWNING -> DeathCategory.DROWNING;
            case FIRE, FIRE_TICK -> DeathCategory.FIRE;
            case VOID -> DeathCategory.VOID;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> DeathCategory.EXPLOSION;
            case SUFFOCATION, CRAMMING -> DeathCategory.SUFFOCATION;
            case STARVATION -> DeathCategory.STARVATION;
            case PROJECTILE -> DeathCategory.PROJECTILE;
            case FREEZE -> DeathCategory.FREEZING;
            default -> DeathCategory.GENERIC;
        };
    }
}
