package net.aethel.core.modules.jobs;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.entity.Player;

/**
 * Meslek eylemlerini oyun olaylarina baglar. Yerlestirilen bloklarin kirilmasi
 * kazanc vermez; aksi halde blok koyup kirmak sonsuz para uretirdi.
 */
final class JobListener implements Listener {

    private final JobModule jobs;
    private final org.bukkit.plugin.Plugin plugin;

    JobListener(JobModule jobs, org.bukkit.plugin.Plugin plugin) {
        this.jobs = jobs;
        this.plugin = plugin;
    }

    /**
     * Oyuncunun kendi koydugu blok isaretlenir ve kirilinca odul verilmez.
     * Bu isaret Bukkit'in metadata'si yerine blogun kalici verisinde tutulamaz,
     * bu yuzden basit ve ucuz olan yol seciliyor: yerlestirmede metadata damgasi.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        event.getBlock().setMetadata("aethel_placed",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().hasMetadata("aethel_placed")) return;
        jobs.handleAction(event.getPlayer(), "BREAK", event.getBlock().getType().name());
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        jobs.handleAction(killer, "KILL", event.getEntity().getType().name());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        jobs.handleAction(player, "CRAFT", event.getRecipe().getResult().getType().name());
    }
}
