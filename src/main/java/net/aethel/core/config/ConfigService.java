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

    public ConfigFile get(String name) {
        ConfigFile file = files.get(name);
        if (file == null) throw new IllegalStateException("Config acilmamis: " + name);
        return file;
    }

    /** Diskten tazeler ve bagli holder alanlarini yeniden doldurur. */
    public void reloadAll() {
        files.forEach((name, config) -> {
            config.reload();
            Object holder = holders.get(name);
            if (holder != null) ConfigMapper.apply(holder, config.yaml());
        });
    }

    public File dataFolder() {
        return dataFolder;
    }
}
