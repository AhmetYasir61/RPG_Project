package net.aethel.core.modules.economy;

import net.aethel.core.api.ProfileService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Bakiyeyi oyuncu girisinde yukler, cikisinda bellekten dusurur. */
final class EconomyListener implements Listener {

    private final EconomyModule module;
    private final ProfileService profiles;

    EconomyListener(EconomyModule module, ProfileService profiles) {
        this.module = module;
        this.profiles = profiles;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.unload(event.getPlayer().getUniqueId());
    }
}
