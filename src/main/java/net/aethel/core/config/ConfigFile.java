package net.aethel.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tek bir YAML dosyasinin disk temsili: yukleme, kaydetme, sema surumu ve migration.
 * ConfigService bunlarin kaydini tutar ve hot-reload'da hepsini tazeler.
 */
public final class ConfigFile {

    private static final String SCHEMA_KEY = "schema-version";

    private final File file;
    private final int expectedSchema;
    private YamlConfiguration yaml;

    public ConfigFile(File file, int expectedSchema) {
        this.file = file;
        this.expectedSchema = expectedSchema;
        reload();
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public File file() {
        return file;
    }

    public void reload() {
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public void save() {
        try {
            yaml.set(SCHEMA_KEY, expectedSchema);
            Files.writeString(file.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Config kaydedilemedi: " + file, e);
        }
    }

    /** Dosyadaki sema surumu; hic yoksa 0 (yeni dosya) doner. */
    public int schemaVersion() {
        return yaml.getInt(SCHEMA_KEY, 0);
    }

    public boolean needsMigration() {
        return schemaVersion() > 0 && schemaVersion() < expectedSchema;
    }

    /**
     * Eski surumu once .bak olarak yedekler, sonra migration'i uygular. Yedek almadan
     * migration yapmak, hatali bir donusum durumunda oyuncu verisi kadar degerli
     * yapilandirmayi geri donulmez sekilde bozar.
     */
    public void migrate(ConfigMigration migration) {
        int from = schemaVersion();
        if (from >= expectedSchema) return;
        backup(from);
        migration.migrate(yaml, from, expectedSchema);
        save();
    }

    private void backup(int fromVersion) {
        if (!file.exists()) return;
        File target = new File(file.getParentFile(), file.getName() + ".v" + fromVersion + ".bak");
        try {
            Files.copy(file.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Config yedegi alinamadi: " + file, e);
        }
    }
}
