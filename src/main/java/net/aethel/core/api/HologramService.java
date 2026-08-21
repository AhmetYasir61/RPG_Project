package net.aethel.core.api;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;

/**
 * Hologram modulunun disari actigi servis. Hologramlar kalicidir (diske yazilir)
 * ya da geciciddir (yalnizca bellek); ikisi de ayni arayuzu kullanir.
 */
public interface HologramService {

    /** Kalici hologram: sunucu yeniden baslasa da geri gelir. */
    Hologram create(String id, Location location);

    /** Gecici hologram: hasar sayisi, kisa bildirim gibi kullanimlar icin. */
    Hologram temporary(Location location, long ticks);

    Optional<Hologram> get(String id);

    Collection<Hologram> all();

    void remove(String id);
}
