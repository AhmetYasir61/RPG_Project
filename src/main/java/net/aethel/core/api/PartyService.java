package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parti servisi. Partinin en gorunur islevi waypoint paylasimi: uyeler birbirinin
 * konumunu saydam kafa isareti olarak gorur, ama yanina gitmek icin yol almalari gerekir.
 */
public interface PartyService {

    /** Bir parti; lider dagilma ve davet yetkisine sahiptir. */
    record Party(UUID id, UUID leader, List<UUID> members, boolean sharedLoot, double xpShare) {}

    Optional<Party> partyOf(UUID player);

    Party create(Player leader);

    boolean invite(Player inviter, Player target);

    boolean accept(Player player, UUID partyId);

    void leave(Player player);

    void disband(UUID partyId);

    /** Uyenin waypoint isaretlerini acar/kapatir. */
    void trackMembers(Player player, boolean enabled);

    boolean isTracking(UUID player);

    List<Party> parties();
}
