package net.aethel.core.modules.panel;

import net.aethel.core.config.ConfigValue;

/**
 * Yonetim paneli ayarlari. Tumu config.yml -> admin.* altindadir.
 * Web sunucusunun kendi ayarlari (bind, port, oturum) WebSettings icindedir;
 * ayni yolu iki holder'da tanimlamak cakisma uretir.
 */
public final class PanelSettings {

    /** GUI veya WEB. Ikisi ayni anda etkin olamaz. */
    @ConfigValue("admin.mode")
    public String mode = "GUI";

    @ConfigValue("admin.permission")
    public String permission = "aethel.admin.panel";
}
