package net.aethel.core.api;

import org.bukkit.entity.Player;

/**
 * Kendi placeholder motorumuz. PlaceholderAPI kuruluysa kopru iki yonlu calisir,
 * kurulu degilse sistem aynen isler; PAPI hicbir zaman zorunlu bagimlilik degildir.
 */
public interface PlaceholderService {

    /** Ornek: register("rpg", (player, key) -> ...) -> %aethel_rpg_level% */
    void register(String prefix, PlaceholderResolver resolver);

    void unregister(String prefix);

    /** Metindeki tum placeholder'lari cozer. PAPI varsa onun etiketleri de cozulur. */
    String apply(Player player, String text);

    /** Tek bir placeholder cozer; bulunamazsa fallback doner. */
    String value(Player player, String prefix, String key, String fallback);
}
