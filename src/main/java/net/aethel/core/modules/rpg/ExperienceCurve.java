package net.aethel.core.modules.rpg;

/**
 * Seviye/XP egrisi. Ussel degil polinom bir egri kullanilir: ussel egri ust
 * seviyelerde ilerlemeyi imkansiz hissettirir ve oyuncuyu erken kaybettirir.
 */
final class ExperienceCurve {

    /**
     * XP(n) = base * n^exponent. exponent 1.6 ile 2.2 arasi MMORPG'lerde standarttir;
     * 1.85 secildi: ilk seviyeler hizli gecer, orta seviyeler dengeli yavaslar ve
     * son seviyeler ussel bir duvara carpmadan uzar.
     */
    private static final double BASE = 120.0D;
    private static final double EXPONENT = 1.85D;

    private ExperienceCurve() {}

    /** Belirli bir seviyeye ulasmak icin gereken TOPLAM XP. */
    static double totalFor(int level) {
        if (level <= 1) return 0;
        return BASE * Math.pow(level - 1, EXPONENT);
    }

    /** Toplam XP'den mevcut seviyeyi cozer. */
    static int levelOf(double totalExperience, int maxLevel) {
        int level = 1;
        while (level < maxLevel && totalExperience >= totalFor(level + 1)) {
            level++;
        }
        return level;
    }

    /** Mevcut seviyedeki ilerleme orani (0..1); HUD cubugu icin. */
    static double progress(double totalExperience, int level, int maxLevel) {
        if (level >= maxLevel) return 1.0;
        double current = totalFor(level);
        double next = totalFor(level + 1);
        return Math.max(0, Math.min(1, (totalExperience - current) / (next - current)));
    }
}
