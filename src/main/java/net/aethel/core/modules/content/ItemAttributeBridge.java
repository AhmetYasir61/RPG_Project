package net.aethel.core.modules.content;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Nitelik eslesmesini icerik modulu DISINDAN kullanilabilir kilar.
 *
 * Soket modulu tasin statlarini ayni kurallarla uygulamali; eslesmeyi ikinci kez
 * yazmak, iki listenin zamanla ayrisip "ayni ad iki yerde farkli sey yapiyor"
 * durumuna dusmesi demektir.
 */
public final class ItemAttributeBridge {

    private ItemAttributeBridge() {}

    public static void apply(Plugin plugin, ItemMeta meta,
                             Map<String, Double> attributes, String suffix) {
        new ItemAttributes(plugin).apply(meta, attributes, suffix);
    }
}
