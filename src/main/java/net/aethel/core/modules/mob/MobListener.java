package net.aethel.core.modules.mob;

import net.aethel.core.api.LootService;
import net.aethel.core.api.SkillDefinition;
import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Mob tetikleyicilerini oyun olaylarina baglar ve ozel loot tablosunu uygular.
 * Custom mob'un vanilla dropu temizlenir; loot tamamen tablodan gelir.
 */
final class MobListener implements Listener {

    private final MobModule mobs;
    private final CoreContext ctx;

    MobListener(MobModule mobs, CoreContext ctx) {
        this.mobs = mobs;
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity victim) {
            mobs.resolve(victim).ifPresent(definition ->
                    mobs.trigger(victim, definition, SkillDefinition.Trigger.ON_DAMAGED));
        }
        if (event.getDamager() instanceof LivingEntity attacker) {
            mobs.resolve(attacker).ifPresent(definition ->
                    mobs.trigger(attacker, definition, SkillDefinition.Trigger.ON_DEAL_DAMAGE));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        mobs.resolve(entity).ifPresent(definition -> {
            mobs.trigger(entity, definition, SkillDefinition.Trigger.ON_DEATH);
            if (definition.lootTable() == null) return;

            // Vanilla drop temizlenir: custom mob'un odulu tamamen tablodan gelmeli.
            event.getDrops().clear();
            ctx.services().optional(LootService.class).ifPresent(loot ->
                    event.getDrops().addAll(
                            loot.roll(definition.lootTable(), entity.getLocation(), entity.getKiller())));
        });
    }
}
