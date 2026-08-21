package net.aethel.core.nms;

import org.bukkit.Bukkit;

import java.util.Optional;

/**
 * Calisma zamaninda sunucu surumune uyan VersionAdapter'i secer. Bulunamazsa bos
 * doner; cekirdek bu durumda NMS gerektiren modulleri kapatir, sunucuyu durdurmaz.
 */
public final class VersionAdapters {

    private VersionAdapters() {}

    /** Ornek: "1.21.11" -> net.aethel.core.nms.v1_21_11.Adapter */
    public static Optional<VersionAdapter> detect() {
        String version = Bukkit.getMinecraftVersion();
        String className = "net.aethel.core.nms.v" + version.replace('.', '_') + ".Adapter";
        try {
            Class<?> type = Class.forName(className);
            return Optional.of((VersionAdapter) type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }
}
