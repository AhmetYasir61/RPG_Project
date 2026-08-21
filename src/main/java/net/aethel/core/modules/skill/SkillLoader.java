package net.aethel.core.modules.skill;

import net.aethel.core.api.ParticleEffect;
import net.aethel.core.api.SkillDefinition;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * contents/<ns>/skills/*.yml dosyalarindan yetenek tanimlarini okur. Tanimda entity
 * alani YOKTUR: gorsel katman yalnizca particle efektlerinden olusur.
 */
final class SkillLoader {

    private final Logger log;

    SkillLoader(Logger log) {
        this.log = log;
    }

    Map<String, SkillDefinition> loadAll(File contents) {
        Map<String, SkillDefinition> result = new HashMap<>();
        File[] namespaces = contents.listFiles(File::isDirectory);
        if (namespaces == null) return result;

        for (File namespace : namespaces) {
            File folder = new File(namespace, "skills");
            File[] files = folder.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;

            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String id : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(id);
                    if (section == null) continue;
                    String fullId = namespace.getName() + ":" + id;
                    result.put(fullId, parse(fullId, section));
                }
            }
        }
        return result;
    }

    private SkillDefinition parse(String id, ConfigurationSection section) {
        Map<String, Double> values = new HashMap<>();
        ConfigurationSection valueSection = section.getConfigurationSection("values");
        if (valueSection != null) {
            valueSection.getKeys(false).forEach(key -> values.put(key, valueSection.getDouble(key)));
        }
        return new SkillDefinition(
                id,
                section.getString("display", id),
                section.getStringList("description"),
                enumValue(SkillDefinition.Trigger.class, section.getString("trigger", "MANUAL"),
                        SkillDefinition.Trigger.MANUAL),
                section.getDouble("cooldown", 5.0),
                section.getDouble("mana-cost", 0.0),
                section.getDouble("range", 8.0),
                enumValue(SkillDefinition.Targeting.class, section.getString("targeting", "AREA"),
                        SkillDefinition.Targeting.AREA),
                values,
                readEffects(section.getMapList("effects")),
                section.getStringList("sounds"));
    }

    /** Efekt listesi; her girdi bir sekil + hareket + particle katmanidir. */
    private List<ParticleEffect> readEffects(List<Map<?, ?>> raw) {
        List<ParticleEffect> effects = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            effects.add(new ParticleEffect(
                    enumValue(ParticleEffect.Shape.class, string(entry, "shape", "CIRCLE"),
                            ParticleEffect.Shape.CIRCLE),
                    enumValue(ParticleEffect.Motion.class, string(entry, "motion", "STATIC"),
                            ParticleEffect.Motion.STATIC),
                    particle(string(entry, "particle", "FLAME")),
                    color(string(entry, "color", "#ffffff")),
                    number(entry, "size", 1.0),
                    number(entry, "speed", 0.0),
                    (int) number(entry, "count", 24),
                    number(entry, "radius", 2.0),
                    number(entry, "height", 1.0),
                    (int) number(entry, "duration", 20),
                    (int) number(entry, "period", 2),
                    number(entry, "offset-y", 0.2),
                    List.of()));
        }
        return effects;
    }

    /** Bilinmeyen particle adi uretimi durdurmaz; uyari verilip FLAME'e dusulur. */
    private Particle particle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Bilinmeyen particle: " + name + " (FLAME kullanilacak)");
            return Particle.FLAME;
        }
    }

    private Color color(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Bilinmeyen deger: " + raw + " (" + type.getSimpleName() + ")");
            return fallback;
        }
    }

    private String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private double number(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
