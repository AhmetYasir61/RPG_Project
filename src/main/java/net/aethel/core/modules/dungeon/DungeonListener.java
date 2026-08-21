package net.aethel.core.modules.dungeon;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.modules.dungeon.instance.ActiveInstance;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

/**
 * Dungeon icindeki oyun kurallari: cekirdek kirma, cikis kapisi, insaat yasagi ve
 * cikista/olumde oyuncunun dogru yere birakilmasi.
 */
final class DungeonListener implements Listener {

    /** Cikis kapisi olarak kullanilan blok. */
    private static final Material EXIT_BLOCK = Material.END_GATEWAY;

    private final CoreContext ctx;
    private final DungeonModule module;

    DungeonListener(CoreContext ctx, DungeonModule module) {
        this.ctx = ctx;
        this.module = module;
    }

    /**
     * Cekirdek kirilinca cokme baslar. Cekirdek disindaki bloklar korunur: dungeon
     * bir yapi, bir insaat alani degil; oyuncularin duvar kirip kestirmeden gitmesi
     * tum yerlesim mantigini bozar.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ActiveInstance instance = instanceAt(event.getPlayer());
        if (instance == null) return;

        if (event.getBlock().getType() == DungeonPopulator.CORE_BLOCK
                && instance.core() != null
                && event.getBlock().getLocation().equals(instance.core())) {
            event.setDropItems(false);
            module.breakCore(instance.snapshot(), event.getPlayer());
            return;
        }
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            ctx.lang().send(event.getPlayer(), "dungeon.no-build");
        }
    }

    /** Cikis kapisina tiklayinca oyuncu disari birakilir; donus yoktur. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != EXIT_BLOCK) return;
        if (instanceAt(event.getPlayer()) == null) return;

        event.setCancelled(true);
        module.exit(event.getPlayer());
    }

    /**
     * Dungeon icinde olen oyuncu disariya dogar. Ornek cokerken olenler de buraya
     * duser; esyalari dunya ile birlikte silindigi icin geri alinamaz.
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ActiveInstance instance = instanceAt(event.getPlayer());
        if (instance == null) return;
        event.getDrops().clear();
        module.playerIndex().remove(event.getPlayer().getUniqueId());
        instance.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Dungeon dunyasi silinmis olabilir; dogus noktasi daima disarisi olur.
        if (!event.getRespawnLocation().getWorld().getName().startsWith("dungeon_")) return;
        event.setRespawnLocation(new DungeonLifecycle(ctx, module, module.settings())
                .findExitLocation());
    }

    /**
     * Baglantisi kopan oyuncu ornekten dusurulur ve cikis noktasina isaretlenir:
     * silinmis bir dunyaya geri girmeye calisirsa sunucu onu bosluga birakirdi.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ActiveInstance instance = instanceAt(event.getPlayer());
        if (instance == null) return;
        instance.removePlayer(event.getPlayer().getUniqueId());
        module.playerIndex().remove(event.getPlayer().getUniqueId());
    }

    private ActiveInstance instanceAt(Player player) {
        UUID instanceId = module.playerIndex().get(player.getUniqueId());
        if (instanceId == null) return null;
        ActiveInstance instance = module.activeInstances().get(instanceId);
        return instance != null && player.getWorld().equals(instance.world()) ? instance : null;
    }
}
