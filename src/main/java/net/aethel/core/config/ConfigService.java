package net.aethel.core.config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tum config dosyalarinin merkezi kaydi. Modul dosyasini burada acar, cekirdek
 * /core reload sirasinda hepsini tek seferde diskten tazeler ve alanlari yeniden baglar.
 */
public final class ConfigService {

    private final File dataFolder;
    private final Map<String, ConfigFile> files = new LinkedHashMap<>();
    private final Map<String, Object> holders = new LinkedHashMap<>();
    private java.util.logging.Logger logger;

    public ConfigService(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * name: "config.yml" ya da "modules/economy.yml". holder: @ConfigValue tasiyan nesne
     * (null olabilir). Dosya yoksa holder'in varsayilanlari yazilarak olusturulur.
     */
    public ConfigFile open(String name, int schemaVersion, Object holder, ConfigMigration migration) {
        ConfigFile config = new ConfigFile(new File(dataFolder, name), schemaVersion);
        if (config.needsMigration()) config.migrate(migration);
        if (holder != null) warnOnDuplicate(name, holder);

        if (holder != null) {
            ConfigMapper.writeDefaults(holder, config.yaml());
            config.save();
            ConfigMapper.apply(holder, config.yaml());
            holders.put(name, holder);
        } else if (!config.file().exists()) {
            config.save();
        }
        files.put(name, config);
        return config;
    }

    /**
     * Zaten acik bir dosyaya ikinci bir holder baglar. Ayni dosyayi open() ile
     * tekrar acmak onceki holder baglantisini duşurur; moduller cekirdek config'inin
     * bir bolumunu okumak istediginde bu metot kullanilir.
     */
    public void bind(String name, Object holder) {
        ConfigFile config = get(name);
        ConfigMapper.writeDefaults(holder, config.yaml());
        config.save();
        ConfigMapper.apply(holder, config.yaml());
        holders.put(name + "#" + holder.getClass().getSimpleName(), holder);
    }

    public ConfigFile get(String name) {
        ConfigFile file = files.get(name);
        if (file == null) throw new IllegalStateException("Config acilmamis: " + name);
        return file;
    }

    /** Diskten tazeler ve bagli holder alanlarini yeniden doldurur. */
    public void reloadAll() {
        files.forEach((name, config) -> config.reload());
        holders.forEach((key, holder) -> {
            String fileName = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
            ConfigFile config = files.get(fileName);
            if (config != null) ConfigMapper.apply(holder, config.yaml());
        });
    }

    public File dataFolder() {
        return dataFolder;
    }

    /**
     * Bir modul ayarini config.yml'de de bulunan bir yola baglarsa uyarir.
     *
     * Bu, teshisi en zor hata siniflarindan biri: kullanici config.yml'yi
     * duzenler, hicbir sey degismez ve hata mesaji da olmaz. Ayni yolun iki
     * dosyada bulunmasi her zaman bir tasarim hatasidir; erken soylemek gerekir.
     */
    private void warnOnDuplicate(String name, Object holder) {
        if (name.equals("config.yml") || logger == null) return;
        ConfigFile core = files.get("config.yml");
        if (core == null) return;

        for (String path : ConfigMapper.paths(holder)) {
            if (!core.yaml().contains(path)) continue;
            logger.warning("Ayar cakismasi: '" + path + "' hem config.yml hem "
                    + name + " icinde tanimli. Okunan dosya: " + name
                    + " — config.yml'deki deger YOKSAYILIYOR.");
        }
    }

    /** Uyarilar icin gunluk; cekirdek kurulurken baglanir. */
    public void logger(java.util.logging.Logger logger) {
        this.logger = logger;
    }
}
