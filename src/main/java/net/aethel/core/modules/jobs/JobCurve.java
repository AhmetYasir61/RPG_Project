package net.aethel.core.modules.jobs;

/**
 * Meslek seviye egrisi. Karakter seviyesinden daha yumusak bir egri kullanilir:
 * meslek ilerlemesi gunluk oynayisa bagli oldugu icin duvara carpmamalidir.
 */
final class JobCurve {

    /** XP(n) = 80 * n^1.6 — karakter egrisinden (1.85) daha yumusak. */
    private static final double BASE = 80.0D;
    private static final double EXPONENT = 1.6D;

    private JobCurve() {}

    static double totalFor(int level) {
        return level <= 1 ? 0 : BASE * Math.pow(level - 1, EXPONENT);
    }

    static int levelOf(double experience, int maxLevel) {
        int level = 1;
        while (level < maxLevel && experience >= totalFor(level + 1)) {
            level++;
        }
        return level;
    }

    static double progress(double experience, int level, int maxLevel) {
        if (level >= maxLevel) return 1.0;
        double current = totalFor(level);
        double next = totalFor(level + 1);
        return Math.max(0, Math.min(1, (experience - current) / (next - current)));
    }
}
