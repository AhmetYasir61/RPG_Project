package net.aethel.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Cok dilli metin servisi. Her oyuncu kendi istemci diliyle konusur; dil dosyalari
 * klasorden otomatik bulunur ve eksik anahtarlar geri dusme zinciriyle doldurulur.
 */
public final class LangService {

    private final File langFolder;
    private final Logger log;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, YamlConfiguration> languages = new LinkedHashMap<>();

    /** Oyuncu dilinin cozulmus hali; her mesajda locale ayristirmamak icin. */
    private final Map<String, String> resolvedLocales = new ConcurrentHashMap<>();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    private String defaultLanguage = "en";
    private boolean followClient = true;

    public LangService(File dataFolder, Logger log) {
        this.langFolder = new File(dataFolder, "lang");
        this.log = log;
    }

    /**
     * Klasordeki TUM dil dosyalarini yukler. Sabit bir liste tutulmuyor: sunucu
     * sahibi lang/ altina yeni bir dosya birakinca dil kendiliginden devreye girer.
     */
    public void load(String defaultLanguage, boolean followClient) {
        this.defaultLanguage = normalize(defaultLanguage);
        this.followClient = followClient;
        languages.clear();
        resolvedLocales.clear();
        reportedMissing.clear();

        File[] files = langFolder.listFiles(file -> file.getName().endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.severe("lang/ klasorunde hicbir dil dosyasi yok.");
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) {
            String code = normalize(file.getName().replace(".yml", ""));
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            repairBrokenLinks(code, yaml);
            languages.put(code, yaml);
        }
        log.info("Yuklenen dil (" + languages.size() + "): " + languages.keySet());
        if (!languages.containsKey(this.defaultLanguage)) {
            log.warning("Varsayilan dil dosyasi yok: " + this.defaultLanguage
                    + " — ilk dil varsayilan olarak kullanilacak.");
            this.defaultLanguage = languages.keySet().iterator().next();
        }
    }

    /** Son ayarlarla dilleri diskten tazeler (/core reload). */
    public void reload() {
        load(defaultLanguage, followClient);
    }

    /**
     * Oyuncunun dilini cozer. Sira: tam yerel ad (pt_br) -> dil kodu (pt) ->
     * varsayilan. Bolgesel varyanti once denemek onemli: pt_br ile pt_pt, zh_cn ile
     * zh_tw arasindaki fark oyuncular icin belirgindir.
     */
    public String languageOf(CommandSender sender) {
        if (!followClient || !(sender instanceof Player player)) return defaultLanguage;

        String locale = normalize(player.locale().toString());
        return resolvedLocales.computeIfAbsent(locale, key -> {
            if (languages.containsKey(key)) return key;
            int underscore = key.indexOf('_');
            String base = underscore > 0 ? key.substring(0, underscore) : key;
            if (languages.containsKey(base)) return base;

            // "pt" istendi, elde "pt_br" var: ayni dilin herhangi bir varyanti,
            // tamamen baska bir dilden her zaman daha iyidir.
            for (String available : languages.keySet()) {
                if (available.startsWith(base + "_")) return available;
            }
            return defaultLanguage;
        });
    }

    /**
     * Anahtari cozup MiniMessage ile render eder. Anahtar oyuncunun dilinde yoksa
     * varsayilan dile, orada da yoksa anahtarin kendisine duser — ceviri eksikligi
     * mesaji kaybettirmez, yalnizca cevrilmemis gosterir.
     */
    public Component render(String language, String key, TagResolver... placeholders) {
        String raw = lookup(language, key);
        if (raw == null) {
            raw = lookup(defaultLanguage, key);
            if (raw != null && reportedMissing.add(language + "/" + key)) {
                log.warning("Eksik ceviri (" + language + "): " + key);
            }
        }
        if (raw == null) {
            if (reportedMissing.add("*/" + key)) log.warning("Eksik dil anahtari: " + key);
            return Component.text(key);
        }
        return mini.deserialize(raw, placeholders);
    }

    private String lookup(String language, String key) {
        YamlConfiguration yaml = languages.get(language);
        return yaml == null ? null : yaml.getString(key);
    }

    public Component render(CommandSender sender, String key, TagResolver... placeholders) {
        return render(languageOf(sender), key, placeholders);
    }

    /** Mesaji dogrudan gonderir. Modul kodunun %99'u bu metodu cagirir. */
    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        sender.sendMessage(render(sender, key, placeholders));
    }

    /** Kisayol: LangService.of("player", name) -> <player> etiketi. */
    public static TagResolver of(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    public static TagResolver of(String name, Number value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /**
     * Tiklanabilir baglanti yer tutucusu.
     *
     * Dil dosyasina <click:open_url:'<url>'> YAZILAMAZ: MiniMessage bir etiketin
     * ARGUMANI icindeki yer tutucuyu cozmez, literal "<url>" metni kalir ve sunucu
     * "https://<url>" adresini kodlamaya calisirken paket hatasi verir.
     */
    public static TagResolver link(String name, String url) {
        Component component = Component.text(url)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
        return Placeholder.component(name, component);
    }

    /** Yuklu dil kodlari; panel ve /core dil komutu icin. */
    public Set<String> languages() {
        return new LinkedHashSet<>(languages.keySet());
    }

    /** Bir dilin varsayilan dile gore ceviri orani (0..1); eksikleri gormek icin. */
    public double coverage(String language) {
        YamlConfiguration target = languages.get(language);
        YamlConfiguration base = languages.get(defaultLanguage);
        if (target == null || base == null) return 0;

        long total = base.getKeys(true).stream().filter(base::isString).count();
        if (total == 0) return 1;
        long translated = base.getKeys(true).stream()
                .filter(base::isString).filter(target::isString).count();
        return translated / (double) total;
    }

    public String defaultLanguage() {
        return defaultLanguage;
    }

    public boolean followClient() {
        return followClient;
    }

    public MiniMessage miniMessage() {
        return mini;
    }

    /** "tr_TR", "tr-TR", "TR" -> "tr_tr" */
    private String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('-', '_').trim();
    }

    /**
     * Eski dil dosyalarindaki bozuk baglanti kalibini onarir. Kullanicinin dil
     * dosyalari asla ezilmedigi icin bu kalip eski kurulumlarda kalir ve her
     * gonderimde paket hatasi uretir; burada bellekte duzeltilir.
     */
    private void repairBrokenLinks(String code, YamlConfiguration yaml) {
        int repaired = 0;
        for (String key : yaml.getKeys(true)) {
            String value = yaml.isString(key) ? yaml.getString(key) : null;
            if (value == null || !value.contains("click:open_url:'<")) continue;

            yaml.set(key, value.replaceAll("<click:open_url:'<[a-zA-Z0-9_]+>'>", "")
                    .replace("</click>", ""));
            repaired++;
        }
        if (repaired > 0) {
            log.warning("Bozuk baglanti kalibi onarildi (" + code + "): " + repaired
                    + " anahtar. Kalici duzeltme icin lang/" + code + ".yml dosyasini sil.");
        }
    }
}
