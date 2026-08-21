package net.aethel.core.api;

/**
 * Karakter statlari. Her stat tek bir formule beslenir; hasar ve dayaniklilik
 * hesaplari StatType uzerinden yurur, sabit sayilar koda gomulmez.
 */
public enum StatType {

    /** Yakin dovus hasari ve tasima kapasitesi. */
    STRENGTH("guc"),
    /** Saldiri hizi, kacinma ve kritik sansi. */
    DEXTERITY("ceviklik"),
    /** Buyu hasari ve mana havuzu. */
    INTELLIGENCE("zeka"),
    /** Can havuzu ve hasar azaltma. */
    VITALITY("dayaniklilik"),
    /** Loot nadirligi ve NPC fiyat indirimi. */
    LUCK("sans");

    private final String turkishName;

    StatType(String turkishName) {
        this.turkishName = turkishName;
    }

    public String turkishName() {
        return turkishName;
    }

    /** Profil niteligi anahtari: "rpg:stat:strength" */
    public String attributeKey() {
        return "rpg:stat:" + name().toLowerCase(java.util.Locale.ROOT);
    }
}
