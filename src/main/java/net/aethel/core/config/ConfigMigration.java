package net.aethel.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Sema surumleri arasi donusum. Modul kendi migration'ini saglar; cekirdek
 * yedeklemeyi ve surum damgasini kendisi halleder.
 */
@FunctionalInterface
public interface ConfigMigration {

    void migrate(YamlConfiguration yaml, int fromVersion, int toVersion);

    /** Hicbir donusum gerekmeyen moduller icin. */
    ConfigMigration NONE = (yaml, from, to) -> {};
}
