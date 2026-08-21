package net.aethel.core.modules.loot;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loot sandiklarinin acilmasini yakalar. Sandik oyuncu basina soguma sayar; ayni
 * sandigi tekrar tekrar acip odul biriktirmek mumkun degildir.
 */
final class LootListener implements Listener {

    private final LootModule loot;
    private final CoreContext ctx;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    LootListener(LootModule loot, CoreContext ctx) {
        this.loot = loot;
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        var location = event.getClickedBlock().getLocation();
        loot.chestAt(location).ifPresent(chest -> {
            event.setCancelled(true);
            var player = event.getPlayer();
            String key = chest.id() + ":" + player.getUniqueId();
            long now = System.currentTimeMillis();
            Long until = cooldowns.get(key);

            if (until != null && until > now) {
                ctx.lang().send(player, "loot.cooldown",
                        LangService.of("seconds", (until - now) / 1000));
                return;
            }
            cooldowns.put(key, now + chest.respawnSeconds() * 1000L);

            double multiplier = loot.rarityMultiplier(location);
            var drops = loot.roll(chest.tableId(), location, player);
            drops.forEach(item -> player.getInventory().addItem(item)
                    .values().forEach(leftover ->
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover)));

            ctx.lang().send(player, "loot.opened",
                    LangService.of("count", drops.size()),
                    LangService.of("rarity", String.format("%.2f", multiplier)));
        });
    }

    /** Oyuncu cikinca sogumasi silinmez: sunucu yeniden baslayana kadar korunur. */
    void clear(UUID playerId) {
        cooldowns.keySet().removeIf(key -> key.endsWith(playerId.toString()));
    }
}
