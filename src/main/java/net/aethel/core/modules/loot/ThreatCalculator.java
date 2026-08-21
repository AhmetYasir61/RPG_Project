package net.aethel.core.modules.loot;

import net.aethel.core.api.Difficulty;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;

/**
 * Bir bolgedeki tehdit yogunlugunu olcer. Cok sayida zayif mob ile az sayida guclu
 * mob ayni puana cikabilir; ikisi de sandigin nadirligini yukseltir.
 */
final class ThreatCalculator {

    /** Tehdit taramasinin yaricapi; sandigin "cevresi" bu kadar sayilir. */
    private static final double SCAN_RADIUS = 24.0D;

    /**
     * Guc ussu 1.5: tek bir guclu mob, ayni toplam cana sahip surunun uzerinde
     * sayilir — cunku oyuncu icin gercek tehdit yogunlasmis gucten gelir.
     */
    private static final double POWER_EXPONENT = 1.5D;

    private ThreatCalculator() {}

    /** Ham tehdit puani: mob gucu × can orani toplaminin hacme bolunmus hali. */
    static double threat(Location center) {
        double total = 0;
        for (var entity : center.getWorld().getNearbyLivingEntities(center, SCAN_RADIUS)) {
            if (entity instanceof Player) continue;
            total += scoreOf(entity);
        }
        // Hacim normalizasyonu: buyuk bir alandaki ayni mob sayisi daha az tehdittir.
        double volume = (4.0 / 3.0) * Math.PI * Math.pow(SCAN_RADIUS, 3) / 1000.0;
        return total / Math.max(1.0, volume);
    }

    private static double scoreOf(LivingEntity entity) {
        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? 20.0 : maxHealthAttribute.getValue();
        double healthRatio = entity.getHealth() / Math.max(1.0, maxHealth);

        // Tier, mob'un vanilla temeline gore ne kadar guclendirildigi.
        double tier = Math.max(1.0, maxHealth / 20.0);
        return Math.pow(tier, POWER_EXPONENT) * healthRatio;
    }

    /**
     * Nadirlik carpani: 1 + log2(1 + tehdit) × zorluk. log2 kullanmamizin sebebi mob
     * yigarak odulu sinirsiz sismekten korumak — 10 kat mob yaklasik 3-4 kat odul verir,
     * boylece sandik bir farm makinesine donusmez.
     */
    static double rarityMultiplier(double threat, Difficulty difficulty) {
        double base = 1.0 + (Math.log(1.0 + threat) / Math.log(2)) ;
        return base * difficulty.rewardMultiplier();
    }
}
