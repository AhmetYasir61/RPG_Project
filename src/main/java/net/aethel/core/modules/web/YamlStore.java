package net.aethel.core.modules.web;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panelin YAML katmani. Kayitlar tarayiciya DUZ (nokta ayirmali) anahtar/deger
 * olarak gider; diske yazarken yeniden agac haline getirilir. Boylece panel
 * dosya bicimini bilmez, dosya da panelin bicimini bilmez.
 *
 * Yol icinde "%s" varsa (ornegin contents/%s/items) bu bir NAMESPACE yeridir:
 * okurken contents/ altindaki tum namespace klasorleri taranir, yazarken kaydin
 * tasidigi __ns alani kullanilir. Boylece panel birden fazla icerik paketini
 * ayni listede gosterir ama dogru dosyaya geri yazar.
 */
final class YamlStore {

    /** Kayitlarda dosya adini ve namespace'i tasiyan gizli alanlar. */
    static final String FILE_KEY = "__file";
    static final String NS_KEY = "__ns";

    private final File root;

    YamlStore(File dataFolder) {
        this.root = dataFolder;
    }

    File file(String relative) {
        return new File(root, relative.endsWith(".yml") ? relative : relative + ".yml");
    }

    /** contents/ altindaki namespace klasorleri; hicbiri yoksa "aethel" varsayilir. */
    List<String> namespaces() {
        File[] dirs = new File(root, "contents").listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return List.of("aethel");
        return Arrays.stream(dirs).map(File::getName).sorted().toList();
    }

    /** Klasor tabanli bolumler icin: klasordeki tum .yml dosyalarini birlestirir. */
    List<Map<String, Object>> loadAll(String relative) {
        if (relative.contains("%s")) {
            List<Map<String, Object>> all = new ArrayList<>();
            for (String ns : namespaces()) all.addAll(read(relative.formatted(ns), ns));
            return all;
        }
        return read(relative, "");
    }

    private List<Map<String, Object>> read(String relative, String namespace) {
        List<Map<String, Object>> records = new ArrayList<>();
        File folder = new File(root, relative);
        if (folder.isDirectory()) {
            File[] children = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (children != null) {
                Arrays.sort(children);
                for (File child : children) records.addAll(readRecords(child, namespace));
            }
            return records;
        }
        File target = file(relative);
        if (target.isFile()) records.addAll(readRecords(target, namespace));
        return records;
    }

    /** Kok anahtarlarin her biri bir kayittir; kimlik "id" alanina yazilir. */
    private List<Map<String, Object>> readRecords(File file, String namespace) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<String, Object>> records = new ArrayList<>();

        for (String key : yaml.getKeys(false)) {
            if (!yaml.isConfigurationSection(key)) continue;
            Map<String, Object> flat = new LinkedHashMap<>();
            flat.put("id", key);
            flat.put(FILE_KEY, file.getName());
            flat.put(NS_KEY, namespace);
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
                    if (list.get(i) instanceof Map<?, ?> map) {
                        int index = i;
                        map.forEach((k, v) -> out.put(path + "." + index + "." + k, v));
                    }
                }
            } else {
                out.put(path, value);
            }
        }
    }

    /**
     * Kaydi diske yazar. Yazma HEDEFLIDIR: ayni kok anahtar altindaki eski deger
     * once silinip yenisi konur, boylece panelde bosaltilan bir alan dosyada
     * hayalet deger olarak kalmaz.
     */
    void saveRecord(String relative, Map<String, Object> record) throws IOException {
        File target = target(relative, record);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        String id = String.valueOf(record.get("id"));
        yaml.set(id, null);

        record.forEach((key, value) -> {
            if (key.equals("id") || key.startsWith("__")) return;
            yaml.set(id + "." + key, value);
        });
        yaml.save(target);
    }

    void deleteRecord(String relative, Map<String, Object> record) throws IOException {
        File target = target(relative, record);
        if (!target.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        yaml.set(String.valueOf(record.get("id")), null);
        yaml.save(target);
    }

    /** Kaydin geldigi (ya da gidecegi) dosya: namespace + dosya adi. */
    private File target(String relative, Map<String, Object> record) {
        String path = relative;
        if (path.contains("%s")) {
            Object ns = record.get(NS_KEY);
            path = path.formatted(ns == null || String.valueOf(ns).isBlank()
                    ? namespaces().get(0) : String.valueOf(ns));
        }
        Object fileName = record.get(FILE_KEY);
        File folder = new File(root, path);
        if (fileName != null && !String.valueOf(fileName).isBlank()) {
            return new File(folder, String.valueOf(fileName));
        }
        // Yeni kayit: klasor tabanli bolumde varsayilan dosya, degilse duz dosya.
        return folder.isDirectory() ? new File(folder, "panel.yml") : file(path);
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
            if (key.startsWith("__") || key.equals("id")) return;
            yaml.set(prefix + "." + key, value);
        });
        yaml.save(target);
    }
}
