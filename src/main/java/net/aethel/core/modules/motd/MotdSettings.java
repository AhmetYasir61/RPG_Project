package net.aethel.core.modules.motd;

import net.aethel.core.config.ConfigValue;

/** Sunucu listesi gorunumu ayarlari. Metinler motd.yml icinde tanimlanir. */
public final class MotdSettings {

    /** Birden fazla MOTD tanimliysa her pingde rastgele secilir. */
    @ConfigValue("motd.random")
    public boolean random = true;

    /** Oyuncu sayisi yerine ozel metin gosterilsin mi. */
    @ConfigValue("motd.custom-player-count")
    public boolean customPlayerCount = false;

    /** Sunucu listesinde gorunen sahte azami oyuncu sayisi (0 = gercek deger). */
    @ConfigValue("motd.fake-max-players")
    public int fakeMaxPlayers = 0;

    /** Listede fare ile beklendiginde gorunen oyuncu ornegi. */
    @ConfigValue("motd.hover-enabled")
    public boolean hoverEnabled = true;

    /** Sunucu ikonu dosyasi (64x64 PNG). Bos ise varsayilan ikon kullanilir. */
    @ConfigValue("motd.icon-file")
    public String iconFile = "server-icon.png";
}
