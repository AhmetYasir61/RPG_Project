package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Bolge geometrisi. Kutu disinda cokgen, ucgen, serit ve kure destekler; her sekil
 * once kaba AABB elemesinden gecer, pahali test yalnizca adaylara uygulanir.
 */
public sealed interface RegionShape {

    /** Kaba on eleme kutusu; her sekil bunu ucuz hesaplar. */
    BoundingBox bounds();

    boolean contains(double x, double y, double z);

    default boolean contains(Location location) {
        return bounds().contains(location.toVector())
                && contains(location.getX(), location.getY(), location.getZ());
    }

    /** Klasik kutu; oda, bina, claim. */
    record Cuboid(Vector min, Vector max) implements RegionShape {
        @Override public BoundingBox bounds() { return BoundingBox.of(min, max); }
        @Override public boolean contains(double x, double y, double z) {
            return x >= min.getX() && x <= max.getX()
                    && y >= min.getY() && y <= max.getY()
                    && z >= min.getZ() && z <= max.getZ();
        }
    }

    /** Duzensiz alan; dungeon salonu. Ray casting ile test edilir. */
    record Polygon(List<Vector> vertices, double minY, double maxY) implements RegionShape {
        @Override
        public BoundingBox bounds() {
            double minX = vertices.stream().mapToDouble(Vector::getX).min().orElse(0);
            double maxX = vertices.stream().mapToDouble(Vector::getX).max().orElse(0);
            double minZ = vertices.stream().mapToDouble(Vector::getZ).min().orElse(0);
            double maxZ = vertices.stream().mapToDouble(Vector::getZ).max().orElse(0);
            return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        /**
         * Ray casting: noktadan saga bir isin gonderilir, kenar kesisim sayisi tek ise
         * nokta icerdedir. Cokgen dis bukey olmak zorunda degildir; bu yontem her
         * basit cokgende dogru calisir.
         */
        @Override
        public boolean contains(double x, double y, double z) {
            if (y < minY || y > maxY) return false;
            boolean inside = false;
            int count = vertices.size();
            for (int i = 0, j = count - 1; i < count; j = i++) {
                Vector a = vertices.get(i);
                Vector b = vertices.get(j);
                boolean crosses = (a.getZ() > z) != (b.getZ() > z);
                if (!crosses) continue;
                double intersectX = (b.getX() - a.getX()) * (z - a.getZ())
                        / (b.getZ() - a.getZ()) + a.getX();
                if (x < intersectX) inside = !inside;
            }
            return inside;
        }
    }

    /** Uc koseli alan; kama bicimli gecisler. */
    record Triangle(Vector a, Vector b, Vector c, double minY, double maxY) implements RegionShape {
        @Override
        public BoundingBox bounds() {
            return new BoundingBox(
                    Math.min(a.getX(), Math.min(b.getX(), c.getX())), minY,
                    Math.min(a.getZ(), Math.min(b.getZ(), c.getZ())),
                    Math.max(a.getX(), Math.max(b.getX(), c.getX())), maxY,
                    Math.max(a.getZ(), Math.max(b.getZ(), c.getZ())));
        }

        /** Baryantrik isaret testi; ucgen icin ray casting'den daha ucuzdur. */
        @Override
        public boolean contains(double x, double y, double z) {
            if (y < minY || y > maxY) return false;
            double d1 = sign(x, z, a, b);
            double d2 = sign(x, z, b, c);
            double d3 = sign(x, z, c, a);
            boolean hasNegative = d1 < 0 || d2 < 0 || d3 < 0;
            boolean hasPositive = d1 > 0 || d2 > 0 || d3 > 0;
            return !(hasNegative && hasPositive);
        }

        private double sign(double x, double z, Vector p, Vector q) {
            return (x - q.getX()) * (p.getZ() - q.getZ()) - (p.getX() - q.getX()) * (z - q.getZ());
        }
    }

    /** Serit/cubuk; koridor, kopru, yol. Iki nokta arasi mesafe + yaricap. */
    record Line(Vector start, Vector end, double radius) implements RegionShape {
        @Override
        public BoundingBox bounds() {
            return BoundingBox.of(start, end).expand(radius);
        }

        /** Nokta-dogru parcasi mesafesi; parca disina tasarsa uc noktalara kirpilir. */
        @Override
        public boolean contains(double x, double y, double z) {
            Vector point = new Vector(x, y, z);
            Vector direction = end.clone().subtract(start);
            double lengthSquared = direction.lengthSquared();
            if (lengthSquared == 0) return point.distance(start) <= radius;

            double t = point.clone().subtract(start).dot(direction) / lengthSquared;
            t = Math.max(0, Math.min(1, t));
            Vector closest = start.clone().add(direction.multiply(t));
            return point.distance(closest) <= radius;
        }
    }

    /** Kure; boss arena, aura alani. */
    record Sphere(Vector center, double radius) implements RegionShape {
        @Override
        public BoundingBox bounds() {
            return new BoundingBox(
                    center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                    center.getX() + radius, center.getY() + radius, center.getZ() + radius);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return center.distanceSquared(new Vector(x, y, z)) <= radius * radius;
        }
    }
}
