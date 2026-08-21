package net.aethel.core.modules.travel.waypoint;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyuncu kafasi uretimi ve onbellegi. Kafa olusturmak profil cozumu gerektirir;
 * her tick yeniden uretmek yerine oyuncu basina bir kez uretip saklariz.
 */
final class PlayerHeads {

    private static final Map<UUID, ItemStack> CACHE = new ConcurrentHashMap<>();
    private static final ItemStack FALLBACK = new ItemStack(Material.PLAYER_HEAD);

    private PlayerHeads() {}

    /** Oyuncu null ise (cevrimdisi ya da konum isareti) genel bir kafa doner. */
    static ItemStack of(Player player) {
        if (player == null) return FALLBACK;
        return CACHE.computeIfAbsent(player.getUniqueId(), id -> {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
                head.setItemMeta(meta);
            }
            return head;
        });
    }

    static void invalidate(UUID playerId) {
        CACHE.remove(playerId);
    }
}
