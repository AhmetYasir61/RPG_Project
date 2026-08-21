package net.aethel.core.modules.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.modules.web.PanelSchema.Section;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semadan uretilen panel veri katmani. Tarayici burayi genel olarak kullanir:
 * "bolum listesi ver", "su bolumun kayitlarini ver", "su kaydi yaz". Bolumlerin
 * adlarini ne bu sinif ne de tarayici bilir; ikisi de {@link PanelSchema}'ya bakar.
 */
final class PanelApi {

    private static final Gson GSON = new Gson();

    private final CoreContext ctx;
    private final YamlStore store;
    private final AuditLog audit;

    PanelApi(CoreContext ctx, AuditLog audit) {
        this.ctx = ctx;
        this.store = new YamlStore(ctx.plugin().getDataFolder());
        this.audit = audit;
    }

    /** Tum bolumlerin tanimi; tarayici arayuzu bundan cizilir. */
    String schemaJson() {
        JsonObject root = new JsonObject();
        JsonArray sections = new JsonArray();
        PanelSchema.sections().values().forEach(section -> sections.add(describe(section)));
        root.add("sections", sections);
        JsonArray namespaces = new JsonArray();
        store.namespaces().forEach(namespaces::add);
        root.add("namespaces", namespaces);
        return root.toString();
    }

    private JsonObject describe(Section section) {
        JsonObject json = new JsonObject();
        json.addProperty("id", section.id());
        json.addProperty("label", section.label());
        json.addProperty("group", section.group());
        json.addProperty("file", section.file());
        json.addProperty("kind", section.kind().name());
        json.addProperty("readOnly", section.readOnly());
        JsonArray groups = new JsonArray();
        section.groups().forEach(group -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("label", group.label());
            JsonArray fields = new JsonArray();
            group.fields().forEach(field -> {
                JsonObject spec = new JsonObject();
                spec.addProperty("key", field.key());
                spec.addProperty("label", field.label());
                spec.addProperty("type", field.type());
                spec.addProperty("hint", field.hint());
                JsonArray options = new JsonArray();
                field.options().forEach(options::add);
                spec.add("options", options);
                fields.add(spec);
            });
            entry.add("fields", fields);
            groups.add(entry);
        });
        json.add("groups", groups);
        return json;
    }

    /** Bir bolumun kayitlari. Kaynak, bolumun turune gore secilir. */
    List<Map<String, Object>> records(Section section) {
        return switch (section.kind()) {
            case FILE -> store.loadAll(section.file());
            case CONFIG -> List.of(configRecord(section));
            case FEATURES -> features();
            case MODULES -> modules();
            case AUDIT -> auditRecords();
            case EVIDENCE, PLAYERS -> List.of();
        };
    }

    /** CONFIG bolumleri "dosya.yml#prefix" bicimindedir; tek kayit dondururler. */
    private Map<String, Object> configRecord(Section section) {
        String[] parts = section.file().split("#", 2);
        Map<String, Object> record = new LinkedHashMap<>(
                store.loadSection(parts[0], parts.length > 1 ? parts[1] : ""));
        record.put("id", section.id());
        record.put(YamlStore.FILE_KEY, parts[0]);
        return record;
    }

    private List<Map<String, Object>> features() {
        List<Map<String, Object>> list = new ArrayList<>();
        ctx.features().snapshot().forEach((key, enabled) -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", key);
            record.put("enabled", enabled);
            record.put("description", ctx.features().description(key));
            list.add(record);
        });
        list.sort((a, b) -> String.valueOf(a.get("id")).compareTo(String.valueOf(b.get("id"))));
        return list;
    }

    private List<Map<String, Object>> modules() {
        List<Map<String, Object>> list = new ArrayList<>();
        var yaml = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new File(ctx.plugin().getDataFolder(), "modules.yml"));
        var section = yaml.getConfigurationSection("modules");
        if (section == null) return list;
        for (String id : section.getKeys(false)) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", id);
            record.put("enabled", section.getBoolean(id + ".enabled", true));
            record.put("state", section.getBoolean(id + ".enabled", true) ? "AKTIF" : "KAPALI");
            list.add(record);
        }
        return list;
    }

    private List<Map<String, Object>> auditRecords() {
        List<Map<String, Object>> list = new ArrayList<>();
        audit.recent(200).join().forEach(entry -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", String.valueOf(entry.id()));
            record.put("action", entry.action());
            record.put("actor", entry.actorName());
            record.put("target", entry.target() == null ? "" : entry.target().toString());
            record.put("details", entry.details());
            record.put("at", java.time.Instant.ofEpochMilli(entry.createdAt()).toString());
            list.add(record);
        });
        return list;
    }

    /**
     * Kaydi diske yazar. Salt okunur bolumler ve bilinmeyen turler reddedilir;
     * panelden gelen bir istek asla denetim kaydini duzenleyemez.
     */
    void save(Section section, Map<String, Object> record) throws Exception {
        if (section.readOnly()) throw new IllegalStateException("read-only");
        switch (section.kind()) {
            case FILE -> store.saveRecord(section.file(), record);
            case CONFIG -> {
                String[] parts = section.file().split("#", 2);
                store.saveSection(parts[0], parts.length > 1 ? parts[1] : "", record);
                ctx.config().reloadAll();
            }
            case FEATURES -> ctx.features().set(String.valueOf(record.get("id")),
                    Boolean.TRUE.equals(record.get("enabled")));
            case MODULES -> saveModule(record);
            default -> throw new IllegalStateException("read-only");
        }
    }

    /**
     * Modul acma/kapama dosyaya yazilir, calisan sunucuda anlik uygulanmaz:
     * bir modulu ortasinda sokup takmak kayit acik listener'lari ve zamanlayicilari
     * yarim birakir. Degisiklik bir sonraki acilista gecerli olur.
     */
    private void saveModule(Map<String, Object> record) throws Exception {
        File file = new File(ctx.plugin().getDataFolder(), "modules.yml");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        yaml.set("modules." + record.get("id") + ".enabled",
                Boolean.TRUE.equals(record.get("enabled")));
        yaml.save(file);
    }

    void delete(Section section, Map<String, Object> record) throws Exception {
        if (section.readOnly() || section.kind() != PanelSchema.Kind.FILE) {
            throw new IllegalStateException("read-only");
        }
        store.deleteRecord(section.file(), record);
    }

    /** Tarayicidan gelen JSON gövdesi duz anahtar/deger haritasina cevrilir. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String body) {
        Map<String, Object> raw = GSON.fromJson(body, Map.class);
        if (raw == null) return Map.of();
        // Gson her sayiyi Double okur; tam sayilar YAML'e "5.0" olarak yazilmasin.
        Map<String, Object> clean = new LinkedHashMap<>();
        raw.forEach((key, value) -> clean.put(key,
                value instanceof Double number && number == Math.floor(number)
                        && !number.isInfinite() ? (Object) number.intValue() : value));
        return clean;
    }
}
