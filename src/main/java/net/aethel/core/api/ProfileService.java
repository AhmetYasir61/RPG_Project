package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Oyuncu profillerinin merkezi erisimi. Cevrimici oyuncular icin bellek onbelleginden
 * anlik doner; cevrimdisi sorgular veritabanindan asenkron gelir.
 */
public interface ProfileService {

    /** Cevrimici oyuncu icin anlik erisim; onbellekte yoksa bos doner. */
    Optional<PlayerProfile> cached(UUID uuid);

    default Optional<PlayerProfile> cached(Player player) {
        return cached(player.getUniqueId());
    }

    /** Cevrimdisi dahil; gerekiyorsa veritabanindan yukler. */
    CompletableFuture<Optional<PlayerProfile>> load(UUID uuid);

    /** Isimden arama (buyuk/kucuk harf duyarsiz). */
    CompletableFuture<Optional<PlayerProfile>> loadByName(String name);

    Collection<PlayerProfile> online();

    /** Profili hemen diske yazar. */
    CompletableFuture<Void> save(PlayerProfile profile);
}
