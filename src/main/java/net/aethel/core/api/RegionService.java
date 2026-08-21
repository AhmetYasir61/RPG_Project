package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Bolge servisi: sekil tabanli alanlar, bayraklar ve zorluk atamalari.
 * Loot ve mob modulleri bolgenin zorlugunu buradan okur.
 */
public interface RegionService {

    /** Bir bolge tanimi. */
    record Region(String id, String world, RegionShape shape, String difficultyId,
                  int priority, List<String> flags, List<String> owners) {}

    Optional<Region> region(String id);

    List<Region> regions();

    /** Konumu iceren bolgeler, oncelige gore sirali (en yuksek once). */
    List<Region> at(Location location);

    /** En yuksek oncelikli bolge; hicbiri yoksa bos. */
    Optional<Region> highestAt(Location location);

    /** Konumdaki etkin zorluk; bolge yoksa normal doner. */
    Difficulty difficultyAt(Location location);

    boolean testFlag(Location location, String flag, Player player);

    void save(Region region);

    void delete(String id);

    /** Zorluk tanimlari panelden yonetilir. */
    List<Difficulty> difficulties();

    Optional<Difficulty> difficulty(String id);

    void saveDifficulty(Difficulty difficulty);
}
