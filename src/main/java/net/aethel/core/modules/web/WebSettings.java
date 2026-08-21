package net.aethel.core.modules.web;

import net.aethel.core.config.ConfigValue;

/**
 * Web paneli ayarlari. Hepsi config.yml -> admin.web.* altindadir: portun iki
 * ayri dosyada tanimlanmasi, birinin degistirilip digerinin unutulmasina ve
 * panelin "calisiyor ama baglanti yanlis porta gidiyor" durumuna yol aciyordu.
 */
public final class WebSettings {

    @ConfigValue("admin.web.bind")
    public String bind = "0.0.0.0";

    @ConfigValue("admin.web.port")
    public int port = 8080;

    /** Oyuncuya gonderilen adres. Ters vekil arkasindaysa elle yazilir. */
    @ConfigValue("admin.web.public-url")
    public String publicUrl = "";

    @ConfigValue("admin.web.session-timeout-minutes")
    public int sessionMinutes = 30;

    /** Yuklenebilecek en buyuk varlik dosyasi (MB). */
    @ConfigValue("admin.web.max-upload-mb")
    public int maxUploadMegabytes = 16;

    /** Envanter duzenleme ve el koyma yetkisi. */
    @ConfigValue("admin.web.allow-inventory-edit")
    public boolean allowInventoryEdit = true;

    /** Her yetkili islemi denetim kaydina yazilir; kapatilmasi onerilmez. */
    @ConfigValue("admin.web.audit-log")
    public boolean auditLog = true;
}
