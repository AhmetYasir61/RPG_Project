package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Loot sandigi servisi. Nadirlik sabit degildir: sandigin bulundugu bolgenin ANLIK
 * tehdit yogunlugundan hesaplanir, boylece tehlike gercek bir odule donusur.
 */
public interface LootService {

    /** Tek bir loot tablosu girdisi. */
    record LootEntry(String itemId, int minAmount, int maxAmount, double weight,
                     int minRarityTier) {}

    /** Bir sandik tanimi. */
    record LootChest(String id, Location location, String tableId, long respawnSeconds) {}

    /**
     * Bolgedeki tehdit yogunlugu. Cok sayida zayif mob ile az sayida guclu mob ayni
     * puana cikabilir; ikisi de nadirligi yukseltir.
     */
    double threatAt(Location location);

    /** Tehdit ve zorluktan hesaplanan nadirlik carpani. */
    double rarityMultiplier(Location location);

    /** Sandigi acar ve icerigini o anki tehdide gore uretir. */
    List<ItemStack> roll(String tableId, Location location, Player opener);

    void registerChest(LootChest chest);

    void removeChest(String id);

    List<LootChest> chests();
}
