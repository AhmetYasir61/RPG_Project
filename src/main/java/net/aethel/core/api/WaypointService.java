package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Parti uyesi takibi ve hedef gosterimi. Serbest teleport olmadigi icin bu servis
 * MMORPG akisinin merkezinde: oyuncu nereye gidecegini gorur, gitmeyi kendisi yapar.
 */
public interface WaypointService {

    /**
     * Parti uyesinin konumunu takip eden saydam kafa isareti olusturur. Isaret,
     * hedef oyuncu hareket ettikce guncellenir ve mesafeyi gosterir.
     */
    WaypointMarker trackPlayer(Player viewer, Player target);

    /** Sabit bir konumu isaretler (gorev hedefi, kesfedilmis waypoint). */
    WaypointMarker trackLocation(Player viewer, Location target, String label);

    List<WaypointMarker> markersOf(Player viewer);

    void clear(Player viewer);
}
