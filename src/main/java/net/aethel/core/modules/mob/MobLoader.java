package net.aethel.core.modules.mob;

import net.aethel.core.api.MobDefinition;
import net.aethel.core.api.SkillDefinition;
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
 * contents/<ns>/mobs/*.yml dosyalarindan mob tanimlarini okur. Gorunum katmani
 * serbesttir; skills bolumu yalnizca skill kimlikleri ve tetikleyiciler icerir.
 */
final class MobLoader {

    private final Logger log;

    MobLoader(Logger log) {
        this.log = log;
    }

    Map<String, MobDefinition> loadAll(File contents) {
        Map<String, MobDefinition> result = new HashMap<>();
        File[] namespaces = contents.listFiles(File::isDirectory);
        if (namespaces == null) return result;

        for (File namespace : namespaces) {
            File folder = new File(namespace, "mobs");
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

    private MobDefinition parse(String id, ConfigurationSection section) {
        return new MobDefinition(id,
                section.getString("display", id),
                section.getString("type", "ZOMBIE"),
                section.getDouble("health", 20.0),
                section.getDouble("damage", 3.0),
                section.getDouble("armor", 0.0),
                section.getDouble("speed", 0.23),
                section.getDouble("follow-range", 24.0),
                section.getInt("tier", 1),
                readAppearance(section.getConfigurationSection("appearance")),
                readSkills(section.getMapList("skills")),
                section.getString("loot-table"),
                readSpawnRules(section.getConfigurationSection("spawn")),
                readOptions(section.getConfigurationSection("options")));
    }

    private MobDefinition.Appearance readAppearance(ConfigurationSection section) {
        if (section == null) {
            return new MobDefinition.Appearance(null, null, null, null, null, null, null,
                    true, "RED");
        }
        return new MobDefinition.Appearance(
                section.getString("model"),
                section.getString("helmet"),
                section.getString("chestplate"),
                section.getString("leggings"),
                section.getString("boots"),
                section.getString("main-hand"),
                section.getString("off-hand"),
                section.getBoolean("health-bar", true),
                section.getString("boss-bar-color", "RED"));
    }

    /** Tetikleyici adi bilinmiyorsa mob yine yuklenir, o yetenek atlanir. */
    private List<MobDefinition.SkillTrigger> readSkills(List<Map<?, ?>> raw) {
        List<MobDefinition.SkillTrigger> skills = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String skillId = string(entry, "skill", null);
            if (skillId == null) continue;
            SkillDefinition.Trigger trigger;
            try {
                trigger = SkillDefinition.Trigger.valueOf(
                        string(entry, "trigger", "MANUAL").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warning("Bilinmeyen mob tetikleyicisi: " + entry.get("trigger"));
                continue;
            }
            skills.add(new MobDefinition.SkillTrigger(skillId, trigger,
                    number(entry, "chance", 1.0),
                    (int) number(entry, "interval", 100),
                    number(entry, "health-threshold", 0.3)));
        }
        return skills;
    }

    private MobDefinition.SpawnRules readSpawnRules(ConfigurationSection section) {
        if (section == null) {
            return new MobDefinition.SpawnRules(List.of(), List.of(), -64, 320, 15, 0.0, 4);
        }
        return new MobDefinition.SpawnRules(
                section.getStringList("biomes"),
                section.getStringList("worlds"),
                section.getInt("min-y", -64),
                section.getInt("max-y", 320),
                section.getInt("max-light", 15),
                section.getDouble("chance", 0.0),
                section.getInt("max-nearby", 4));
    }

    private Map<String, String> readOptions(ConfigurationSection section) {
        Map<String, String> options = new HashMap<>();
        if (section == null) return options;
        section.getKeys(false).forEach(key -> options.put(key, section.getString(key)));
        return options;
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
