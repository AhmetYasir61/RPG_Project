package net.aethel.core.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Paket tabanli hologram. Sunucuda entity olusturmaz; her satir yalnizca ilgili
 * oyuncunun istemcisinde cizilir, bu yuzden per-player farkli metin gosterebilir.
 */
public interface Hologram {

    String id();

    Location location();

    void teleport(Location location);

    /** Tum izleyiciler icin ortak satirlar. */
    void lines(List<Component> lines);

    /** Yalnizca bir oyuncunun gordugu satirlar; placeholder cozumu kisiye ozel olur. */
    void lines(Player viewer, List<Component> lines);

    void show(Player player);

    void hide(Player player);

    /** Gorunurluk kosulu: false donen oyunculara hologram gonderilmez. */
    void visibilityRule(java.util.function.Predicate<Player> rule);

    void remove();
}
