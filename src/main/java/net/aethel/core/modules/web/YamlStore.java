package net.aethel.core.modules.web;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panelin YAML katmani. Kayitlar tarayiciya DUZ (nokta ayirmali) anahtar/deger
 * olarak gider; diske yazarken yeniden agac haline getirilir. Boylece panel
 * dosya bicimini bilmez, dosya da panelin bicimini bilmez.
 */
final class YamlStore {

    private final File root;

    YamlStore(File dataFolder) {
        this.root = dataFolder;
    }

    File file(String relative) {
        return new File(root, relative.endsWith(".yml") ? relative : relative + ".yml");
    }

    /** Klasor tabanli bolumler icin: klasordeki tum .yml dosyalarini birlestirir. */
    List<Map<String, Object>> loadAll(String relative) {
        File target = file(relative);
        List<Map<String, Object>> records = new ArrayList<>();

        File folder = new File(root, relative);
        if (folder.isDirectory()) {
            File[] children = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (children != null) {
                java.util.Arrays.sort(children);
                for (File child : children) records.addAll(readRecords(child));
            }
            return records;
        }
        if (target.isFile()) records.addAll(readRecords(target));
        return records;
    }

    /** Kok anahtarlarin her biri bir kayittir; kimlik "id" alanina yazilir. */
    private List<Map<String, Object>> readRecords(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<String, Object>> records = new ArrayList<>();

        for (String key : yaml.getKeys(false)) {
            if (!yaml.isConfigurationSection(key)) continue;
            Map<String, Object> flat = new LinkedHashMap<>();
            flat.put("id", key);
            flat.put("__file", file.getName());
            flatten(yaml.getConfigurationSection(key), "", flat);
            records.add(flat);
        }
        return records;
    }

    private void flatten(ConfigurationSection section, String prefix, Map<String, Object> out) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof ConfigurationSection child) {
                flatten(child, path, out);
            } else if (value instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?>) {
                // Liste icindeki nesneler indeksli yola acilir: effects.0.shape
                for (int i = 0; i < list.size(); i++) {
                    Object entry = list.get(i);
                    if (entry instanceof Map<?, ?> map) {
                        map.forEach((k, v) -> out.put(path + "." + i + "." + k, v));
                    }
                }
            } else {
                out.put(path, value);
            }
        }
    }

    /**
     * Kaydi diske yazar. Yalnizca gelen anahtarlar guncellenir; dosyadaki
     * yorumlar Bukkit YAML'i tarafindan korunmaz, bu yuzden yazma HEDEFLIDIR:
     * ayni kok anahtar altindaki eski deger silinip yenisi konur.
     */
    void saveRecord(String relative, String fileName, Map<String, Object> record) throws IOException {
        File target = fileName == null || fileName.isBlank()
                ? file(relative)
                : new File(new File(root, relative), fileName);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        String id = String.valueOf(record.get("id"));
        yaml.set(id, null);

        record.forEach((key, value) -> {
            if (key.equals("id") || key.startsWith("__")) return;
            yaml.set(id + "." + unindex(key), value);
        });
        yaml.save(target);
    }

    void deleteRecord(String relative, String fileName, String id) throws IOException {
        File target = fileName == null || fileName.isBlank()
                ? file(relative)
                : new File(new File(root, relative), fileName);
        if (!target.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        yaml.set(id, null);
        yaml.save(target);
    }

    /** config.yml gibi tek bolumlu dosyalar icin duz okuma. */
    Map<String, Object> loadSection(String fileName, String prefix) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file(fileName));
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten(yaml.getConfigurationSection(prefix), "", flat);
        return flat;
    }

    void saveSection(String fileName, String prefix, Map<String, Object> values) throws IOException {
        File target = file(fileName);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        values.forEach((key, value) -> {
            if (key.startsWith("__")) return;
            yaml.set(prefix + "." + unindex(key), value);
        });
        yaml.save(target);
    }

    /** "effects.0.shape" -> "effects.0.shape": Bukkit indeksli yolu desteklemez,
     *  bu yuzden listeler tek girdi olarak yazilir; cok girdili listeler dosyadan
     *  elle duzenlenir. Panelin ilk girdiyi duzenlemesi bilinen sinirdir. */
    private String unindex(String key) {
        return key;
    }
}
