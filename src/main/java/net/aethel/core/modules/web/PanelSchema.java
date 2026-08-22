package net.aethel.core.modules.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panelin bolum/alan semasi. Tarayici tarafi bu semadan uretilir: yeni bir modul
 * alani eklemek icin YALNIZCA buraya satir eklenir, HTML'e dokunulmaz.
 *
 * Alan tanimi bicimi: "anahtar|Etiket|tip|secenek,secenek|ipucu"
 * tip: text | num | bool | sel | area | list | color
 */
public final class PanelSchema {

    private PanelSchema() {}

    /** Bir bolum: kaynak dosya + alan gruplari. */
    public record Section(String id, String label, String group, String file, Kind kind,
                          boolean readOnly, List<FieldGroup> groups) {}

    public record FieldGroup(String label, List<Field> fields) {}

    public record Field(String key, String label, String type, List<String> options, String hint) {}

    /**
     * FILE   : contents/ altindaki cok kayitli YAML (kok anahtar = kayit kimligi)
     * CONFIG : config.yml icindeki tek bolum (tek kayit, prefix ile)
     * FEATURES / MODULES / AUDIT / EVIDENCE / PLAYERS : ozel kaynaklar
     */
    public enum Kind { FILE, CONFIG, FEATURES, MODULES, AUDIT, EVIDENCE, PLAYERS }

    private static Field field(String spec) {
        String[] p = spec.split("\\|", -1);
        List<String> opts = p.length > 3 && !p[3].isBlank() ? List.of(p[3].split(",")) : List.of();
        return new Field(p[0], p[1], p.length > 2 ? p[2] : "text", opts, p.length > 4 ? p[4] : "");
    }

    private static FieldGroup group(String label, String... specs) {
        return new FieldGroup(label, java.util.Arrays.stream(specs).map(PanelSchema::field).toList());
    }

    private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();

    private static void add(Section section) {
        SECTIONS.put(section.id(), section);
    }

