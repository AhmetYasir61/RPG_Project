package net.aethel.core.api;

import java.util.Map;
import java.util.Set;

/**
 * Ozellik anahtarlari. Kapali bir ozellik oyunda HIC YOKMUS gibi davranir: komutu
 * tab-complete'te gorunmez, listener'i kayitli degildir, menude yer kaplamaz.
 */
public interface FeatureService {

    /** Anahtar bicimi: "travel.scroll", "party.tracking", "loot.threat-scaling". */
    boolean enabled(String key);

    /** Kapaliysa true doner; okunabilirlik icin. */
    default boolean disabled(String key) {
        return !enabled(key);
    }

    /**
     * Calisma zamaninda degistirir. Kapatma aninda o ozelligin listener'lari
     * dusurulur, acma aninda yeniden baglanir; sunucu yeniden baslatilmaz.
     */
    void set(String key, boolean value);

    Set<String> keys();

    Map<String, Boolean> snapshot();

    /**
     * Bir ozelligi tanimlar ve varsayilanini bildirir. Moduller onLoad icinde
     * kendi ozelliklerini kaydeder; boylece features.yml kendiliginden dolar.
     */
    void declare(String key, boolean defaultValue, String description);

    String description(String key);
}
