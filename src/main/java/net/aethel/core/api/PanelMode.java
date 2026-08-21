package net.aethel.core.api;

import java.util.Locale;

/**
 * Yonetim paneli modu. GUI ve WEB birbirini disler: ayni kaydi iki yerden duzenlemek
 * cakisma ve veri kaybi uretir, bu yuzden cekirdek boot'ta tek mod secildigini dogrular.
 */
public enum PanelMode {

    /** Oyun ici menu paneli; metin girisi anvil ile alinir. */
    GUI,

    /** Tarayici paneli; dosya yukleme ve onizleme yalnizca bu modda mumkundur. */
    WEB;

    public static PanelMode parse(String raw) {
        if (raw == null) return GUI;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GUI;
        }
    }

    public boolean isWeb() {
        return this == WEB;
    }
}
