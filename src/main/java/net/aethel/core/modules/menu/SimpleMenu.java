package net.aethel.core.modules.menu;

import net.aethel.core.api.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu arayuzunun sandik envanteri ile implementasyonu. InventoryHolder olarak kendini
 * verir; dinleyici bu sayede menuyu tek adimda bulur, harici bir kayit tablosu gerekmez.
 */
public final class SimpleMenu implements Menu, InventoryHolder {

    private final Map<Integer, Consumer<MenuClick>> handlers = new HashMap<>();
    private final int rows;
    private Component title;
    private Inventory inventory;
    private Consumer<Player> closeHandler = player -> {};

    private List<MenuEntry> entries = List.of();
    private int[] contentSlots = new int[0];
    private int previousSlot = -1;
    private int nextSlot = -1;
    private int page;

    SimpleMenu(Component title, int rows) {
        this.title = title;
        this.rows = Math.max(1, Math.min(6, rows));
        this.inventory = Bukkit.createInventory(this, this.rows * 9, title);
    }

    @Override
    public Menu set(int slot, ItemStack item, Consumer<MenuClick> handler) {
        inventory.setItem(slot, item);
        if (handler != null) handlers.put(slot, handler);
        return this;
    }

    @Override
    public Menu set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    @Override
    public Menu fill(ItemStack item) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, item);
        }
        return this;
    }

    @Override
    public Menu paginate(List<MenuEntry> entries, int[] contentSlots, int previousSlot, int nextSlot) {
        this.entries = List.copyOf(entries);
        this.contentSlots = contentSlots.clone();
        this.previousSlot = previousSlot;
        this.nextSlot = nextSlot;
        this.page = 0;
        renderPage();
        return this;
    }

    @Override
    public Menu onClose(Consumer<Player> handler) {
        this.closeHandler = handler == null ? player -> {} : handler;
        return this;
    }

    @Override
    public Menu title(Component title) {
        this.title = title;
        return this;
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void refresh() {
        if (!entries.isEmpty()) renderPage();
    }

    /**
     * Sayfa icerigi her cizimde slotlar temizlenerek yazilir; onceki sayfadan kalan
     * item'in tiklanabilir kalmasi, kullanicinin yanlis kaydi secmesine yol acardi.
     */
    private void renderPage() {
        for (int slot : contentSlots) {
            inventory.setItem(slot, null);
            handlers.remove(slot);
        }
        int start = page * contentSlots.length;
        for (int i = 0; i < contentSlots.length; i++) {
            int index = start + i;
            if (index >= entries.size()) break;
            MenuEntry entry = entries.get(index);
            set(contentSlots[i], entry.item(), entry.handler());
        }
        if (previousSlot >= 0) {
            handlers.put(previousSlot, click -> {
                if (page > 0) { page--; renderPage(); }
            });
        }
        if (nextSlot >= 0) {
            handlers.put(nextSlot, click -> {
                if ((page + 1) * contentSlots.length < entries.size()) { page++; renderPage(); }
            });
        }
    }

    void handleClick(Player player, int slot, boolean right, boolean shift) {
        Consumer<MenuClick> handler = handlers.get(slot);
        if (handler != null) handler.accept(new MenuClick(player, slot, right, shift));
    }

    void handleClose(Player player) {
        closeHandler.accept(player);
    }

    int totalPages() {
        return contentSlots.length == 0 ? 1
                : Math.max(1, (int) Math.ceil(entries.size() / (double) contentSlots.length));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    List<MenuEntry> entries() {
        return entries;
    }
}
