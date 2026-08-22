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
            case SLASH -> slash(base, target, radius, effect.count(), progress, 0);
            case SLASH_STORM -> slashStorm(base, target, radius, effect.count(), progress);
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

    /**
     * Kesigin bakacagi yon. Hedef varsa ona dogru; yoksa origin'in kendi bakis
     * yonu. Hedefsiz bir kesigin rastgele bir yone savrulmasi, yetenegi
     * "calismiyor" gosterirdi.
     */
    private static Vector direction(Location origin, Location target) {
        Vector forward = target == null
                ? origin.getDirection()
                : target.toVector().subtract(origin.toVector());
        forward.setY(forward.getY() * 0.35D);          // kesik daha yatay okunur
        if (forward.lengthSquared() < 1.0E-6) forward = new Vector(1, 0, 0);
        return forward.normalize();
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
    /**
     * Yay bicimli kesik. Kilic sallanisinin birakigi iz gibi: bakis yonune DIK
     * bir duzlemde, merkezden disa acilan bir yay.
     *
     * Yay ilerledikce disari acilir (progress ile yaricap buyur) ve uclarina
     * dogru seyrelir: yogunluk ortada toplanir, boylece hareket eden bir kesik
     * izlenimi olusur, duran bir yarim daire degil.
     */
    private static List<Location> slash(Location origin, Location target, double radius,
                                        int count, double progress, double offset) {
        Vector forward = direction(origin, target);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();

        // Yay 150 derece; tam yarim daire fazla "duran" gorunuyor.
        double span = Math.toRadians(150);
        double spread = radius * (0.55D + 0.45D * progress);

        List<Location> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5D : i / (double) (count - 1);
            double angle = (t - 0.5D) * span + offset;

            // Uclarda yaricapi kisaltmak yayin sivri bitmesini saglar.
            double taper = 0.65D + 0.35D * Math.cos((t - 0.5D) * Math.PI);
            double r = spread * taper;

            Vector point = forward.clone().multiply(r * Math.cos(angle))
                    .add(right.clone().multiply(r * Math.sin(angle)))
                    .add(up.clone().multiply(Math.sin(angle * 2) * radius * 0.12D));
            points.add(origin.clone().add(point));
        }
        return points;
    }

    /** Ic ice uc yay; her biri biraz kaydirilmis ve farkli yaricapta. */
    private static List<Location> slashStorm(Location origin, Location target, double radius,
                                             int count, double progress) {
        List<Location> points = new ArrayList<>(count);
        int perArc = Math.max(3, count / 3);
        for (int arc = 0; arc < 3; arc++) {
            double offset = Math.toRadians(arc * 26 - 26);
            double scale = 0.7D + arc * 0.2D;
            points.addAll(slash(origin, target, radius * scale, perArc, progress, offset));
        }
        return points;
    }

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
