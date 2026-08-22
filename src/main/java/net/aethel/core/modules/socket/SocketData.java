package net.aethel.core.modules.socket;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Soket durumunun item uzerindeki kalici kaydi.
 *
 * Veri ITEM'DA durur, oyuncuda degil: kilic el degistirdiginde ilerlemesi de
 * onunla gider. Sandiga konup alinan bir kilicin asamasini kaybetmesi, oyuncunun
 * emegini gorunmez bir yerde silmek olurdu.
 */
final class SocketData {

    private final NamespacedKey stoneKey;
    private final NamespacedKey killsKey;

    SocketData(Plugin plugin) {
        this.stoneKey = new NamespacedKey(plugin, "socket_stone");
        this.killsKey = new NamespacedKey(plugin, "socket_kills");
    }

    Optional<String> stone(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer()
                .get(stoneKey, PersistentDataType.STRING));
    }

    int kills(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        Integer value = stack.getItemMeta().getPersistentDataContainer()
                .get(killsKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    void stone(ItemStack stack, String stoneId) {
        stack.editMeta(meta -> {
            if (stoneId == null) {
                meta.getPersistentDataContainer().remove(stoneKey);
                meta.getPersistentDataContainer().remove(killsKey);
            } else {
                meta.getPersistentDataContainer().set(stoneKey, PersistentDataType.STRING, stoneId);
                meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, 0);
            }
        });
    }

    void kills(ItemStack stack, int value) {
        stack.editMeta(meta -> meta.getPersistentDataContainer()
                .set(killsKey, PersistentDataType.INTEGER, Math.max(0, value)));
    }
}
