package net.aethel.core.modules.economy;

import net.aethel.core.api.EconomyService;
import net.aethel.core.api.ProfileService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.storage.Database;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Altin ekonomisi. Bakiyeler bellekte tutulur, degisiklikler asenkron yazilir;
 * Vault kuruluysa modul kendini ekonomi saglayicisi olarak kaydeder.
 */
@ModuleInfo(id = "economy", name = "Ekonomi", depends = {"profile"})
public final class EconomyModule implements Module, EconomyService {

    private final EconomySettings settings = new EconomySettings();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private Database database;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.database = ctx.database();
        ctx.config().open("modules/economy.yml", 1, settings, ConfigMigration.NONE);
        ctx.services().register(EconomyService.class, this, "economy");

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("economy.transfer", true, "Oyuncular arasi para transferi");
        features.declare("economy.transaction-log", true, "Islem gecmisi kaydi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.schema().migrate("economy", EconomySchema.MIGRATIONS);
        ctx.listener(new EconomyListener(this, ctx.services().get(ProfileService.class)));

        if (settings.registerVault && ctx.plugin().getServer().getPluginManager()
                .getPlugin("Vault") != null) {
            VaultBridge.register(ctx, this, settings);
        }
        ctx.plugin().getServer().getOnlinePlayers()
                .forEach(player -> load(player.getUniqueId()));

        // %aethel_economy_balance% gibi anahtarlar HUD ve scoreboard'da kullanilir.
        ctx.services().optional(net.aethel.core.api.PlaceholderService.class)
                .ifPresent(service -> service.register("economy", (player, key) -> {
                    if (player == null) return null;
                    return switch (key) {
                        case "balance" -> format(balance(player.getUniqueId()));
                        case "balance_raw" -> String.format("%.2f", balance(player.getUniqueId()));
                        case "currency" -> settings.name;
                        case "symbol" -> settings.symbol;
                        default -> null;
                    };
                }));
    }

    @Override
    public void onDisable(CoreContext ctx) {
        balances.clear();
    }

    /** Oyuncu girisinde bakiye yuklenir; kaydi yoksa baslangic bakiyesi verilir. */
    void load(UUID uuid) {
        database.queryOne("SELECT balance FROM core_balance WHERE uuid = ?",
                        stmt -> stmt.setString(1, uuid.toString()),
                        rs -> rs.getDouble("balance"))
                .thenAccept(value -> {
                    if (value == null) {
                        balances.put(uuid, settings.startingBalance);
                        write(uuid, settings.startingBalance, settings.startingBalance, "baslangic");
                    } else {
                        balances.put(uuid, value);
                    }
                });
    }

    void unload(UUID uuid) {
        balances.remove(uuid);
    }

    @Override
    public double balance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0D);
    }

    @Override
    public CompletableFuture<Double> balanceAsync(UUID uuid) {
        Double cached = balances.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return database.queryOne("SELECT balance FROM core_balance WHERE uuid = ?",
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> rs.getDouble("balance")).thenApply(value -> value == null ? 0.0D : value);
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID uuid, double amount, String reason) {
        if (amount <= 0) return CompletableFuture.completedFuture(false);
        return balanceAsync(uuid).thenApply(current -> {
            if (current < amount) return false;
            double next = current - amount;
            balances.put(uuid, next);
            write(uuid, -amount, next, reason);
            return true;
        });
    }

    @Override
    public CompletableFuture<Void> deposit(UUID uuid, double amount, String reason) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);
        return balanceAsync(uuid).thenAccept(current -> {
            double next = Math.min(settings.maxBalance, current + amount);
            balances.put(uuid, next);
            write(uuid, amount, next, reason);
        });
    }

    /**
     * Transfer once dusme sonra ekleme olarak yapilir: dusme basarisizsa ekleme hic
     * calismaz, boylece havadan para uretilmesi mumkun olmaz.
     */
    @Override
    public CompletableFuture<Boolean> transfer(UUID from, UUID to, double amount) {
        if (!ctx.feature("economy.transfer")) return CompletableFuture.completedFuture(false);
        return withdraw(from, amount, "transfer:" + to).thenCompose(ok -> {
            if (!ok) return CompletableFuture.completedFuture(false);
            return deposit(to, amount, "transfer:" + from).thenApply(ignored -> true);
        });
    }

    @Override
    public CompletableFuture<List<TopEntry>> top(int limit) {
        return database.query(
                "SELECT b.uuid AS uuid, p.name AS name, b.balance AS balance"
                        + " FROM core_balance b LEFT JOIN core_profile p ON p.uuid = b.uuid"
                        + " ORDER BY b.balance DESC LIMIT " + Math.max(1, Math.min(100, limit)),
                Database.StatementBinder.NONE,
                rs -> new TopEntry(UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"), rs.getDouble("balance")));
    }

    @Override
    public String format(double amount) {
        return String.format(Locale.forLanguageTag("tr"),
                "%,." + settings.decimals + "f%s", amount, settings.symbol);
    }

    /** Bakiye yazimi ve (acikse) islem gecmisi kaydi tek yerden gecer. */
    private void write(UUID uuid, double delta, double balanceAfter, String reason) {
        String upsert = database.dialect() == net.aethel.core.storage.SqlDialect.MYSQL
                ? "INSERT INTO core_balance (uuid, balance) VALUES (?, ?)"
                        + " ON DUPLICATE KEY UPDATE balance = ?"
                : "INSERT OR REPLACE INTO core_balance (uuid, balance) VALUES (?, ?)";
        database.update(upsert, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, balanceAfter);
            if (database.dialect() == net.aethel.core.storage.SqlDialect.MYSQL) {
                stmt.setDouble(3, balanceAfter);
            }
        });
        if (!settings.keepTransactionLog || !ctx.feature("economy.transaction-log")) return;
        database.update("INSERT INTO core_transaction (uuid, amount, balance_after, reason, created_at)"
                + " VALUES (?, ?, ?, ?, ?)", stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, delta);
            stmt.setDouble(3, balanceAfter);
            stmt.setString(4, reason);
            stmt.setLong(5, System.currentTimeMillis());
        });
    }

    EconomySettings settings() {
        return settings;
    }
}
