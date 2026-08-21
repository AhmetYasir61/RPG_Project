package net.aethel.core.api;

import java.util.List;
import java.util.Map;

/**
 * Bir custom mob tanimi. Moblar GERCEK entity'dir; gorunum katmani serbesttir
 * (vanilla tip, ekipmanli gorunum ya da model). Kisit yalnizca skill tarafindadir.
 */
public record MobDefinition(String id,
                            String displayName,
                            String baseType,
                            double health,
                            double damage,
                            double armor,
                            double movementSpeed,
                            double followRange,
                            int tier,
                            Appearance appearance,
                            List<SkillTrigger> skills,
                            String lootTable,
                            SpawnRules spawnRules,
                            Map<String, String> options) {

    /**
     * Gorunum katmani. model alani doldurulursa harici bir model motoru (ModelEngine
     * benzeri) devreye girer; bos ise ekipman ve isim ile vanilla gorunum kullanilir.
     */
    public record Appearance(String model,
                             String helmet,
                             String chestplate,
                             String leggings,
                             String boots,
                             String mainHand,
                             String offHand,
                             boolean showHealthBar,
                             String bossBarColor) {}

    /** Bir yetenegin hangi tetikleyiciyle ve hangi sartla calisacagi. */
    public record SkillTrigger(String skillId,
                               SkillDefinition.Trigger trigger,
                               double chance,
                               int intervalTicks,
                               double healthThreshold) {}

    /** Dogal spawn kurallari. */
    public record SpawnRules(List<String> biomes,
                             List<String> worlds,
                             int minY,
                             int maxY,
                             int maxLightLevel,
                             double chance,
                             int maxNearby) {}

    /** Tehdit hesabinda kullanilan guc katsayisi. */
    public double powerScore() {
        return Math.pow(Math.max(1, tier), 1.5) * (health / 20.0);
    }
}
