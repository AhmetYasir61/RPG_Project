package net.aethel.core.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Oyun ici para (altin) servisi. Vault kuruluysa cekirdek kendini saglayici olarak
 * kaydeder; marka parasi PCoins bu servise dahil DEGILDIR (bkz. PCoinService).
 */
public interface EconomyService {

    /** Cevrimici oyuncular icin onbellekten anlik okuma. */
    double balance(UUID uuid);

    CompletableFuture<Double> balanceAsync(UUID uuid);

    /** Bakiye yeterliyse duser ve true doner; yetersizse hicbir sey yapmaz. */
    CompletableFuture<Boolean> withdraw(UUID uuid, double amount, String reason);

    CompletableFuture<Void> deposit(UUID uuid, double amount, String reason);

    /** Iki oyuncu arasi transfer; tek islemde yapilir, yarim kalmaz. */
    CompletableFuture<Boolean> transfer(UUID from, UUID to, double amount);

    CompletableFuture<java.util.List<TopEntry>> top(int limit);

    /** Para bicimlendirme; simge ve ondalik ayari config'ten gelir. */
    String format(double amount);

    /** Siralama tablosu satiri. */
    record TopEntry(UUID uuid, String name, double balance) {}
}
