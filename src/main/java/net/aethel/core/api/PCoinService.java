package net.aethel.core.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PCoins (marka parasi) servisi. Kaynak merkezi backend'dir; sunucu yalnizca ayna
 * tutar. Vault'a baglanmaz — ucuncu parti bir plugin marka parasini harcayamasin diye.
 */
public interface PCoinService {

    /** Son senkronizasyondaki bakiye. Kesin deger icin refresh() cagrilir. */
    long cached(UUID uuid);

    /** Backend'den guncel bakiyeyi ceker. */
    CompletableFuture<Long> refresh(UUID uuid);

    /**
     * Harcama: once backend'de rezervasyon yapilir, onay gelince true doner.
     * Once item verip sonra dusmeye calismak, backend erisilemezse bedava item uretir.
     */
    CompletableFuture<SpendResult> spend(UUID uuid, long amount, String sku, String reason);

    /** Bekleyen satin almalari uygular; islem id'si ile idempotenttir. */
    CompletableFuture<Integer> claimPending(UUID uuid);

    boolean isOnline();

    /** Harcama sonucu; reddedilme sebebi kullaniciya dogru mesaji gostermek icin. */
    enum SpendResult {
        OK,
        INSUFFICIENT_FUNDS,
        BACKEND_OFFLINE,
        REJECTED
    }
}
