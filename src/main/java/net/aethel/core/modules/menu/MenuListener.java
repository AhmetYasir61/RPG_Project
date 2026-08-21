package net.aethel.core.modules.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;

/**
 * Menu ve anvil envanterlerinin olay koprusu. Menu envanterlerinde her tiklama
 * iptal edilir: item alinip verilmesi yalnizca isleyicinin kararidir.
 */
final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var holder = event.getInventory().getHolder();

        if (holder instanceof SimpleMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) return;
            menu.handleClick(player, event.getSlot(),
                    event.isRightClick(), event.isShiftClick());
            return;
        }
        if (holder instanceof AnvilInput anvil) {
            event.setCancelled(true);
            if (event.getSlot() == AnvilInput.RESULT_SLOT) anvil.submit(player);
        }
    }

    /** Surukleme, menu envanterine item sokmanin ikinci yoludur; o da kapatilir. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        var holder = event.getInventory().getHolder();
        if (holder instanceof SimpleMenu || holder instanceof AnvilInput) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getInventory().getHolder() instanceof AnvilInput anvil) {
            anvil.current(event.getView().getRenameText());
            // Sonuc slotu bos kalirsa oyuncu onaylayamaz; girdiyi aynen kopyalariz.
            event.setResult(event.getInventory().getItem(0));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof SimpleMenu menu) {
            menu.handleClose(player);
        }
    }
}