    static {
        // ---- Genel ----
        add(new Section("modules", "Moduller", "Genel", "modules.yml", Kind.MODULES, false, List.of(
                group("Modul", "id|Modul|text", "enabled|Etkin|bool", "state|Durum|text",
                        "depends|Bagimliliklar|list"))));
        add(new Section("features", "Ozellikler", "Genel", "features.yml", Kind.FEATURES, false, List.of(
                group("Anahtar", "id|Anahtar|text", "enabled|Durum|bool|" +
                        "|Degisiklik aninda gecerli; yeniden baslatma gerekmez."),
                group("Aciklama", "description|Ne yapar|area"))));
        add(new Section("audit", "Denetim Kaydi", "Genel", "web_audit", Kind.AUDIT, true, List.of(
                group("Kayit", "action|Islem|text", "actor|Islemi yapan|text", "target|Hedef|text",
                        "details|Ayrinti|area", "at|Zaman|text"))));
        add(new Section("evidence", "Kanit Deposu", "Genel", "web_evidence", Kind.EVIDENCE, true, List.of(
                group("Kayit", "item|Esya|text", "slot|Slot|num", "target|Oyuncu|text",
                        "actor|Islemi yapan|text", "reason|Gerekce|text", "at|Zaman|text"))));

        // ---- Icerik ----
        add(new Section("items", "Itemlar", "Icerik", "contents/%s/items", Kind.FILE, false, List.of(
                group("Kimlik", "id|Kimlik|text", "display|Gorunen ad|text",
                        "material|Materyal|text||Taban Bukkit materyali.",
                        "texture|Doku yolu|text", "rarity|Nadirlik|sel|COMMON,UNCOMMON,RARE,EPIC,LEGENDARY",
                        "custom-model-data|Model verisi|num"),
                group("Gorunum", "lore|Lore|area", "durability|Dayaniklilik|num", "glow|Parlama|bool"),
                group("Nitelikler", "attributes.damage|Hasar|num",
                        "attributes.attack-speed|Saldiri hizi|num", "tags|Etiketler|list"),
                group("Tuketim", "food.nutrition|Besin|num", "food.saturation|Doygunluk|num",
                        "food.always-edible|Her zaman yenebilir|bool", "food.eat-seconds|Yeme suresi|num"))));

        add(new Section("skills", "Yetenekler", "Icerik", "contents/%s/skills", Kind.FILE, false, List.of(
                group("Kimlik", "id|Kimlik|text", "display|Gorunen ad|text", "description|Aciklama|area"),
                group("Kullanim", "trigger|Tetikleyici|sel|MANUAL,ON_TIMER,ON_LOW_HEALTH,ON_DEAL_DAMAGE,ON_DAMAGED",
                        "cooldown|Soguma (sn)|num", "mana-cost|Mana maliyeti|num", "range|Menzil|num",
                        "targeting|Hedefleme|sel|AREA,LINE,SINGLE,ALL_ALLIES,SELF"),
                group("Degerler", "values.damage|Hasar|num", "values.heal|Iyilestirme|num", "sounds|Sesler|list"),
                group("Parcacik efekti", "effects.0.shape|Sekil|sel|RING,BEAM,BURST,HELIX,SPHERE",
                        "effects.0.motion|Hareket|sel|EXPAND,TRAVEL,STATIC,ORBIT",
                        "effects.0.particle|Parcacik|text", "effects.0.color|Renk|color",
                        "effects.0.count|Adet|num", "effects.0.radius|Yaricap|num",
                        "effects.0.duration|Sure (tick)|num", "effects.0.period|Periyot (tick)|num",
                        "effects.0.offset-y|Y kaymasi|num"))));

        add(new Section("mobs", "Moblar", "Icerik", "contents/%s/mobs", Kind.FILE, false, List.of(
                group("Kimlik", "id|Kimlik|text", "display|Gorunen ad|text", "type|Entity tipi|text",
                        "tier|Kademe|num"),
                group("Savas", "health|Can|num", "damage|Hasar|num", "armor|Zirh|num", "speed|Hiz|num",
                        "follow-range|Takip menzili|num"),
                group("Gorunum", "appearance.model|bbmodel|text", "appearance.helmet|Kask|text",
                        "appearance.main-hand|Ana el|text", "appearance.health-bar|Can bari|bool",
                        "appearance.boss-bar-color|Boss bar rengi|text"),
                group("Loot", "loot-table|Loot tablosu|text"),
                group("Dogma", "spawn.worlds|Dunyalar|list", "spawn.biomes|Biyomlar|list",
                        "spawn.min-y|Min Y|num", "spawn.max-y|Maks Y|num", "spawn.max-light|Maks isik|num",
                        "spawn.chance|Sans|num", "spawn.max-nearby|Yakinda maks|num"))));

        add(new Section("dialogs", "Diyaloglar", "Icerik", "contents/%s/dialogs", Kind.FILE, false, List.of(
                group("Dugum", "id|Kimlik|text", "speaker|Konusan|text", "portrait|Portre|text",
                        "chars-per-tick|Tick basina harf|num"),
                group("Metin", "lines|Satirlar|area"),
                group("Sonuc", "outcomes|Sonuclar|list||[quest], [item], [message]"))));

        add(new Section("menus", "Menuler", "Icerik", "contents/%s/menus", Kind.FILE, false, List.of(
                group("Menu", "id|Kimlik|text", "title|Baslik|text", "rows|Satir|num"))));

        add(new Section("fonts", "Fontlar", "Icerik", "contents/%s/fonts", Kind.FILE, false, List.of(
                group("Parca", "id|Kimlik|text", "texture|Doku|text", "height|Yukseklik|num",
                        "ascent|Taban cizgisi|num"))));

        add(new Section("loot", "Loot Tablolari", "Icerik", "loot/tables", Kind.FILE, false, List.of(
                group("Tablo", "id|Tablo kimligi|text", "min-rolls|Min cekim|num", "max-rolls|Maks cekim|num"))));

        add(new Section("pack", "Kaynak Paketi", "Icerik", "resource-pack", Kind.CONFIG, false, List.of(
                group("Uretim", "generate|Uret|bool", "generate-on-start|Acilista uret|bool",
                        "force|Zorunlu gonder|bool"),
                group("Sunum", "serve|Sun|bool", "bind|Dinlenen adres|text", "port|Port|num",
                        "public-host|Genel adres|text"))));

        // ---- Dunya ----
        add(new Section("regions", "Bolgeler", "Dunya", "regions/regions", Kind.FILE, false, List.of(
                group("Bolge", "id|Kimlik|text", "world|Dunya|text", "difficulty|Zorluk|text",
                        "priority|Oncelik|num"))));
        add(new Section("difficulties", "Zorluklar", "Dunya", "regions/difficulties", Kind.FILE, false, List.of(
                group("Zorluk", "id|Kimlik|text", "displayName|Gorunen ad|text",
                        "percent|Yuzde|num||100 = vanilla dengesi.", "icon|Font ikonu|text",
                        "hardcore|Hardcore|bool"))));
        add(new Section("dungeon", "Dungeonlar", "Dunya", "dungeon", Kind.CONFIG, false, List.of(
                group("Yasam suresi", "collapse-seconds|Cokme suresi (sn)|num",
                        "empty-timeout-seconds|Bos ornek zaman asimi|num", "max-instances|Azami ornek|num"),
                group("Tasarim dunyasi", "design-world|Tasarim dunyasi|text", "room-min-y|Oda min Y|num",
                        "room-height|Oda yuksekligi|num"),
                group("Cikis", "exit-world|Cikis dunyasi|text", "exit-biomes|Cikis biyomlari|list",
                        "exit-scatter|Cikis dagilimi|num"))));
        add(new Section("waypoints", "Waypointler", "Dunya", "travel/waypoints", Kind.FILE, false, List.of(
                group("Nokta", "id|Kimlik|text", "display|Gorunen ad|text", "world|Dunya|text",
                        "x|X|num", "y|Y|num", "z|Z|num"),
                group("Kural", "discovery-required|Kesif gerekli|bool", "cost|Ucret|num"))));
        add(new Section("npc", "NPC & Hologram", "Dunya", "npc/npcs", Kind.FILE, false, List.of(
                group("NPC", "id|Kimlik|text", "name|Ad|text", "world|Dunya|text", "skin|Kaplama|text",
                        "dialog|Diyalog|text"),
                group("Etkilesim", "menu|Menu|text", "quest|Gorev|text",
                        "hologram-lines|Hologram satirlari|list"))));

        // ---- Oyuncu ----
        add(new Section("players", "Oyuncular", "Oyuncu", "profile", Kind.PLAYERS, false, List.of(
                group("Hesap", "name|Oyuncu|text", "uuid|UUID|text", "world|Dunya|text", "online|Cevrimici|bool"),
                group("RPG", "rpg:level|Seviye|num", "rpg:stat:strength|Guc|num",
                        "rpg:stat:dexterity|Ceviklik|num", "rpg:stat:intelligence|Zeka|num",
                        "rpg:stat:vitality|Dayaniklilik|num", "rpg:stat:luck|Sans|num"),
                group("Ekonomi", "balance|Bakiye|num", "groups|Gruplar|list"),
                group("Envanter", "inventory|Envanter|list||El konulan esya silinmez, kanit deposuna tasinir."))));
        add(new Section("rpg", "RPG", "Oyuncu", "rpg", Kind.CONFIG, false, List.of(
                group("Seviye", "max-level|Seviye tavani|num", "points-per-level|Seviye basina puan|num",
                        "xp-per-mob-level|Mob basina XP|num"),
                group("Mana", "base-mana|Temel mana|num", "mana-per-intelligence|Zeka basina mana|num",
                        "mana-regen|Mana yenilenme /sn|num"),
                group("Stat etkileri", "damage-per-strength|Guc basina hasar|num",
                        "health-per-vitality|Dayaniklilik basina can|num"))));
        add(new Section("jobs", "Meslekler", "Oyuncu", "jobs", Kind.CONFIG, false, List.of(
                group("Sinir", "max-active|Ayni anda meslek|num",
                        "leave-cooldown-hours|Birakma sogumasi (saat)|num",
                        "progress-kept-on-leave|Korunan ilerleme|num"),
                group("Bildirim", "announce-level-up|Seviye duyurusu|bool"))));
        add(new Section("permissions", "Yetkiler", "Oyuncu", "permissions/groups", Kind.FILE, false, List.of(
                group("Grup", "name|Grup adi|text", "displayName|Gorunen ad|text", "weight|Agirlik|num"),
                group("Sohbet", "prefix|On ek|text", "suffix|Son ek|text"),
                group("Kalitim", "parents|Ust gruplar|list", "permissions|Izinler|list"))));
        add(new Section("economy", "Ekonomi", "Oyuncu", "economy", Kind.CONFIG, false, List.of(
                group("Para birimi", "currency-symbol|Simge|text", "currency-name|Ad|text",
                        "decimals|Ondalik|num"),
                group("Bakiyeler", "starting-balance|Baslangic bakiyesi|num", "max-balance|Azami bakiye|num"),
                group("Entegrasyon", "register-vault|Vault saglayicisi|bool",
                        "keep-transaction-log|Islem gecmisi|bool"))));
        add(new Section("pcoins", "PCoins", "Oyuncu", "pcoins", Kind.CONFIG, false, List.of(
                group("Baglanti", "enabled|Etkin|bool", "endpoint|Uc nokta|text", "api-key|API anahtari|text"),
                group("Davranis", "claim-purchases|Satin alma teslimi|bool", "spending|Oyun ici harcama|bool"))));
        add(new Section("party", "Parti", "Oyuncu", "party", Kind.CONFIG, false, List.of(
                group("Sinir", "max-members|Azami uye|num", "invite-timeout-seconds|Davet zaman asimi|num"),
                group("Paylasim", "shared-xp|Ortak XP|bool", "shared-loot|Ortak loot|bool",
                        "tracking|Uye takibi|bool"))));
        add(new Section("travel", "Seyahat", "Oyuncu", "travel", Kind.CONFIG, false, List.of(
                group("Cast sureleri", "scroll-cast-seconds|Parsomen cast (sn)|num",
                        "hearthstone-cast-seconds|Ocak tasi cast (sn)|num",
                        "hearthstone-cooldown-minutes|Ocak tasi soguma (dk)|num"),
                group("Kisitlar", "combat-lock-seconds|Savas kilidi (sn)|num",
                        "require-discovery|Kesif zorunlu|bool", "discovery-radius|Kesif yaricapi|num"),
                group("Item", "scroll-item|Parsomen item|text"))));
        add(new Section("auth", "Kimlik", "Oyuncu", "auth", Kind.CONFIG, false, List.of(
                group("Sir", "mode|Mod|sel|PIN,PASSWORD", "min-length|Min uzunluk|num",
                        "max-length|Maks uzunluk|num"),
                group("Oturum", "max-attempts|Maks deneme|num", "timeout-seconds|Zaman asimi (sn)|num",
                        "session-minutes|Oturum (dk)|num", "web-token-seconds|Web jetonu (sn)|num"),
                group("Davranis", "freeze-until-login|Girise kadar dondur|bool"))));

        // ---- Arayuz ----
        add(new Section("quests", "Gorevler", "Arayuz", "contents/%s/quests", Kind.FILE, false, List.of(
                group("Gorev", "id|Kimlik|text", "displayName|Gorunen ad|text", "description|Aciklama|area",
                        "requiredLevel|Gerekli seviye|num", "repeatable|Tekrarlanabilir|bool",
                        "nextQuest|Sonraki gorev|text"),
                group("Hedef", "objectives.0.type|Hedef tipi|sel|KILL,COLLECT,CRAFT,TRAVEL,TALK,REACH_LEVEL",
                        "objectives.0.target|Hedef|text", "objectives.0.amount|Adet|num",
                        "objectives.0.description|Hedef aciklamasi|text"),
                group("Oduller", "rewards|Oduller|list"))));
        add(new Section("hud", "HUD", "Arayuz", "hud/layouts", Kind.FILE, false, List.of(
                group("Duzen", "id|Kimlik|text", "condition|Kosul|text", "priority|Oncelik|num",
                        "updateTicks|Guncelleme (tick)|num"),
                group("Katman", "layers.0.text|Katman metni|text", "layers.0.offsetX|X kaymasi (px)|num",
                        "layers.0.showWhen|Gosterim kosulu|text"))));
        add(new Section("scoreboard", "Scoreboard", "Arayuz", "scoreboard", Kind.CONFIG, false, List.of(
                group("Yenileme", "update-ticks|Guncelleme (tick)|num"),
                group("Gorunurluk", "sidebar-enabled|Yan tablo|bool", "tab-enabled|Tab listesi|bool",
                        "tab-prefix|Tab on eki|bool"))));
        add(new Section("motd", "MOTD", "Arayuz", "motd", Kind.CONFIG, false, List.of(
                group("Metin", "random|Rastgele MOTD|bool", "icon-file|Sunucu ikonu|text"),
                group("Oyuncu sayisi", "custom-player-count|Ozel oyuncu sayisi|bool",
                        "fake-max-players|Sahte azami|num", "hover-enabled|Hover listesi|bool"))));
        add(new Section("chat", "Sohbet", "Arayuz", "chat", Kind.CONFIG, false, List.of(
                group("Bicim", "format|Format|text", "local-radius|Yerel yaricap|num"),
                group("Denetim", "mentions|Bahsetme sesi|bool", "filter|Kelime filtresi|bool",
                        "filter-words|Filtre listesi|list"))));
    }

    public static Map<String, Section> sections() {
        return java.util.Collections.unmodifiableMap(SECTIONS);
    }

    public static Section section(String id) {
        return SECTIONS.get(id);
    }
}
