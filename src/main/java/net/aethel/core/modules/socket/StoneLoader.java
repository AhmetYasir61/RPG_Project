package net.aethel.core.modules.socket;

import net.aethel.core.api.SocketStone;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * contents/<ns>/stones/*.yml dosyalarindan soket tasi tanimlarini okur.
 * Item yukleyicisiyle ayni kaliba uyar: ayni id iki kez tanimlanirsa ikincisi
 * reddedilir ve uyari verilir.
 */
final class StoneLoader {

    private final Logger log;

    StoneLoader(Logger log) {
        this.log = log;
    }

    Map<String, SocketStone> loadAll(File contentsFolder) {
        Map<String, SocketStone> result = new HashMap<>();
        File[] namespaces = contentsFolder.listFiles(File::isDirectory);
        if (namespaces == null) return result;

        for (File namespace : namespaces) {
            File[] files = new File(namespace, "stones")
                    .listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                load(namespace.getName(), file, result);
            }
        }
        return result;
    }

    private void load(String namespace, File file, Map<String, SocketStone> target) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            String fullId = namespace + ":" + id;
            if (target.containsKey(fullId)) {
                log.warning("Ayni tas id iki kez tanimli, atlaniyor: " + fullId
                        + " (" + file.getName() + ")");
                continue;
            }
            Map<String, Double> attributes = new HashMap<>();
            ConfigurationSection stats = section.getConfigurationSection("attributes");
            if (stats != null) {
                stats.getKeys(false).forEach(key -> attributes.put(key, stats.getDouble(key)));
            }
            target.put(fullId, new SocketStone(id, namespace,
                    section.getString("display", id),
                    section.getStringList("lore"),
                    section.getString("material", "FIREWORK_STAR"),
                    section.getString("texture", "item/" + id + ".png"),
                    section.getString("tint", "#ffffff"),
                    attributes,
                    section.getString("particle", "FLAME"),
                    section.getStringList("tags")));
        }
    }
}
