package net.aethel.core.api;

/**
 * Isim + yuzde bicimli zorluk. Menude adi gorunur, sag caprazinda yuzdeyi tasiyan
 * kucuk bir ikon cizilir (font karakteri). Yuzde tum carpanlari besler.
 */
public record Difficulty(String id,
                         String displayName,
                         int percent,
                         String icon,
                         boolean hardcore) {

    /** Temel seviye; 100 = vanilla dengesi. */
    public static final int BASELINE = 100;

    public static Difficulty normal() {
        return new Difficulty("normal", "<white>Normal</white>", BASELINE, "", false);
    }

    /** Yuzdeyi carpana cevirir: %78 -> 0.78, %140 -> 1.40 */
    public double multiplier() {
        return percent / (double) BASELINE;
    }

    /** Mob cani; zorluk arttikca dogrusal artar. */
    public double healthMultiplier() {
        return multiplier();
    }

    /**
     * Mob hasari zorluktan daha yavas olcekler (karekok): hasar dogrusal artarsa
     * yuksek zorlukta oyuncu tek vurusta olur ve zorluk "imkansiz"a doner.
     */
    public double damageMultiplier() {
        return Math.sqrt(multiplier());
    }

    /** Odul, zorlukla dogrusal artar; risk-odul dengesi burada kurulur. */
    public double rewardMultiplier() {
        return multiplier();
    }

    /** Menude gosterilecek tam etiket: ad + kucuk yuzde ikonu. */
    public String label() {
        return displayName + "<font:aethel:default> " + icon + "</font><gray>" + percent + "%</gray>";
    }
}
