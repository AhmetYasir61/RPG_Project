package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Seyahat servisi. Serbest teleport YOKTUR: isinlanmak icin parsomen harcanir ya da
 * hearthstone sogumasi beklenir; waypoint'e once yuruyerek gitmis olmak sarttir.
 */
public interface TravelService {

    /** Kesfedilmis bir seyahat noktasi. */
    record Waypoint(String id, String displayName, Location location, String icon,
                    double scrollCost, boolean requiresDiscovery) {}

    List<Waypoint> waypoints();

    Optional<Waypoint> waypoint(String id);

    /** Oyuncunun kesfettigi waypoint'ler; kesfetmedigine isinlanamaz. */
    CompletableFuture<List<String>> discovered(UUID player);

    CompletableFuture<Void> discover(UUID player, String waypointId);

    /**
     * Parsomen ile isinlanma baslatir. Cast suresi boyunca hasar alinirsa iptal olur
     * ve parsomen HARCANMAZ; ceza, harcanan zamandir.
     */
    CastResult beginScrollTeleport(Player player, String waypointId);

    /** Hearthstone: tek bag noktasi, uzun soguma, parsomen gerektirmez. */
    CastResult useHearthstone(Player player);

    /** Hearthstone bag noktasini bulunulan yere tasir. */
    boolean bindHearthstone(Player player);

    long hearthstoneCooldown(UUID player);

    /** Isinlanma denemesinin sonucu. */
    enum CastResult {
        STARTED,
        NOT_DISCOVERED,
        NO_SCROLL,
        ON_COOLDOWN,
        BLOCKED_REGION,
        IN_COMBAT
    }
}
