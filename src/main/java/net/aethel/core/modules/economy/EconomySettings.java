package net.aethel.core.modules.economy;

import net.aethel.core.config.ConfigValue;

/** Altin ekonomisi ayarlari; bicimlendirme ve baslangic bakiyesi buradan gelir. */
public final class EconomySettings {

    @ConfigValue("economy.currency-symbol")
    public String symbol = "◎";

    @ConfigValue("economy.currency-name")
    public String name = "Altin";

    @ConfigValue("economy.starting-balance")
    public double startingBalance = 250.0D;

    @ConfigValue("economy.max-balance")
    public double maxBalance = 1_000_000_000.0D;

    @ConfigValue("economy.decimals")
    public int decimals = 2;

    /** Vault kuruluysa cekirdegi ekonomi saglayicisi olarak kaydeder. */
    @ConfigValue("economy.register-vault")
    public boolean registerVault = true;

    /** Islem gecmisi tutulsun mu; denetim icin onerilir, disk maliyeti dusuktur. */
    @ConfigValue("economy.keep-transaction-log")
    public boolean keepTransactionLog = true;
}
