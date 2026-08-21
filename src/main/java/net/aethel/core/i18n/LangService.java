package net.aethel.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Cok dilli metin servisi. Tum metinler lang/*.yml icinde MiniMessage formatinda
 * durur; kodda hicbir kullaniciya gorunen string bulunmaz.
 */
public final class LangService {

    private final File langFolder;
    private final Logger log;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private String defaultLanguage = "tr";
    private boolean followClient = true;

    public LangService(File dataFolder, Logger log) {
        this.langFolder = new File(dataFolder, "lang");
        this.log = log;
    }

    /**
     * followClient true ise oyuncunun istemci dili tercih edilir; false ise herkese
     * varsayilan dil gonderilir. Tek dilli bir sunucuda istemci dilini takip etmek,
     * yabanci istemcili oyunculara yarim cevrilmis bir arayuz gostermek demektir.
     */
    public void load(String defaultLanguage, boolean followClient, String... available) {
        this.followClient = followClient;
        this.defaultLanguage = defaultLanguage;
        languages.clear();
        for (String code : available) {
            File file = new File(langFolder, code + ".yml");
            if (!file.exists()) {
                log.warning("Dil dosyasi bulunamadi: " + file.getName());
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            repairBrokenLinks(code, yaml);
            languages.put(code, yaml);
        }
        log.info("Yuklenen diller: " + languages.keySet());
    }

    /** Son kullanilan ayarlarla dilleri diskten tazeler (/core reload). */
    public void reload() {
        load(defaultLanguage, followClient, languages.keySet().toArray(String[]::new));
    }

    /**
     * Eski dil dosyalarindaki bozuk baglanti kalibini onarir. Kullanicinin dil
     * dosyalari asla ezilmedigi icin, daha once yazilmis dosyalarda bu kalip kalir
     * ve her gonderimde paket hatasi uretir; burada bellekte duzeltilir.
     */
    private void repairBrokenLinks(String code, YamlConfiguration yaml) {
        int repaired = 0;
        for (String key : yaml.getKeys(true)) {
            String value = yaml.isString(key) ? yaml.getString(key) : null;
            if (value == null || !value.contains("click:open_url:'<")) continue;

            String fixed = value.replaceAll("<click:open_url:'<[a-zA-Z0-9_]+>'>", "")
                    .replace("</click>", "");
            yaml.set(key, fixed);
            repaired++;
        }
        if (repaired > 0) {
            log.warning("Dil dosyasindaki bozuk baglanti kalibi onarildi (" + code
                    + "): " + repaired + " anahtar. Kalici duzeltme icin lang/" + code
                    + ".yml dosyasini silip sunucuyu yeniden baslat.");
        }
    }

    /** Oyuncunun istemci dilini kullanir; desteklenmiyorsa varsayilana duser. */
    public String languageOf(CommandSender sender) {
        if (!followClient) return defaultLanguage;
        if (sender instanceof Player player) {
            String code = player.locale().getLanguage().toLowerCase(Locale.ROOT);
            if (languages.containsKey(code)) return code;
        }
        return defaultLanguage;
    }

    /** Anahtari cozup MiniMessage ile render eder. Anahtar yoksa anahtarin kendisi doner. */
    public Component render(String language, String key, TagResolver... placeholders) {
        YamlConfiguration yaml = languages.getOrDefault(language, languages.get(defaultLanguage));
        String raw = yaml == null ? null : yaml.getString(key);
        if (raw == null) {
            log.warning("Eksik dil anahtari: " + key + " (" + language + ")");
            return Component.text(key);
        }
        return mini.deserialize(raw, placeholders);
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

    /**
     * Tiklanabilir baglanti yer tutucusu.
     *
     * Dil dosyasina <click:open_url:'<url>'> YAZILAMAZ: MiniMessage bir etiketin
     * ARGUMANI icindeki yer tutucuyu cozmez, literal "<url>" metni kalir ve sunucu
     * "https://<url>" adresini kodlamaya calisirken paket hatasi verir — mesaj
     * oyuncuya hic ulasmaz. Bu yuzden baglanti burada, hazir bir bilesen olarak kurulur.
     */
    public static TagResolver link(String name, String url) {
        Component component = Component.text(url)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
        return Placeholder.component(name, component);
    }

    public static TagResolver of(String name, Number value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public MiniMessage miniMessage() {
        return mini;
    }
}
