package net.aethel.core.modules.web;

import com.google.gson.Gson;
import net.aethel.core.api.EconomyService;
import net.aethel.core.api.PermissionService;
import net.aethel.core.api.PlayerProfile;
import net.aethel.core.api.ProfileService;
import net.aethel.core.api.RpgService;
import net.aethel.core.api.StatType;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.modules.web.PanelSchema.Section;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Panelin veri katmani. Bolum kimligi disinda hicbir sey bilmez: ne okunacagi da
 * ne yazilacagi da {@link PanelSchema}'dan gelir, boylece yeni bir modul alani
 * eklemek icin yalnizca semaya satir eklenir.
 *
 * Butun oyun durumu erisimi cekirdek servisleri uzerinden yapilir; bu sinif
 * Bukkit'e dogrudan yalnizca cevrimici oyuncu listesi icin dokunur.
 */
final class PanelApi {

    private static final Gson GSON = new Gson();

    private final CoreContext ctx;
    private final YamlStore store;
    private final AuditLog audit;
    private final EvidenceStore evidence;
    private final long startedAt = System.currentTimeMillis();

    PanelApi(CoreContext ctx, AuditLog audit, EvidenceStore evidence) {
        this.ctx = ctx;
        this.store = new YamlStore(ctx.plugin().getDataFolder());
        this.audit = audit;
        this.evidence = evidence;
    }

    /** Bolum tanimlari; tarayici arayuzu bundan da cizilebilir. */
    String schemaJson() {
        return GSON.toJson(PanelSchema.sections().values());
    }

    String recordsJson(Section section) {
        return GSON.toJson(records(section));
    }

