package net.aethel.core.util;

/**
 * Piksel kaydirmasini negatif/pozitif bosluk karakterlerine cevirir. HUD ve diyalog
 * kutusu bu araci paylasir; font uretici ayni tabloyu U+F800'den itibaren tanimlar.
 */
public final class SpaceEncoder {

    /** FontGenerator ile ayni sira: once negatifler, sonra pozitifler. */
    private static final int[] NEGATIVE = {-1, -2, -3, -4, -5, -6, -7, -8, -16, -32, -64, -128};
    private static final int[] POSITIVE = {1, 2, 3, 4, 5, 6, 7, 8, 16, 32, 64, 128};
    private static final int NEGATIVE_BASE = 0xF800;
    private static final int POSITIVE_BASE = NEGATIVE_BASE + NEGATIVE.length;

    private SpaceEncoder() {}

    /**
     * Istenen kaydirmayi en az karakterle uretir: buyukten kucuge acgozlu secim.
     * 128'lik adimlarla baslamak, -300 gibi bir kaydirmayi 3 karakterde cozer;
     * tek tek -1 kullansaydik 300 karakterlik bir metin cikardi.
     */
    public static String shift(int pixels) {
        if (pixels == 0) return "";
        StringBuilder builder = new StringBuilder();
        int remaining = Math.abs(pixels);
        boolean negative = pixels < 0;
        int[] steps = negative ? NEGATIVE : POSITIVE;
        int base = negative ? NEGATIVE_BASE : POSITIVE_BASE;

        for (int i = steps.length - 1; i >= 0 && remaining > 0; i--) {
            int size = Math.abs(steps[i]);
            while (remaining >= size) {
                builder.append(Character.toChars(base + i));
                remaining -= size;
            }
        }
        return builder.toString();
    }

    /** Iki parcayi ust uste bindirir: once ciz, sonra genisligi kadar geri gel. */
    public static String overlay(String glyph, int glyphWidth) {
        return glyph + shift(-glyphWidth);
    }
}
