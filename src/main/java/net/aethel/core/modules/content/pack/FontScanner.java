package net.aethel.core.modules.content.pack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * contents/<ns>/fonts/*.yml dosyalarindan font parcasi tanimlarini toplar.
 * Her parca bir texture, yukseklik ve taban cizgisi kaymasi tasir.
 */
final class FontScanner {

    private FontScanner() {}

    static Map<String, FontGenerator.GlyphSpec> scan(File contents, Logger log) {
        Map<String, FontGenerator.GlyphSpec> specs = new LinkedHashMap<>();
        File[] namespaces = contents.listFiles(File::isDirectory);
        if (namespaces == null) return specs;

        for (File namespace : namespaces) {
            File folder = new File(namespace, "fonts");
            File[] files = folder.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;

            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String key : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(key);
                    if (section == null) continue;
                    specs.put(namespace.getName() + ":" + key, new FontGenerator.GlyphSpec(
                            section.getString("texture", "font/" + key + ".png"),
                            section.getInt("height", 8),
                            section.getInt("ascent", 7)));
                }
            }
        }
        log.info("Font parcasi: " + specs.size());
        return specs;
    }
}
