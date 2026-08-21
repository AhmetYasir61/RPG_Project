package net.aethel.core.modules.auth;

import net.aethel.core.config.ConfigValue;

/**
 * Giris/kayit ayarlari. Panel moduna gore akis degisir ama kurallar (deneme sayisi,
 * PIN uzunlugu, oturum suresi) her iki modda da ayni sekilde uygulanir.
 */
public final class AuthSettings {

    /** PIN veya PASSWORD. PIN yalnizca rakam kabul eder, akilda kalmasi kolaydir. */
    @ConfigValue("auth.mode")
    public String mode = "PIN";

    @ConfigValue("auth.min-length")
    public int minLength = 4;

    @ConfigValue("auth.max-length")
    public int maxLength = 32;

    /** Bu kadar basarisiz denemeden sonra oyuncu atilir. */
    @ConfigValue("auth.max-attempts")
    public int maxAttempts = 5;

    /** Giris yapilmazsa bu sure sonunda oyuncu atilir (saniye). */
    @ConfigValue("auth.timeout-seconds")
    public int timeoutSeconds = 60;

    /** Ayni IP'den son giristen bu sure icinde donen oyuncu otomatik dogrulanir (dakika). */
    @ConfigValue("auth.session-minutes")
    public int sessionMinutes = 30;

    /** Giris yapmamis oyuncu hareket edemez, konusamaz, esya alamaz. */
    @ConfigValue("auth.freeze-until-login")
    public boolean freezeUntilLogin = true;

    /** WEB modunda uretilen giris baglantisinin gecerlilik suresi (saniye). */
    @ConfigValue("auth.web-token-seconds")
    public int webTokenSeconds = 300;
}
