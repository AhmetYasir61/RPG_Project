package net.aethel.core.modules.socket;

import net.aethel.core.api.SocketStone;
import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/**
 * Tasin VURUSTA calisan etkileri: yanma, yavaslatma ve tasin parcacigi.
 *
 * Nitelikler (hasar, saldiri hizi) itemin uzerinde durur ve vurus anina gerek
 * duymaz; sure cinsinden etkiler ise ancak burada uygulanabilir. Ikisi ayri
 * yerlerde durur cunku farkli hayat dongulerine sahiptirler.
 */
final class StoneEffectListener implements Listener {

    /** Ayni tik icinde birden cok kez tetiklenmemesi icin en dusuk sure. */
    private static final int MIN_TICKS = 20;

    private final CoreContext ctx;
    private final SocketModule sockets;
    private final SocketData data;

    StoneEffectListener(CoreContext ctx, SocketModule sockets, SocketData data) {
        this.ctx = ctx;
        this.sockets = sockets;
        this.data = data;
    }

    /**
     * NORMAL onceligi: hasar hesabi bitmis olmali ama olay hala iptal
     * edilebilir olmali. Iptal edilmis bir vurusta etki uygulanmaz.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        var stoneId = data.stone(weapon);
        if (stoneId.isEmpty()) return;

        var stone = sockets.stone(stoneId.get());
        if (stone.isEmpty()) return;

        int stage = sockets.stage(weapon);
        applyTimed(victim, stone.get().attributes(), stage);
        spark(victim, stone.get(), stage);
    }

    /**
     * Sure cinsinden etkiler. Sure asamayla uzar: ilk asamada yarim, son
     * asamada tam -- ilerlemenin savasta gercek bir karsiligi olsun diye.
     */
    private void applyTimed(LivingEntity victim, Map<String, Double> attributes, int stage) {
        attributes.forEach((key, value) -> {
            if (value == null || value <= 0) return;
            int ticks = (int) Math.round(value * 20 * (0.5D + 0.15D * stage));
            if (ticks < MIN_TICKS) ticks = MIN_TICKS;

            switch (key.toLowerCase(Locale.ROOT)) {
                case "burn-seconds" -> victim.setFireTicks(Math.max(victim.getFireTicks(), ticks));
                case "slow-seconds" -> victim.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, ticks, Math.min(3, stage), true, true));
                case "weakness-seconds" -> victim.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, ticks, Math.min(2, stage), true, true));
                case "poison-seconds" -> victim.addPotionEffect(new PotionEffect(
                        PotionEffectType.POISON, ticks, Math.min(2, stage), true, true));
                default -> { /* nitelik olarak zaten uygulandi */ }
            }
        });
    }

    /**
     * Vurus parcacigi. Adet asamayla artar ama TAVANLIDIR: 100 oyunculu bir
     * sunucuda her vurusta yuzlerce parcacik, istemcileri gereksiz yorar.
     */
    private void spark(LivingEntity victim, SocketStone stone, int stage) {
        int count = Math.min(24, 4 + stage * 5);
        victim.getWorld().spawnParticle(particleOf(stone),
                victim.getLocation().add(0, victim.getHeight() * 0.6, 0),
                count, 0.3, 0.3, 0.3, 0.01);
    }

    private Particle particleOf(SocketStone stone) {
        try {
            return Particle.valueOf(stone.particle().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.CRIT;
        }
    }
}
