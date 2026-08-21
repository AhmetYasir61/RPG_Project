package net.aethel.core.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Optional;

/**
 * Ses adi cozumu. Sound.valueOf kaldirilmak uzere isaretlendigi icin registry
 * uzerinden cozeriz; hem enum adi ("BLOCK_ANVIL_USE") hem anahtar ("block.anvil.use") kabul edilir.
 */
public final class Sounds {

    private Sounds() {}

    public static Optional<Sound> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String normalized = raw.toLowerCase(Locale.ROOT).replace('_', '.');
        NamespacedKey key = NamespacedKey.fromString(
                normalized.contains(":") ? normalized : "minecraft:" + normalized);
        return key == null ? Optional.empty() : Optional.ofNullable(Registry.SOUNDS.get(key));
    }
}
