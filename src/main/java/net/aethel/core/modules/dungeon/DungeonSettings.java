package net.aethel.core.modules.dungeon;

import net.aethel.core.config.ConfigValue;

/**
 * Dungeon ayarlari. Cokme suresi ve cikis davranisi burada; tasarim dunyasi
 * ayarlari da ayni dosyada durur.
 */
public final class DungeonSettings {

    /** Cekirdek kirildiktan sonra cikis icin verilen sure (saniye). */
    @ConfigValue("dungeon.collapse-seconds")
    public int collapseSeconds = 600;

    /** Bos kalan bir ornek bu sure sonunda kendiliginden silinir (saniye). */
    @ConfigValue("dungeon.empty-timeout-seconds")
    public int emptyTimeoutSeconds = 120;

    /** Ayni anda acik olabilecek en fazla ornek sayisi. */
    @ConfigValue("dungeon.max-instances")
    public int maxInstances = 8;

    /** Oda sablonlarinin tasarlandigi duz dunyanin adi. */
    @ConfigValue("dungeon.design-world")
    public String designWorld = "dungeon_tasarim";

    /** Odalarin dikey araligi: tasarim dunyasinda bu Y'den itibaren calisilir. */
    @ConfigValue("dungeon.room-min-y")
    public int roomMinY = 64;

    @ConfigValue("dungeon.room-height")
    public int roomHeight = 24;

    /** Cikista oyuncunun birakilacagi dunya. */
    @ConfigValue("dungeon.exit-world")
    public String exitWorld = "world";

    /** Cikis noktasi bu biyomlarda aranir; oyuncu ormana birakilir. */
    @ConfigValue("dungeon.exit-biomes")
    public java.util.List<?> exitBiomes = java.util.List.of("FOREST", "TAIGA", "BIRCH_FOREST");

    /** Cikis noktasi girisin bu kadar uzagina atilir (blok). */
    @ConfigValue("dungeon.exit-scatter")
    public int exitScatter = 200;
}
