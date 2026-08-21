package net.aethel.core.modules.rpg;

import net.aethel.core.api.StatType;
import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * RPG statlarini oyun mekaniklerine baglar: hasar formulu, can havuzu ve mob
 * oldurmeden XP kazanci. Vanilla degerleri ezilmez, uzerine eklenir.
 */
final class RpgListener implements Listener {

    private final RpgModule rpg;
    private final CoreContext ctx;
    private final RpgSettings settings;

    RpgListener(RpgModule rpg, CoreContext ctx, RpgSettings settings) {
        this.rpg = rpg;
        this.ctx = ctx;
        this.settings = settings;
    }

    /** Giriste can havuzu statlara gore ayarlanir. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ctx.scheduler().later("rpg", 20L, () -> applyHealth(event.getPlayer()));
    }

    private void applyHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        int vitality = rpg.effectiveStats(player.getUniqueId()).get(StatType.VITALITY);
        attribute.setBaseValue(20.0 + vitality * settings.healthPerVitality);
    }

    /**
     * Hasar formulu: vanilla hasarin UZERINE stat katkisi eklenir. Vanilla degeri
     * tamamen ezmek, buyu/ok/dusme gibi tum hasar turlerini yeniden yazmayi gerektirirdi.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        int strength = rpg.effectiveStats(attacker.getUniqueId()).get(StatType.STRENGTH);
        if (strength <= 0) return;
        event.setDamage(event.getDamage() + strength * settings.damagePerStrength);
    }

    /** Mob oldurmede XP; olduren oyuncunun seviyesine gore degil, mobun gucune gore. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        LivingEntity victim = event.getEntity();
        var attribute = victim.getAttribute(Attribute.MAX_HEALTH);
        double tier = attribute == null ? 1.0 : Math.max(1.0, attribute.getValue() / 20.0);
        rpg.addExperience(killer, settings.xpPerMobLevel * tier, "mob");
    }
}
