package net.aethel.core.modules.dialog;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Diyalog girdisi. Chat ve envanter KULLANILMAZ: secim hotbar kaydirarak yapilir,
 * onay shift ile verilir. Boylece oyuncu diyalogdan hic cikmaz.
 */
final class DialogInput implements Listener {

    private final DialogModule dialogs;

    DialogInput(DialogModule dialogs) {
        this.dialogs = dialogs;
    }

    /**
     * Hotbar kaydirma secim yapar. Fare tekerlegi her istemcide vardir, ekstra tus
     * ogrenmeyi gerektirmez ve chat acmadan calisir — bu yuzden secim tasiyicisi olarak
     * kullaniliyor. Olay iptal edilir ki elindeki esya degismesin.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHotbar(PlayerItemHeldEvent event) {
        if (!dialogs.awaitingChoice(event.getPlayer())) return;
        event.setCancelled(true);

        int delta = slotDelta(event.getPreviousSlot(), event.getNewSlot());
        dialogs.moveSelection(event.getPlayer(), delta);
    }

    /**
     * Hotbar dairesel oldugu icin 8'den 0'a gecis "ileri", 0'dan 8'e gecis "geri"
     * demektir; duz cikarma bunu ters okur ve secim ters yone atlar.
     */
    private int slotDelta(int previous, int next) {
        int raw = next - previous;
        if (raw > 4) return raw - 9;
        if (raw < -4) return raw + 9;
        return raw;
    }

    /** Shift: akan metni atlar, tamamlanmissa secimi onaylar ya da devam eder. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!dialogs.inDialog(player)) return;
        dialogs.confirm(player);
    }

    /** Sag tik de onay sayilir; fareyle oynayan icin daha dogal. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!dialogs.inDialog(event.getPlayer())) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        dialogs.confirm(event.getPlayer());
    }

    /** Diyalog sirasinda envanter acilmaz: kutu ekranin ortasinda kalir. */
    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && dialogs.inDialog(player)) {
            event.setCancelled(true);
        }
    }

    /** Q ile esya dusurmek diyalogu bozar; engellenir. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (dialogs.inDialog(event.getPlayer())) event.setCancelled(true);
    }

    /** F ile el degistirme de secim akisini bozar. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (dialogs.inDialog(event.getPlayer())) event.setCancelled(true);
    }
}
