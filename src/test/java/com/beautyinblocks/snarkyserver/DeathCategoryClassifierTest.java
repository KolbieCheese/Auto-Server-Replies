package com.beautyinblocks.snarkyserver;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathCategoryClassifierTest {
    private final DeathCategoryClassifier classifier = new DeathCategoryClassifier();

    @Test
    void mapsExpandedDamageCauses() {
        assertEquals(DeathCategory.PVP, classifier.classify(true, EntityDamageEvent.DamageCause.FALL));
        assertEquals(DeathCategory.EXPLOSION, classifier.classify(false, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION));
        assertEquals(DeathCategory.SUFFOCATION, classifier.classify(false, EntityDamageEvent.DamageCause.SUFFOCATION));
        assertEquals(DeathCategory.STARVATION, classifier.classify(false, EntityDamageEvent.DamageCause.STARVATION));
        assertEquals(DeathCategory.PROJECTILE, classifier.classify(false, EntityDamageEvent.DamageCause.PROJECTILE));
        assertEquals(DeathCategory.FREEZING, classifier.classify(false, EntityDamageEvent.DamageCause.FREEZE));
        assertEquals(DeathCategory.GENERIC, classifier.classify(false, null));
    }
}
