package net.aethel.core.modules.pcoins;

import net.aethel.core.config.ConfigValue;

/**
 * PCoins backend ayarlari. API anahtari BILEREK burada tutulmaz: config dosyalari
 * paylasilir ve ekran goruntusu alinir, anahtar ayri bir dosyada durur.
 */
public final class PCoinSettings {

    @ConfigValue("pcoins.enabled")
    public boolean enabled = false;

    @ConfigValue("pcoins.endpoint")
    public String endpoint = "https://api.pokewing.net/v1";

    /** Anahtar dosyasinin plugin klasorune gore yolu. */
    @ConfigValue("pcoins.api-key-file")
    public String apiKeyFile = "pcoins-key.txt";

    @ConfigValue("pcoins.sync-interval-seconds")
    public int syncIntervalSeconds = 60;

    /** QUEUE: backend kapaliyken kazanimlar kuyruklanir, harcama reddedilir. */
    @ConfigValue("pcoins.offline-mode")
    public String offlineMode = "QUEUE";

    @ConfigValue("pcoins.request-timeout-seconds")
    public int requestTimeoutSeconds = 10;

    @ConfigValue("pcoins.display-name")
    public String displayName = "PCoin";
}