    /** Bir bolumun kayitlari. Kaynak, bolumun turune gore secilir. */
    List<Map<String, Object>> records(Section section) {
        return switch (section.kind()) {
            case FILE -> store.loadAll(section.file());
            case CONFIG -> List.of(configRecord(section));
            case FEATURES -> features();
            case MODULES -> modules();
            case AUDIT -> auditRecords();
            case EVIDENCE -> evidenceRecords();
            case PLAYERS -> players();
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
        list.sort(java.util.Comparator.comparing(row -> String.valueOf(row.get("id"))));
        return list;
    }

    /**
     * Modul listesi modules.yml'den okunur, calisan ModuleManager'dan degil:
     * panel yalnizca "acik mi kapali mi" sorusunu duzenler ve degisiklik bir
     * sonraki acilista gecerli olur (bkz. saveModule).
     */
    private List<Map<String, Object>> modules() {
        List<Map<String, Object>> list = new ArrayList<>();
        var yaml = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new File(ctx.plugin().getDataFolder(), "modules.yml"));
        var section = yaml.getConfigurationSection("modules");
        if (section == null) return list;
        for (String id : section.getKeys(false)) {
            boolean enabled = section.getBoolean(id + ".enabled", true);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", id);
            record.put("enabled", enabled);
            record.put("state", enabled ? "AKTIF" : "KAPALI");
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

    private List<Map<String, Object>> evidenceRecords() {
        List<Map<String, Object>> list = new ArrayList<>();
        evidence.recent(200).join().forEach(entry -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", String.valueOf(entry.id()));
            record.put("item", entry.item() == null ? "?" : entry.item().getType().name());
            record.put("target", entry.owner().toString());
            record.put("actor", entry.actor().toString());
            record.put("reason", entry.reason());
            record.put("at", java.time.Instant.ofEpochMilli(entry.createdAt()).toString());
            list.add(record);
        });
        return list;
    }

    /**
     * Cevrimici oyuncular. Envanter yalnizca OKUNUR bir ozet olarak gonderilir;
     * esya kaldirma islemi ayri bir uctan (seize) gecer ve kanit deposuna yazar.
     */
    private List<Map<String, Object>> players() {
        List<Map<String, Object>> list = new ArrayList<>();
        var rpg = ctx.services().optional(RpgService.class);
        var economy = ctx.services().optional(EconomyService.class);
        var permissions = ctx.services().optional(PermissionService.class);

        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", player.getName());
            row.put("name", player.getName());
            row.put("uuid", uuid.toString());
            row.put("world", player.getWorld().getName());
            row.put("online", true);
            rpg.ifPresent(service -> {
                row.put("rpg:level", service.level(uuid));
                for (StatType type : StatType.values()) {
                    row.put("rpg:stat:" + type.name().toLowerCase(), service.stat(uuid, type));
                }
            });
            economy.ifPresent(service -> row.put("balance", service.balance(uuid)));
            permissions.ifPresent(service -> row.put("groups", service.groupsOf(uuid)));

            List<String> inventory = new ArrayList<>();
            var contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] != null) {
                    inventory.add(slot + " · " + contents[slot].getType()
                            + " ×" + contents[slot].getAmount());
                }
            }
            row.put("inventory", inventory);
            list.add(row);
        }
        return list;
    }

    /** Sunucu durumu: panelin ust seridi bunu her acilista bir kez ceker. */
    Map<String, Object> status() {
        Runtime runtime = Runtime.getRuntime();
        var server = ctx.plugin().getServer();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tps", Math.round(server.getTPS()[0] * 10) / 10.0);
        out.put("players", server.getOnlinePlayers().size());
        out.put("maxPlayers", server.getMaxPlayers());
        out.put("uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000);
        out.put("memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576);
        out.put("memoryMaxMb", runtime.maxMemory() / 1048576);
        out.put("storage", ctx.config().get("config.yml").yaml()
                .getString("storage.type", "SQLITE"));
        out.put("modules", modules());
        return out;
    }

    /**
     * Kaydi diske yazar. Salt okunur bolumler sunucu tarafinda reddedilir:
     * istemcinin ne gonderdigine guvenilmez.
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
            case PLAYERS -> savePlayer(record);
            default -> throw new IllegalStateException("read-only");
        }
    }

    /**
     * Modul acma/kapama dosyaya yazilir, calisan sunucuda anlik uygulanmaz:
     * bir modulu ortasinda sokup takmak acik listener'lari ve zamanlayicilari
     * yarim birakir. Degisiklik bir sonraki acilista gecerli olur.
     */
    private void saveModule(Map<String, Object> record) throws Exception {
        File file = new File(ctx.plugin().getDataFolder(), "modules.yml");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        yaml.set("modules." + record.get("id") + ".enabled",
                Boolean.TRUE.equals(record.get("enabled")));
        yaml.save(file);
    }

    /**
     * Oyuncu duzenleme. Yalnizca stat, seviye ve bakiye yazilir; envanter
     * listesi salt okunurdur. Oyuncu cevrimdisiysa islem sessizce atlanir --
     * onbellekte olmayan bir profili buradan yazmak veriyi ezme riski tasir.
     */
    private void savePlayer(Map<String, Object> record) {
        UUID uuid = UUID.fromString(String.valueOf(record.get("uuid")));
        ctx.services().optional(RpgService.class).ifPresent(rpg -> {
            for (StatType type : StatType.values()) {
                Object value = record.get("rpg:stat:" + type.name().toLowerCase());
                if (value instanceof Number number) rpg.setStat(uuid, type, number.intValue());
            }
        });
        if (record.get("balance") instanceof Number balance) {
            ctx.services().optional(EconomyService.class).ifPresent(economy -> {
                double current = economy.balance(uuid);
                double delta = balance.doubleValue() - current;
                if (delta > 0) economy.deposit(uuid, delta, "panel");
                else if (delta < 0) economy.withdraw(uuid, -delta, "panel");
            });
        }
        ctx.services().optional(ProfileService.class)
                .flatMap(profiles -> profiles.cached(uuid))
                .ifPresent(this::touch);
    }

    private void touch(PlayerProfile profile) {
        ctx.services().optional(ProfileService.class)
                .ifPresent(profiles -> profiles.save(profile));
    }

    void delete(Section section, Map<String, Object> record) throws Exception {
        if (section.readOnly() || section.kind() != PanelSchema.Kind.FILE) {
            throw new IllegalStateException("read-only");
        }
        store.deleteRecord(section.file(), record);
    }

    /** Tarayicidan gelen JSON govdesi duz anahtar/deger haritasina cevrilir. */
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

    static String json(Object value) {
        return GSON.toJson(value);
    }
}
