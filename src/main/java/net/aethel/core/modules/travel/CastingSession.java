package net.aethel.core.modules.travel;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Devam eden bir isinlanma okumasi. Hareket ve hasar bu nesne uzerinden izlenir;
 * iptal olursa parsomen harcanmaz, ceza yalnizca kaybedilen zamandir.
 */
final class CastingSession {

    /** Bu mesafeden fazla hareket okumayi bozar; hafif kaymalar tolere edilir. */
    private static final double MOVE_TOLERANCE = 0.6D;

    private final Player player;
    private final Location origin;
    private final Location destination;
    private final String waypointId;
    private final long finishAt;
    private final boolean consumesScroll;
    private boolean cancelled;

    CastingSession(Player player, Location destination, String waypointId,
                   double seconds, boolean consumesScroll) {
        this.player = player;
        this.origin = player.getLocation().clone();
        this.destination = destination;
        this.waypointId = waypointId;
        this.finishAt = System.currentTimeMillis() + (long) (seconds * 1000);
        this.consumesScroll = consumesScroll;
    }

    Player player() { return player; }
    Location destination() { return destination; }
    String waypointId() { return waypointId; }
    boolean consumesScroll() { return consumesScroll; }
    boolean cancelled() { return cancelled; }

    void cancel() {
        this.cancelled = true;
    }

    boolean finished() {
        return System.currentTimeMillis() >= finishAt;
    }

    /** Kalan sure, ilerleme cubugunu cizmek icin. */
    double remainingSeconds() {
        return Math.max(0, (finishAt - System.currentTimeMillis()) / 1000.0);
    }

    /** Oyuncu yerinden ayrildiysa okuma bozulur. */
    boolean moved() {
        return player.getLocation().distanceSquared(origin) > MOVE_TOLERANCE * MOVE_TOLERANCE;
    }
}
