package net.aethel.core.modules.content;

import net.aethel.core.api.CustomItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * contents/<ns>/items/*.yml dosyalarini okuyup CustomItem tanimlarina cevirir.
 * Ayni id iki kez tanimlanirsa ikincisi reddedilir ve uyari verilir.
 */
final class ItemLoader {

    private final Logger log;

    ItemLoader(Logger log) {
        this.log = log;
    }

    /** contents klasorundeki tum namespace'leri tarar. */
    Map<String, CustomItem> loadAll(File contentsFolder) {
        Map<String, CustomItem> result = new HashMap<>();
        File[] namespaces = contentsFolder.listFiles(File::isDirectory);
        if (namespaces == null) return result;

        for (File namespace : namespaces) {
            File items = new File(namespace, "items");
            File[] files = items.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                loadFile(namespace.getName(), file, result);
            }
        }
        return result;
    }

    private void loadFile(String namespace, File file, Map<String, CustomItem> target) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            String fullId = namespace + ":" + id;
            if (target.containsKey(fullId)) {
                log.warning("Ayni item id iki kez tanimli, atlaniyor: " + fullId
                        + " (" + file.getName() + ")");
                continue;
            }
            target.put(fullId, parse(namespace, id, section));
        }
    }

    private CustomItem parse(String namespace, String id, ConfigurationSection section) {
        return new CustomItem(
                id,
                namespace,
                section.getString("display", id),
                section.getStringList("lore"),
                section.getString("material", "PAPER"),
                section.getString("texture", "item/" + id + ".png"),
                section.getString("rarity", "COMMON"),
                section.getInt("custom-model-data", 0),
                readDoubles(section.getConfigurationSection("attributes")),
                readInts(section.getConfigurationSection("enchantments")),
                section.contains("durability") ? section.getInt("durability") : null,
                section.getBoolean("unbreakable", false),
                section.getBoolean("glow", false),
                readFood(section.getConfigurationSection("food")),
                readEquip(section.getConfigurationSection("equippable")),
                section.getStringList("tags"));
    }

    private Map<String, Double> readDoubles(ConfigurationSection section) {
        Map<String, Double> values = new HashMap<>();
        if (section == null) return values;
        section.getKeys(false).forEach(key -> values.put(key, section.getDouble(key)));
        return values;
    }

    private Map<String, Integer> readInts(ConfigurationSection section) {
        Map<String, Integer> values = new HashMap<>();
        if (section == null) return values;
        section.getKeys(false).forEach(key -> values.put(key, section.getInt(key)));
        return values;
    }

    private CustomItem.FoodProperties readFood(ConfigurationSection section) {
        if (section == null) return null;
        return new CustomItem.FoodProperties(
                section.getInt("nutrition", 1),
                (float) section.getDouble("saturation", 0.5D),
                section.getBoolean("always-edible", false),
                (float) section.getDouble("eat-seconds", 1.6D));
    }

    private CustomItem.EquipProperties readEquip(ConfigurationSection section) {
        if (section == null) return null;
        return new CustomItem.EquipProperties(
                section.getString("slot", "head"),
                section.getDouble("armor", 0.0D),
                section.getDouble("toughness", 0.0D),
                section.getString("equip-sound", "item.armor.equip_leather"));
    }

    /** Uretim oncesi dogrulama: texture dosyasi gercekten var mi. */
    List<String> validate(Map<String, CustomItem> items, File contentsFolder) {
        List<String> problems = new ArrayList<>();
        items.values().forEach(item -> {
            File texture = new File(contentsFolder,
                    item.namespace() + "/textures/" + item.texture());
            if (!texture.exists()) {
                problems.add("Texture bulunamadi: " + item.fullId() + " -> " + item.texture());
            }
        });
        return problems;
    }
}
