package net.aethel.core.modules.region;

import net.aethel.core.api.Difficulty;
import net.aethel.core.api.RegionService.Region;
import net.aethel.core.api.RegionShape;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bolge ve zorluk tanimlarinin YAML kalicilastirmasi. Panelden yapilan her degisiklik
 * hemen diske yazilir; sunucu cokse bile tanim kaybolmaz.
 */
final class RegionStorage {

    private final ConfigFile regionsFile;
    private final ConfigFile difficultiesFile;
    private final CoreContext ctx;

    RegionStorage(CoreContext ctx) {
        this.ctx = ctx;
        this.regionsFile = ctx.config().open("regions.yml", 1, null, ConfigMigration.NONE);
        this.difficultiesFile = ctx.config().open("difficulties.yml", 1, null, ConfigMigration.NONE);
    }

    List<Region> loadRegions() {
        List<Region> result = new ArrayList<>();
        ConfigurationSection root = regionsFile.yaml().getConfigurationSection("regions");
        if (root == null) return result;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            RegionShape shape = readShape(section.getConfigurationSection("shape"));
            if (shape == null) {
                ctx.logger().warning("Bolge sekli okunamadi: " + id);
                continue;
            }
            result.add(new Region(id,
                    section.getString("world", "world"),
                    shape,
                    section.getString("difficulty", "normal"),
                    section.getInt("priority", 0),
                    section.getStringList("flags"),
                    section.getStringList("owners")));
        }
        return result;
    }

    /** Sekil tipi disaridan gelir; bilinmeyen tip sessizce kabul edilmez. */
    private RegionShape readShape(ConfigurationSection section) {
        if (section == null) return null;
        String type = section.getString("type", "CUBOID").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "CUBOID" -> new RegionShape.Cuboid(
                    vector(section, "min"), vector(section, "max"));
            case "SPHERE" -> new RegionShape.Sphere(
                    vector(section, "center"), section.getDouble("radius", 8));
            case "LINE" -> new RegionShape.Line(
                    vector(section, "start"), vector(section, "end"),
                    section.getDouble("radius", 3));
            case "TRIANGLE" -> new RegionShape.Triangle(
                    vector(section, "a"), vector(section, "b"), vector(section, "c"),
                    section.getDouble("min-y", -64), section.getDouble("max-y", 320));
            case "POLYGON" -> new RegionShape.Polygon(
                    readVertices(section), section.getDouble("min-y", -64),
                    section.getDouble("max-y", 320));
            default -> null;
        };
    }

    private List<Vector> readVertices(ConfigurationSection section) {
        List<Vector> vertices = new ArrayList<>();
        for (String raw : section.getStringList("vertices")) {
            String[] parts = raw.split(",");
            if (parts.length < 2) continue;
            vertices.add(new Vector(Double.parseDouble(parts[0]), 0,
                    Double.parseDouble(parts[1])));
        }
        return vertices;
    }

    private Vector vector(ConfigurationSection section, String key) {
        String raw = section.getString(key, "0,0,0");
        String[] parts = raw.split(",");
        return new Vector(Double.parseDouble(parts[0]),
                parts.length > 1 ? Double.parseDouble(parts[1]) : 0,
                parts.length > 2 ? Double.parseDouble(parts[2]) : 0);
    }

    void saveRegion(Region region) {
        String base = "regions." + region.id();
        var yaml = regionsFile.yaml();
        yaml.set(base + ".world", region.world());
        yaml.set(base + ".difficulty", region.difficultyId());
        yaml.set(base + ".priority", region.priority());
        yaml.set(base + ".flags", region.flags());
        yaml.set(base + ".owners", region.owners());
        writeShape(base + ".shape", region.shape());
        regionsFile.save();
    }

    private void writeShape(String base, RegionShape shape) {
        var yaml = regionsFile.yaml();
        switch (shape) {
            case RegionShape.Cuboid cuboid -> {
                yaml.set(base + ".type", "CUBOID");
                yaml.set(base + ".min", format(cuboid.min()));
                yaml.set(base + ".max", format(cuboid.max()));
            }
            case RegionShape.Sphere sphere -> {
                yaml.set(base + ".type", "SPHERE");
                yaml.set(base + ".center", format(sphere.center()));
                yaml.set(base + ".radius", sphere.radius());
            }
            case RegionShape.Line line -> {
                yaml.set(base + ".type", "LINE");
                yaml.set(base + ".start", format(line.start()));
                yaml.set(base + ".end", format(line.end()));
                yaml.set(base + ".radius", line.radius());
            }
            case RegionShape.Triangle triangle -> {
                yaml.set(base + ".type", "TRIANGLE");
                yaml.set(base + ".a", format(triangle.a()));
                yaml.set(base + ".b", format(triangle.b()));
                yaml.set(base + ".c", format(triangle.c()));
                yaml.set(base + ".min-y", triangle.minY());
                yaml.set(base + ".max-y", triangle.maxY());
            }
            case RegionShape.Polygon polygon -> {
                yaml.set(base + ".type", "POLYGON");
                List<String> vertices = new ArrayList<>();
                polygon.vertices().forEach(vertex ->
                        vertices.add(vertex.getX() + "," + vertex.getZ()));
                yaml.set(base + ".vertices", vertices);
                yaml.set(base + ".min-y", polygon.minY());
                yaml.set(base + ".max-y", polygon.maxY());
            }
        }
    }

    private String format(Vector vector) {
        return vector.getX() + "," + vector.getY() + "," + vector.getZ();
    }

    void deleteRegion(String id) {
        regionsFile.yaml().set("regions." + id, null);
        regionsFile.save();
    }

    List<Difficulty> loadDifficulties() {
        List<Difficulty> result = new ArrayList<>();
        ConfigurationSection root = difficultiesFile.yaml().getConfigurationSection("difficulties");
        if (root == null) return result;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            result.add(new Difficulty(id,
                    section.getString("display", id),
                    section.getInt("percent", 100),
                    section.getString("icon", ""),
                    section.getBoolean("hardcore", false)));
        }
        return result;
    }

    void saveDifficulty(Difficulty difficulty) {
        String base = "difficulties." + difficulty.id();
        var yaml = difficultiesFile.yaml();
        yaml.set(base + ".display", difficulty.displayName());
        yaml.set(base + ".percent", difficulty.percent());
        yaml.set(base + ".icon", difficulty.icon());
        yaml.set(base + ".hardcore", difficulty.hardcore());
        difficultiesFile.save();
    }
}
