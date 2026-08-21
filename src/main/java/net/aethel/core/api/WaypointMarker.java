package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Dunyada gorunen bir hedef isareti (parti uyesi, gorev hedefi, kesfedilmis nokta).
 * Paket ile cizilir; oyuncuyu oraya isinlamaz, yalnizca yolu gosterir.
 */
public interface WaypointMarker {

    /** Isaretin sahibi olan oyuncu — isareti yalnizca o gorur. */
    Player viewer();

    Location target();

    void target(Location location);

    /** Isaret basligi; mesafe otomatik eklenir. */
    void label(String label);

    /** Uzaktayken bile gorunur mu (duvar arkasi parlama). */
    void glowThroughWalls(boolean glow);

    void remove();
}
