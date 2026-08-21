package net.aethel.core.modules.economy;

import net.aethel.core.api.EconomyService;
import net.aethel.core.bootstrap.CoreContext;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.util.List;
import java.util.UUID;

/**
 * Vault ekonomi saglayicisi. Vault kurulu degilse bu sinif hic yuklenmez; bu yuzden
 * cekirdek Vault'a zorunlu bagimli olmadan onunla tam uyumlu calisir.
 */
final class VaultBridge extends AbstractEconomy {

    private final EconomyService economy;
    private final EconomySettings settings;

    private VaultBridge(EconomyService economy, EconomySettings settings) {
        this.economy = economy;
        this.settings = settings;
    }

    static void register(CoreContext ctx, EconomyService economy, EconomySettings settings) {
        VaultBridge bridge = new VaultBridge(economy, settings);
        ctx.plugin().getServer().getServicesManager().register(
                net.milkbowl.vault.economy.Economy.class, bridge, ctx.plugin(), ServicePriority.Highest);
        ctx.logger().info("Vault ekonomi saglayicisi olarak kaydedildik.");
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "AethelCore"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return settings.decimals; }
    @Override public String format(double amount) { return economy.format(amount); }
    @Override public String currencyNamePlural() { return settings.name; }
    @Override public String currencyNameSingular() { return settings.name; }

    @Override public boolean hasAccount(String playerName) { return true; }
    @Override public boolean hasAccount(String playerName, String worldName) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return true; }

    @Override public double getBalance(String playerName) { return balanceOf(playerName); }
    @Override public double getBalance(String playerName, String world) { return balanceOf(playerName); }
    @Override public double getBalance(OfflinePlayer player) { return economy.balance(player.getUniqueId()); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(String playerName, double amount) { return balanceOf(playerName) >= amount; }
    @Override public boolean has(String playerName, String world, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }

    /**
     * Vault senkron bir cevap bekler; bu yuzden asenkron cagriyi burada tamamlariz.
     * Cagri zaten bellek onbellegi uzerinden dondugu icin pratikte bloklama olmaz.
     */
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        boolean ok = economy.withdraw(player.getUniqueId(), amount, "vault").join();
        return new EconomyResponse(amount, economy.balance(player.getUniqueId()),
                ok ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                ok ? null : "Yetersiz bakiye");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        economy.deposit(player.getUniqueId(), amount, "vault").join();
        return new EconomyResponse(amount, economy.balance(player.getUniqueId()),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(offline(playerName), amount);
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        return withdrawPlayer(playerName, amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(offline(playerName), amount);
    }
    @Override public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        return depositPlayer(playerName, amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String world) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return true; }

    // Banka destegi kapali: MMORPG ekonomisinde banka mantigini kendi modulumuz tasiyor.
    @Override public EconomyResponse createBank(String name, String player) { return unsupported(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse deleteBank(String name) { return unsupported(); }
    @Override public EconomyResponse bankBalance(String name) { return unsupported(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return unsupported(); }
    @Override public List<String> getBanks() { return List.of(); }

    private EconomyResponse unsupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "Banka destegi kapali");
    }

    private OfflinePlayer offline(String playerName) {
        return org.bukkit.Bukkit.getOfflinePlayer(playerName);
    }

    private double balanceOf(String playerName) {
        UUID uuid = offline(playerName).getUniqueId();
        return economy.balance(uuid);
    }
}
