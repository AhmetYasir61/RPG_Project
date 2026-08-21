package net.aethel.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Eski modules/*.yml dosyalarindaki ayarlari config.yml'ye tasir. Ayarlar bir
 * donem yanlis dosyadan okunuyordu; kullanicinin girdigi degerler kaybolmamali.
 */
public final class LegacyConfigMigration {

    /** Tasinacak dosyalar ve config.yml'de karsilik gelen kok bolumler. */
    private static final List<String[]> MOVES = List.of(
            new String[] {"modules/content.yml", "resource-pack"},
            new String[] {"modules/auth.yml", "auth"},
            new String[] {"modules/panel.yml", "admin"},
            new String[] {"modules/web.yml", "admin"});

    private LegacyConfigMigration() {}

    /**
     * Eski dosya varsa degerlerini config.yml'ye kopyalar ve dosyayi .tasindi
     * uzantisiyla saklar. Silmiyoruz: yanlis bir tasima olursa kullanici elindeki
     * degerlere geri donebilmeli.
     */
    public static void run(File dataFolder, ConfigFile coreConfig, Logger log) {
        int moved = 0;
        for (String[] move : MOVES) {
            File legacy = new File(dataFolder, move[0]);
            if (!legacy.exists()) continue;

            YamlConfiguration old = YamlConfiguration.loadConfiguration(legacy);
            moved += copySection(old, coreConfig.yaml(), move[1], log, move[0]);
            legacy.renameTo(new File(legacy.getParentFile(), legacy.getName() + ".tasindi"));
        }
        if (moved > 0) {
            coreConfig.save();
            log.warning("Eski ayar dosyalarindan " + moved + " deger config.yml'ye tasindi. "
                    + "Eski dosyalar .tasindi uzantisiyla saklandi.");
        }
    }

    /**
     * Yalnizca config.yml'de OLMAYAN ya da varsayilanla ayni olan yollari tasir?
     * Hayir: kullanicinin eski dosyada girdigi deger daha guncel sayilir, cunku
     * o dosya o zamana kadar GERCEKTEN okunan dosyaydi.
     */
    private static int copySection(YamlConfiguration source, YamlConfiguration target,
                                   String rootKey, Logger log, String fileName) {
        var section = source.getConfigurationSection(rootKey);
        if (section == null) return 0;

        int count = 0;
        for (String key : section.getKeys(true)) {
            String path = rootKey + "." + key;
            if (source.isConfigurationSection(path)) continue;

            Object value = source.get(path);
            if (value == null) continue;
            Object current = target.get(path);
            if (value.equals(current)) continue;

            target.set(path, value);
            log.info("Tasindi (" + fileName + "): " + path + " = " + value);
            count++;
        }
        return count;
    }
}
