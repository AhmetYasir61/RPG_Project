package net.aethel.core.modules.travel;

import net.aethel.core.config.ConfigValue;

/**
 * Seyahat ayarlari. Serbest teleport kapali oldugu icin bu degerler MMORPG hissini
 * dogrudan belirler: cast suresi, soguma ve kesif zorunlulugu.
 */
public final class TravelSettings {

    /** Parsomen okuma suresi (saniye). Bu sure boyunca hasar alinirsa iptal olur. */
    @ConfigValue("travel.scroll-cast-seconds")
    public double scrollCastSeconds = 6.0D;

    /** Hearthstone cast suresi. */
    @ConfigValue("travel.hearthstone-cast-seconds")
    public double hearthstoneCastSeconds = 10.0D;

    /** Hearthstone sogumasi (dakika). */
    @ConfigValue("travel.hearthstone-cooldown-minutes")
    public int hearthstoneCooldownMinutes = 30;

    /** Savastan sonra bu sure gecmeden isinlanilamaz (saniye). */
    @ConfigValue("travel.combat-lock-seconds")
    public int combatLockSeconds = 10;

    /** Parsomen item kimligi; contents icinde tanimli olmalidir. */
    @ConfigValue("travel.scroll-item")
    public String scrollItem = "aethel:kesif_parsomeni";

    /** Waypoint'e isinlanmak icin oraya once yuruyerek gitmis olmak sart mi. */
    @ConfigValue("travel.require-discovery")
    public boolean requireDiscovery = true;

    /** Kesif yaricapi: oyuncu bu mesafeye girince waypoint kesfedilmis sayilir. */
    @ConfigValue("travel.discovery-radius")
    public double discoveryRadius = 12.0D;
}
