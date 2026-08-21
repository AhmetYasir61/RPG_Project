package net.aethel.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    public LangService(File dataFolder, Logger log) {
        this.langFolder = new File(dataFolder, "lang");
        this.log = log;
    }

    public void load(String defaultLanguage, String... available) {
        this.defaultLanguage = defaultLanguage;
        languages.clear();
        for (String code : available) {
            File file = new File(langFolder, code + ".yml");
            if (!file.exists()) {
                log.warning("Dil dosyasi bulunamadi: " + file.getName());
                continue;
            }
            languages.put(code, YamlConfiguration.loadConfiguration(file));
        }
        log.info("Yuklenen diller: " + languages.keySet());
    }

    /** Oyuncunun istemci dilini kullanir; desteklenmiyorsa varsayilana duser. */
    public String languageOf(CommandSender sender) {
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

    public static TagResolver of(String name, Number value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public MiniMessage miniMessage() {
        return mini;
    }
}
