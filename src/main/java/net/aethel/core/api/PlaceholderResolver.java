package net.aethel.core.api;

import org.bukkit.entity.Player;

/**
 * Bir placeholder ailesinin cozucusu. Modul kendi on ekini kaydeder; cekirdek
 * %aethel_<prefix>_<key>% desenini ayristirip buraya yonlendirir.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * key: on ek sonrasi kalan kisim (orn. "level", "party_size").
     * player null olabilir (konsol baglami). Cozulemezse null donulur.
     */
    String resolve(Player player, String key);
}
