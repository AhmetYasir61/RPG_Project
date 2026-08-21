package net.aethel.core.modules.region;

import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;

/**
 * Bolge bayraklarini oyun olaylarina uygular: insaat ve PvP korumasi.
 * Bayrak yoksa serbest davranir; koruma yalnizca acikca tanimlandiginda devrededir.
 */
final class RegionListener implements Listener {

    private final RegionModule regions;
    private final CoreContext ctx;

    RegionListener(RegionModule regions, CoreContext ctx) {
        this.regions = regions;
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!regions.testFlag(event.getBlock().getLocation(), "build", event.getPlayer())) {
            event.setCancelled(true);
            ctx.lang().send(event.getPlayer(), "region.no-build");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!regions.testFlag(event.getBlock().getLocation(), "build", event.getPlayer())) {
            event.setCancelled(true);
            ctx.lang().send(event.getPlayer(), "region.no-build");
        }
    }

    /** PvP kontrolu SALDIRGANIN degil, KURBANIN konumuna gore yapilir. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!regions.testFlag(victim.getLocation(), "pvp", attacker)) {
            event.setCancelled(true);
            ctx.lang().send(attacker, "region.no-pvp");
        }
    }
}
