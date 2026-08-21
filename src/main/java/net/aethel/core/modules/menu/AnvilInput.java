package net.aethel.core.modules.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Anvil uzerinden metin girisi. Chat kullanilmaz: oyuncu menuden hic cikmaz, yazdigi
 * metin kimseye gorunmez ve akis kesilmez (bkz. docs/DESIGN.md, tamamen menu tabanli).
 */
public final class AnvilInput implements InventoryHolder {

    /** Anvil'in sonuc slotu; oyuncu buraya tiklayinca giris onaylanir. */
    static final int RESULT_SLOT = 2;

    private final Consumer<String> callback;
    private final Inventory inventory;
    private String current;

    AnvilInput(Component title, String initial, Consumer<String> callback) {
        this.callback = callback;
        this.current = initial;
        this.inventory = Bukkit.createInventory(this, InventoryType.ANVIL, title);

        // Sol slota konan item'in adi, oyuncunun duzenledigi metindir.
        ItemStack paper = new ItemStack(Material.PAPER);
        paper.editMeta(meta -> meta.displayName(Component.text(initial)));
        inventory.setItem(0, paper);
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    /** PrepareAnvilEvent her tus vurusunda gelir; guncel metni burada saklariz. */
    void current(String text) {
        if (text != null) this.current = text;
    }

    String current() {
        return current;
    }

    void submit(Player player) {
        player.closeInventory();
        callback.accept(current);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
