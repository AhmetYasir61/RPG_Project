package net.aethel.core.api;

import java.util.List;
import java.util.Map;

/**
 * Bir yetenek tanimi. Gorsel katman YALNIZCA particle efektlerinden olusur; tanim
 * icinde entity/model alani bilerek yoktur, tip sistemi bunu engeller.
 */
public record SkillDefinition(String id,
                              String displayName,
                              List<String> description,
                              Trigger trigger,
                              double cooldownSeconds,
                              double manaCost,
                              double range,
                              Targeting targeting,
                              Map<String, Double> values,
                              List<ParticleEffect> effects,
                              List<String> sounds) {

    /** Yetenegin ne zaman calistigi. */
    public enum Trigger {
        /** Oyuncu/mob tarafindan bilincli kullanim. */
        MANUAL,
        ON_SPAWN,
        ON_DAMAGED,
        ON_DEAL_DAMAGE,
        ON_DEATH,
        ON_TIMER,
        ON_LOW_HEALTH
    }

    /** Kimin etkilenecegi. */
    public enum Targeting {
        SELF,
        SINGLE_TARGET,
        AREA,
        CONE,
        LINE,
        ALL_ALLIES
    }

    public double value(String key, double fallback) {
        return values.getOrDefault(key, fallback);
    }
}
