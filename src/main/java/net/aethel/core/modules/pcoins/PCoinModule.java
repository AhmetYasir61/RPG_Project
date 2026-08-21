package net.aethel.core.modules.pcoins;

import net.aethel.core.api.PCoinService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Marka parasi (PCoins) modulu. Backend kaynaktir, sunucu ayna tutar; harcama once
 * rezervasyon sonra onay seklinde ilerler ve satin almalar idempotent uygulanir.
 */
@ModuleInfo(id = "pcoins", name = "PCoins", depends = {"profile"})
public final class PCoinModule implements Module, PCoinService, Listener {

    private final PCoinSettings settings = new PCoinSettings();
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean backendOnline = new AtomicBoolean(false);
    private PCoinClient client;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/pcoins.yml", 1, settings, ConfigMigration.NONE);
        ctx.services().register(PCoinService.class, this, "pcoins");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        if (!settings.enabled) {
            ctx.logger().info("PCoins kapali; marka parasi devre disi.");
            return;
        }
        String apiKey = readApiKey();
        if (apiKey == null) {
            ctx.logger().warning("PCoins anahtar dosyasi bulunamadi: " + settings.apiKeyFile);
            return;
        }
        this.client = new PCoinClient(settings, apiKey, ctx.logger(), ctx.scheduler().ioExecutor());
        ctx.listener(this);

        int period = Math.max(15, settings.syncIntervalSeconds);
        ctx.scheduler().repeating("pcoins", 20L * 10, 20L * period, this::syncAll);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        cache.clear();
        backendOnline.set(false);
    }

    /** Oyuncu girer girmez bekleyen satin almalari uygularız; beklemesi gerekmez. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        refresh(uuid).thenRun(() -> claimPending(uuid));
    }

    private void syncAll() {
        ctx.plugin().getServer().getOnlinePlayers()
                .forEach(player -> refresh(player.getUniqueId())
                        .thenRun(() -> claimPending(player.getUniqueId())));
    }

    @Override
    public long cached(UUID uuid) {
        return cache.getOrDefault(uuid, 0L);
    }

    @Override
    public CompletableFuture<Long> refresh(UUID uuid) {
        if (client == null) return CompletableFuture.completedFuture(cached(uuid));
        return client.balance(uuid).thenApply(balance -> {
            backendOnline.set(balance >= 0);
            if (balance >= 0) cache.put(uuid, balance);
            return cached(uuid);
        });
    }

    /**
     * Harcama: rezervasyon -> odul teslimi cagiran tarafta -> commit. Rezervasyon
     * onaylanmadan hicbir sey verilmez; backend erisilemezse islem reddedilir.
     */
    @Override
    public CompletableFuture<SpendResult> spend(UUID uuid, long amount, String sku, String reason) {
        if (client == null || !backendOnline.get()) {
            return CompletableFuture.completedFuture(SpendResult.BACKEND_OFFLINE);
        }
        if (cached(uuid) < amount) {
            return CompletableFuture.completedFuture(SpendResult.INSUFFICIENT_FUNDS);
        }
        return client.reserve(uuid, amount, sku, reason).thenCompose(reservation -> {
            if (reservation == null || !reservation.has("reservation")) {
                return CompletableFuture.completedFuture(SpendResult.REJECTED);
            }
            String id = reservation.get("reservation").getAsString();
            return client.commit(id).thenApply(result -> {
                if (result == null) {
                    client.cancel(id);
                    return SpendResult.REJECTED;
                }
                cache.merge(uuid, -amount, Long::sum);
                return SpendResult.OK;
            });
        });
    }

    /**
     * Bekleyen satin almalar islem id'si ile uygulanir ve backend'e onaylanir.
     * Ayni islem iki kez gelse bile onay verilmis olan tekrar islenmez.
     */
    @Override
    public CompletableFuture<Integer> claimPending(UUID uuid) {
        if (client == null) return CompletableFuture.completedFuture(0);
        return client.pending(uuid).thenApply(response -> {
            if (response == null || !response.has("transactions")) return 0;
            var transactions = response.getAsJsonArray("transactions");
            int applied = 0;
            for (var element : transactions) {
                var transaction = element.getAsJsonObject();
                String id = transaction.get("id").getAsString();
                long amount = transaction.get("amount").getAsLong();
                cache.merge(uuid, amount, Long::sum);
                client.acknowledge(uuid, id);
                applied++;
                notifyPlayer(uuid, amount);
            }
            return applied;
        });
    }

    private void notifyPlayer(UUID uuid, long amount) {
        ctx.scheduler().sync("pcoins", () -> {
            var player = ctx.plugin().getServer().getPlayer(uuid);
            if (player == null) return;
            ctx.lang().send(player, "pcoins.received",
                    net.aethel.core.i18n.LangService.of("amount", amount),
                    net.aethel.core.i18n.LangService.of("currency", settings.displayName));
        });
    }

    @Override
    public boolean isOnline() {
        return backendOnline.get();
    }

    /** Anahtar dosyadan okunur ve hicbir zaman loglanmaz. */
    private String readApiKey() {
        Path path = new java.io.File(ctx.config().dataFolder(), settings.apiKeyFile).toPath();
        try {
            return Files.exists(path)
                    ? Files.readString(path, StandardCharsets.UTF_8).trim()
                    : null;
        } catch (IOException e) {
            ctx.logger().warning("PCoins anahtari okunamadi.");
            return null;
        }
    }
}
