package net.aethel.core.modules.panel;

import net.aethel.core.config.ConfigValue;

/**
 * Yonetim paneli ayarlari. mode GUI veya WEB olabilir; ikisi ayni anda etkin olamaz
 * ve cekirdek bunu acilista dogrular.
 */
public final class PanelSettings {

    @ConfigValue("admin.mode")
    public String mode = "GUI";

    @ConfigValue("admin.permission")
    public String permission = "aethel.admin.panel";

    @ConfigValue("admin.web.bind")
    public String webBind = "0.0.0.0";

    @ConfigValue("admin.web.port")
    public int webPort = 8080;

    /** Oyuncuya gonderilen adres; ters vekil arkasindaysa elle yazilir. */
    @ConfigValue("admin.web.public-url")
    public String webPublicUrl = "http://127.0.0.1:8080";

    @ConfigValue("admin.web.session-timeout-minutes")
    public int webSessionMinutes = 30;

    /** WEB modunda yetkilinin oyuncu envanterine mudahale edebilmesi. */
    @ConfigValue("admin.web.allow-inventory-edit")
    public boolean allowInventoryEdit = true;
}
