package net.aethel.core.modules.rpg;

import net.aethel.core.config.ConfigValue;

/** RPG ayarlari: seviye tavani, puan kazanci ve stat carpanlari. */
public final class RpgSettings {

    @ConfigValue("rpg.max-level")
    public int maxLevel = 100;

    /** Seviye basina verilen yetenek puani. */
    @ConfigValue("rpg.points-per-level")
    public int pointsPerLevel = 3;

    @ConfigValue("rpg.base-mana")
    public double baseMana = 100.0D;

    /** Her zeka puani bu kadar mana ekler. */
    @ConfigValue("rpg.mana-per-intelligence")
    public double manaPerIntelligence = 5.0D;

    /** Mana yenilenme hizi (saniyede). */
    @ConfigValue("rpg.mana-regen")
    public double manaRegen = 2.5D;

    /** Her guc puaninin yakin dovus hasarina katkisi. */
    @ConfigValue("rpg.damage-per-strength")
    public double damagePerStrength = 0.35D;

    /** Her dayaniklilik puaninin can havuzuna katkisi. */
    @ConfigValue("rpg.health-per-vitality")
    public double healthPerVitality = 0.5D;

    /** Mob oldurmede kazanilan temel XP. */
    @ConfigValue("rpg.xp-per-mob-level")
    public double xpPerMobLevel = 12.0D;
}
