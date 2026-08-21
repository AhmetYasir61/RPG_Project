package net.aethel.core.modules.web;

import net.aethel.core.config.ConfigValue;

/**
 * Web paneli ayarlari. Panel yalnizca admin.mode=WEB iken calisir; GUI modunda
 * port hic dinlenmez.
 */
public final class WebSettings {

    @ConfigValue("web.bind")
    public String bind = "0.0.0.0";

    @ConfigValue("web.port")
    public int port = 8080;

    @ConfigValue("web.session-minutes")
    public int sessionMinutes = 30;

    /** Yuklenebilecek en buyuk varlik dosyasi (MB). */
    @ConfigValue("web.max-upload-mb")
    public int maxUploadMegabytes = 16;

    /** Envanter duzenleme ve el koyma yetkisi. */
    @ConfigValue("web.allow-inventory-edit")
    public boolean allowInventoryEdit = true;

    /** Her yetkili islemi denetim kaydina yazilir; kapatilmasi onerilmez. */
    @ConfigValue("web.audit-log")
    public boolean auditLog = true;
}
