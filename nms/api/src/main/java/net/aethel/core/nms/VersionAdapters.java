package net.aethel.core.nms;

import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Calisma zamaninda sunucu surumune uyan VersionAdapter'i secer. Bulunamazsa bos
 * doner; cekirdek bu durumda NMS gerektiren modulleri kapatir, sunucuyu durdurmaz.
 */
public final class VersionAdapters {

    private VersionAdapters() {}

    /** Ornek: "1.21.11" -> net.aethel.core.nms.v1_21_11.Adapter */
    public static Optional<VersionAdapter> detect() {
        return detect(null);
    }

    /**
     * Hata sebebini logs'a yazan surum. LinkageError da yakalanir: adapter sinifi
     * jar'da olup ic NMS sinifi degistiyse ClassNotFoundException degil NoClassDefFoundError
     * gelir ve bu, cok daha zor tespit edilen bir hata olur.
     */
    public static Optional<VersionAdapter> detect(Logger log) {
        String version = Bukkit.getMinecraftVersion();
        String className = "net.aethel.core.nms.v" + version.replace('.', '_') + ".Adapter";
        try {
            Class<?> type = Class.forName(className, true, VersionAdapters.class.getClassLoader());
            return Optional.of((VersionAdapter) type.getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException e) {
            if (log != null) {
                log.warning("Bu surum icin NMS adapteri derlenmemis: " + version
                        + " (aranan sinif: " + className + ")");
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError e) {
            if (log != null) {
                log.warning("NMS adapteri yuklenemedi (" + version + "): "
                        + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            return Optional.empty();
        }
    }
}
