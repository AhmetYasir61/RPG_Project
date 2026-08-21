package net.aethel.core.modules.skill;

import net.aethel.core.api.ParticleEffect;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Sekil basina particle konumlarini uretir. Saf matematik: dunya durumuna dokunmaz,
 * bu yuzden test edilebilir ve istenirse ana thread disinda hesaplanabilir.
 */
final class ParticleGeometry {

    /**
     * Altin aci (yaklasik 2.39996 radyan). Kure uzerinde noktalari esit dagitmak icin
     * kullanilir; naif enlem/boylam dagilimi kutuplarda yiginlasma yapar ve efekt
     * "topak" gorunur.
     */
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private ParticleGeometry() {}

    /**
     * step: efektin kacinci adiminda oldugumuz (0..steps). Motion bu deger uzerinden
     * uygulanir; boylece ayni sekil kodu hem sabit hem genisleyen efekti besler.
     */
    static List<Location> build(ParticleEffect effect, Location origin, Location target, int step) {
        double progress = effect.steps() <= 1 ? 1.0 : step / (double) effect.steps();
        double radius = radiusFor(effect, progress);
        Location base = origin.clone().add(0, effect.offsetY(), 0);

        return switch (effect.shape()) {
            case POINT -> List.of(base);
            case CIRCLE -> circle(base, radius, effect.count(), 0);
            case RING -> circle(base, radius, effect.count(), rotationFor(effect, progress));
            case HELIX -> helix(base, radius, effect.count(), effect.height(), progress);
            case CONE -> cone(base, target, radius, effect.count(), effect.height());
            case BEAM -> beam(base, target, effect.count());
            case SPHERE -> sphere(base, radius, effect.count());
            case WAVE -> circle(base, radius, effect.count(), 0);
            case BURST -> burst(base, radius, effect.count());
        };
    }

    /** EXPAND hareketinde yaricap adim adim buyur; digerlerinde sabittir. */
    private static double radiusFor(ParticleEffect effect, double progress) {
        return effect.motion() == ParticleEffect.Motion.EXPAND
                ? effect.radius() * progress
                : effect.radius();
    }

    /** ORBIT hareketinde halka her adimda biraz doner. */
    private static double rotationFor(ParticleEffect effect, double progress) {
        return effect.motion() == ParticleEffect.Motion.ORBIT ? progress * Math.PI * 4 : 0;
    }

    private static List<Location> circle(Location center, double radius, int count, double rotation) {
        List<Location> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = rotation + (2 * Math.PI * i / count);
            points.add(center.clone().add(
                    Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        return points;
    }

    /** Sarmal: aci ile yukseklik birlikte artar. */
    private static List<Location> helix(Location center, double radius, int count,
                                        double height, double progress) {
        List<Location> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double ratio = i / (double) count;
            double angle = ratio * Math.PI * 6 + progress * Math.PI * 2;
            points.add(center.clone().add(
                    Math.cos(angle) * radius, ratio * height, Math.sin(angle) * radius));
        }
        return points;
    }

    /** Koni: hedefe bakan eksende, mesafeyle genisleyen halkalar. */
    private static List<Location> cone(Location origin, Location target, double radius,
                                       int count, double length) {
        List<Location> points = new ArrayList<>(count);
        Vector direction = target == null
                ? origin.getDirection()
                : target.toVector().subtract(origin.toVector()).normalize();

        int rings = Math.max(2, count / 8);
        for (int ring = 1; ring <= rings; ring++) {
            double distance = length * ring / rings;
            double ringRadius = radius * ring / rings;
            Location ringCenter = origin.clone().add(direction.clone().multiply(distance));
            points.addAll(perpendicularCircle(ringCenter, direction, ringRadius, 8));
        }
        return points;
    }

    /** Isin: iki nokta arasi esit araliklarla dizilir. */
    private static List<Location> beam(Location origin, Location target, int count) {
        if (target == null) return List.of(origin);
        List<Location> points = new ArrayList<>(count);
        Vector step = target.toVector().subtract(origin.toVector()).multiply(1.0 / count);
        for (int i = 0; i <= count; i++) {
            points.add(origin.clone().add(step.clone().multiply(i)));
        }
        return points;
    }

    /** Kure: altin aci ile esit dagilim (Fibonacci kuresi). */
    private static List<Location> sphere(Location center, double radius, int count) {
        List<Location> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double y = 1 - (i / (double) (count - 1)) * 2;
            double ringRadius = Math.sqrt(1 - y * y);
            double theta = GOLDEN_ANGLE * i;
            points.add(center.clone().add(
                    Math.cos(theta) * ringRadius * radius,
                    y * radius,
                    Math.sin(theta) * ringRadius * radius));
        }
        return points;
    }

    /** Saçilma: rastgele yonlerde, merkezden disari. */
    private static List<Location> burst(Location center, double radius, int count) {
        List<Location> points = new ArrayList<>(count);
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector direction = new Vector(
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1).normalize().multiply(radius * random.nextDouble());
            points.add(center.clone().add(direction));
        }
        return points;
    }

    /** Verilen yone dik duzlemde cember; koni halkalari icin. */
    private static List<Location> perpendicularCircle(Location center, Vector normal,
                                                      double radius, int count) {
        Vector axis = Math.abs(normal.getY()) < 0.99
                ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector u = normal.clone().crossProduct(axis).normalize();
        Vector v = normal.clone().crossProduct(u).normalize();

        List<Location> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            Vector offset = u.clone().multiply(Math.cos(angle) * radius)
                    .add(v.clone().multiply(Math.sin(angle) * radius));
            points.add(center.clone().add(offset));
        }
        return points;
    }
}
