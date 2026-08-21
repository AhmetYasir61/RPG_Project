package net.aethel.core.modules.travel;

import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Savas kilidi ve okuma iptali. Hasar alan oyuncunun isinlanma okumasi bozulur;
 * bu, kacisi imkansiz kilmadan MMORPG'deki "savastan kolayca kacamazsin" kuralini kurar.
 */
final class TravelListener implements Listener {

    private final TravelModule travel;
    private final CoreContext ctx;
    private final TravelSettings settings;

    TravelListener(TravelModule travel, CoreContext ctx, TravelSettings settings) {
        this.travel = travel;
        this.ctx = ctx;
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        travel.markCombat(player.getUniqueId());
        travel.cancelCast(player.getUniqueId());
    }

    /** Saldiran taraf da savas kilidine girer; vurup kacmak mumkun olmasin. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            travel.markCombat(attacker.getUniqueId());
            travel.cancelCast(attacker.getUniqueId());
        }
    }
}
