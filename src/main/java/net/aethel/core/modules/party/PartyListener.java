package net.aethel.core.modules.party;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Parti uyelerinin giris/cikisinda waypoint isaretlerini tazeler; cikan uyenin
 * isareti diger uyelerde asili kalmaz.
 */
final class PartyListener implements Listener {

    private final PartyModule party;

    PartyListener(PartyModule party) {
        this.party = party;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        party.partyOf(event.getPlayer().getUniqueId()).ifPresent(found ->
                found.members().forEach(member -> {
                    var online = party.context().plugin().getServer().getPlayer(member);
                    if (online != null) party.trackMembers(online, party.isTracking(member));
                }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        party.partyOf(event.getPlayer().getUniqueId()).ifPresent(found ->
                found.members().forEach(member -> {
                    var online = party.context().plugin().getServer().getPlayer(member);
                    if (online != null && !member.equals(event.getPlayer().getUniqueId())) {
                        party.trackMembers(online, party.isTracking(member));
                    }
                }));
    }
}
