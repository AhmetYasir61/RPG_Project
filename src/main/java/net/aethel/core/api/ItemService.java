package net.aethel.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * Custom item uretimi ve cozumu. Kimlik PDC icinde kalici tutulur; item ne kadar
 * degisirse degissin, hangi tanimdan geldigi her zaman bulunabilir.
 */
public interface ItemService {

    /** Tanimdan yeni bir ItemStack uretir. Bilinmeyen id icin bos doner. */
    Optional<ItemStack> create(String fullId);

    Optional<ItemStack> create(String fullId, int amount);

    /** Elindeki item'in hangi tanimdan geldigini cozer. */
    Optional<CustomItem> resolve(ItemStack stack);

    boolean isCustom(ItemStack stack);

    Optional<CustomItem> definition(String fullId);

    Collection<CustomItem> all();

    /** Tanimlari diskten yeniden okur (panel "yeniden yukle" dugmesi). */
    int reload();
}
