package net.aethel.core.modules.dialog;

import java.util.Locale;

/**
 * Diyalogun ekranda nasil cizilecegi. BOX, MMORPG sunucularindaki cerceveli
 * diyalog kutusudur ve negatif bosluk fontu ile cizilir.
 */
public enum DialogStyle {

    /** Ekranin altinda cerceveli kutu: portre + isim + metin + secenekler. */
    BOX,

    /** Tek satir action bar; kisa bildirimler icin. */
    ACTIONBAR,

    /** Sohbet penceresine yazar; en basit ve pack gerektirmeyen mod. */
    CHAT;

    public static DialogStyle parse(String raw) {
        if (raw == null) return BOX;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BOX;
        }
    }
}
