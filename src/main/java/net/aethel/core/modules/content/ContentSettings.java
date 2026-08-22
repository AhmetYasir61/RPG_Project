package net.aethel.core.modules.content;

import net.aethel.core.config.ConfigValue;

/** Icerik ve kaynak paketi ayarlari. Pack varsayilan olarak zorunludur. */
public final class ContentSettings {

    @ConfigValue("resource-pack.generate")
    public boolean generate = true;

    @ConfigValue("resource-pack.serve")
    public boolean serve = true;

    @ConfigValue("resource-pack.bind")
    public String bind = "0.0.0.0";

    @ConfigValue("resource-pack.port")
    public int port = 8085;

    /** Oyuncuya gonderilen adres. Bos ise sunucunun kendi IP'si kullanilir. */
    @ConfigValue("resource-pack.public-host")
    public String publicHost = "";

    /** true: kabul etmeyen veya indiremeyen oyuncu atilir. */
    @ConfigValue("resource-pack.force")
    public boolean force = true;

    /** Acilista uretim yapilsin mi; kapaliysa yalnizca mevcut zip sunulur. */
    @ConfigValue("resource-pack.generate-on-start")
    public boolean generateOnStart = true;

    /** Paketin pack_format degeri. 1.21.11 -> 75. */
    @ConfigValue("resource-pack.format")
    public int format = 75;
}
