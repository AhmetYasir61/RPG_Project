package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Giris ve kayit servisi. Panel modu GUI ise oyun ici (AuthMe benzeri) akis,
 * WEB ise tarayiciya yonlendirme kullanilir; is mantigi ikisinde de aynidir.
 */
public interface AuthService {

    /** Oyuncunun mevcut oturum durumu. */
    enum State {
        /** Hic kaydi yok; kayit olmasi gerekiyor. */
        UNREGISTERED,
        /** Kayitli ama bu oturumda henuz dogrulanmadi. */
        AWAITING_LOGIN,
        /** Dogrulandi, oynayabilir. */
        AUTHENTICATED
    }

    State state(UUID uuid);

    default boolean isAuthenticated(Player player) {
        return state(player.getUniqueId()) == State.AUTHENTICATED;
    }

    /** Yeni kayit olusturur. PIN uzunlugu ve karmasikligi config'te tanimlidir. */
    CompletableFuture<Boolean> register(UUID uuid, String secret);

    /** Girisi dogrular; basarisiz denemeler sayilir ve sinira ulasan oyuncu atilir. */
    CompletableFuture<Boolean> login(UUID uuid, String secret);

    CompletableFuture<Boolean> changeSecret(UUID uuid, String oldSecret, String newSecret);

    /** Yetkili sifirlamasi; eski sifre gerekmez, denetim kaydina yazilir. */
    CompletableFuture<Void> adminReset(UUID uuid, String actor);

    /**
     * WEB modunda oyuncuya verilecek tek kullanimlik giris baglantisi. Token kisa
     * omurludur; tarayicida islem bitince oyuncu oyunda otomatik dogrulanir.
     */
    String webLoginUrl(Player player);
}
