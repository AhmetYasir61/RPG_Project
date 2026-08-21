package net.aethel.core.bootstrap;

import net.aethel.core.config.ConfigValue;

/**
 * config.yml kok ayarlari. ConfigMapper tarafindan doldurulur ve /core reload
 * sirasinda ayni nesne uzerinde tazelenir.
 */
public final class CoreSettings {

    @ConfigValue(value = "language.default", comment = "Varsayilan dil kodu")
    public String defaultLanguage = "tr";

    /** false: herkese varsayilan dil gonderilir, istemci dili yoksayilir. */
    @ConfigValue("language.follow-client")
    public boolean followClient = true;

    @ConfigValue("language.available")
    public java.util.List<?> availableLanguages = java.util.List.of("tr", "en");

    @ConfigValue(value = "profile.autosave-seconds", comment = "Oyuncu verisi flush araligi")
    public int autosaveSeconds = 300;

    @ConfigValue(value = "performance.tick-budget-ms", comment = "Tick basina cekirdek is butcesi")
    public double tickBudgetMillis = 1.5D;

    @ConfigValue(value = "debug", comment = "Ayrintili loglama")
    public boolean debug = false;

    public String[] languageCodes() {
        return availableLanguages.stream().map(String::valueOf).toArray(String[]::new);
    }
}
