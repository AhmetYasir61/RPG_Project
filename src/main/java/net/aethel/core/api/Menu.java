package net.aethel.core.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Tiklanabilir bir oyun ici menu. Slot bazli kurulur, sayfalama destekler ve her
 * tiklama kendi isleyicisine gider; envanter olaylari modul kodunda gorunmez.
 */
public interface Menu {

    /** Tek bir slota item ve tiklama isleyicisi baglar. */
    Menu set(int slot, ItemStack item, Consumer<MenuClick> handler);

    /** Isleyicisiz dolgu item'i (cerceve, ayrac). */
    Menu set(int slot, ItemStack item);

    /** Bos slotlari verilen item ile doldurur. */
    Menu fill(ItemStack item);

    /**
     * Sayfalanacak icerik. Menu, verilen slot havuzuna sigacak kadarini gosterir ve
     * ileri/geri dugmelerini kendisi yonetir.
     */
    Menu paginate(List<MenuEntry> entries, int[] contentSlots, int previousSlot, int nextSlot);

    /** Menu kapandiginda calisir (oyuncu ESC'ye bastiginda da). */
    Menu onClose(Consumer<Player> handler);

    Menu title(Component title);

    void open(Player player);

    void refresh();

    /** Bir tiklama olayinin menu kodunda gorunen hali. */
    record MenuClick(Player player, int slot, boolean rightClick, boolean shiftClick) {}

    /** Sayfalanan liste ogesi. */
    record MenuEntry(ItemStack item, Consumer<MenuClick> handler) {}
}
