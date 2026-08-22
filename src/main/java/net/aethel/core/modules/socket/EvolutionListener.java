package net.aethel.core.modules.socket;

import net.aethel.core.api.CustomItem;
import net.aethel.core.api.ItemService;
import net.aethel.core.api.SocketStone;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * Oldurmeleri sayar ve asama yukselince silahi yeniler.
 *
 * Yalnizca EntityDeathEvent dinlenir; hicbir zamanlayici, hicbir oyuncu taramasi
 * yoktur. Ilerleme olay ANINDA islenir, boylece 100 oyunculu bir sunucuda bile
 * surekli calisan bir maliyet olusmaz.
 */
final class EvolutionListener implements Listener {

    private final CoreContext ctx;
    private final SocketModule sockets;
    private final SocketData data;

    EvolutionListener(CoreContext ctx, SocketModule sockets, SocketData data) {
        this.ctx = ctx;
        this.sockets = sockets;
        this.data = data;
    }

    /**
     * MONITOR: baska eklentiler olumu iptal edebilir ya da degistirebilir;
     * ilerleme yalnizca gercekten olmus bir canli icin sayilmalidir.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        Optional<String> stoneId = data.stone(weapon);
        if (stoneId.isEmpty()) return;

        Optional<CustomItem> found = sockets.definition(weapon).filter(CustomItem::socketable);
        if (found.isEmpty()) return;
        CustomItem item = found.get();

        int before = item.socketing().stageFor(data.kills(weapon));
        data.kills(weapon, data.kills(weapon) + 1);
        int after = item.socketing().stageFor(data.kills(weapon));

        if (after == before) {
            sockets.refresh(weapon);
            return;
        }
        sockets.refresh(weapon);
        announce(killer, item, stoneId.get(), after);

        // Son asamaya ulasan silah baska bir item'a DONUSUR.
        if (after >= item.socketing().lastStage() && item.socketing().evolves()) {
            evolve(killer, weapon, item, stoneId.get());
        }
    }

    /** Asama yukselisi: mesaj, ses ve tasin renginde bir parcacik patlamasi. */
    private void announce(Player player, CustomItem item, String stoneId, int stage) {
        ctx.lang().send(player, "socket.stage-up",
                LangService.of("stage", stage),
                LangService.of("last", item.socketing().lastStage()));

        sockets.stone(stoneId).ifPresent(stone -> {
            player.getWorld().spawnParticle(particleOf(stone),
                    player.getLocation().add(0, 1, 0), 20 * stage, 0.4, 0.6, 0.4, 0.02);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f + stage * 0.15f);
        });
    }

    /**
     * Evrim: yeni item TANIMDAN uretilir, eski silahin dayanikliligi ve adi
     * tasinmaz. Kilic artik baska bir silahtir; eskisinin hasarini tasimasi
     * beklenmez.
     */
    private void evolve(Player player, ItemStack weapon, CustomItem item, String stoneId) {
        String target = item.socketing().finalId();
        String fullId = target.contains(":") ? target : item.namespace() + ":" + target;

        ctx.services().optional(ItemService.class)
                .flatMap(items -> items.create(fullId, weapon.getAmount()))
                .ifPresentOrElse(evolved -> {
                    player.getInventory().setItemInMainHand(evolved);
                    ctx.lang().send(player, "socket.evolved",
                            LangService.of("item", fullId));
                    player.getWorld().spawnParticle(Particle.FLASH,
                            player.getLocation().add(0, 1, 0), 6);
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.ITEM_TRIDENT_THUNDER, 1f, 1.2f);
                }, () -> ctx.logger().warning("Evrim hedefi bulunamadi: " + fullId
                        + " (" + item.fullId() + " tanimindaki evolves-into)"));
    }

    /** Tas kendi parcacigini secer; bilinmeyen ad FLAME'e duser. */
    private Particle particleOf(SocketStone stone) {
        try {
            return Particle.valueOf(stone.particle().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.FLAME;
        }
    }
}
