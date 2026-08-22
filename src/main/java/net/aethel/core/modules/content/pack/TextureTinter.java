package net.aethel.core.modules.content.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Dokunun GRI bolgesini bir renge boyar ve asamaya gore parlakligini artirir.
 *
 * Isaretleme icin ayri bir maske dosyasi istenmez: gri piksel zaten
 * "boyanabilir" demektir (R, G ve B birbirine yakinsa renksizdir). Sanatci
 * kilicin agzini griye cizer, gerisi kendiliginde calisir. Renkli pikseller
 * (ahsap sap, deri kabza) hic dokunulmadan kalir.
 *
 * Calisma aninda degil, PAKET URETIMINDE calisir: istemci paketi yalnizca
 * girerken indirir, bu yuzden her varyantin pakette hazir durmasi gerekir.
 */
final class TextureTinter {

    /** R,G,B arasindaki en buyuk fark bunun altindaysa piksel "gri" sayilir. */
    private static final int GREY_TOLERANCE = 24;

    /**
     * Boyanacak grinin parlaklik araligi.
     *
     * Alt sinir SART: neredeyse her Minecraft dokusunun dis hatti saf siyahtir
     * ve saf siyah da "gri"dir. Sinir olmasaydi kilicin dis hatti da renge
     * boyanir, item dagilmis gorunurdu. Ust sinir da benzer sekilde beyaz
     * parlamalari korur.
     */
    private static final int MIN_TONE = 28;
    private static final int MAX_TONE = 232;

    /** Son asamada renk tamamen devralir; ara asamalar buna dogru yaklasir. */
    private static final double MAX_STRENGTH = 1.0D;

    /**
     * Asamalar arasindaki fark GORULEBILIR olmali. Ilk asama zaten bir dokunustan
     * ibaret olsaydi oyuncu tasi takti mi takmadi mi anlayamazdi; bu yuzden ilk
     * asama da belirgin bir tabandan baslar ve ustune eklenir.
     */
    private static final double BASE_STRENGTH = 0.45D;

    private TextureTinter() {}

    /**
     * @param stage     asama indeksi (1..lastStage); 0 icin varyant uretilmez
     * @param lastStage son asama indeksi
     */
    static void write(File source, File target, int tintRgb, int stage, int lastStage)
            throws IOException {
        write(source, maskFor(source), target, tintRgb, stage, lastStage);
    }

    /**
     * @param mask varsa yalnizca maskede BEYAZ olan pikseller boyanir; yoksa gri
     *             piksel sezgisi kullanilir. Maske, sanatciya kesin denetim verir:
     *             "gri" sezgisi cogu dokuda dogru calisir ama her dokuda degil.
     */
    static void write(File source, File mask, File target, int tintRgb,
                      int stage, int lastStage) throws IOException {
        BufferedImage image = ImageIO.read(source);
        if (image == null) throw new IOException("Doku okunamadi: " + source);

        BufferedImage maskImage = mask != null && mask.isFile() ? ImageIO.read(mask) : null;
        if (maskImage != null && (maskImage.getWidth() != image.getWidth()
                || maskImage.getHeight() != image.getHeight())) {
            throw new IOException("Maske olculeri dokuyla uyusmuyor: " + mask.getName());
        }

        double strength = strengthOf(stage, lastStage);
        BufferedImage out = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        int tintR = (tintRgb >> 16) & 0xFF;
        int tintG = (tintRgb >> 8) & 0xFF;
        int tintB = tintRgb & 0xFF;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                if (tintable(maskImage, x, y, r, g, b)) {
                    // Gri deger tonu belirler, renk onu carpar: dokunun kendi
                    // golge/isik detayi korunur, uzerine yalnizca renk gelir.
                    // Ton 0.45 tabanina cekilir, yoksa koyu griler renksiz kalir
                    // ve alev yalnizca en acik piksellerde gorunur.
                    double tone = 0.45D + 0.55D * (((r + g + b) / 3.0D) / 255.0D);
                    r = blend(r, tint(tone, tintR), strength);
                    g = blend(g, tint(tone, tintG), strength);
                    b = blend(b, tint(tone, tintB), strength);

                    // Ust asamalarda bolge ayrica AYDINLANIR: "parilti gitgide
                    // artiyor" hissini veren sey bu, ayri bir efekt degil.
                    double lift = strength * strength * 0.45D;
                    r = clamp(r + (int) ((255 - r) * lift));
                    g = clamp(g + (int) ((255 - g) * lift * 0.55D));
                    b = clamp(b + (int) ((255 - b) * lift * 0.25D));
                }
                out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        ImageIO.write(out, "png", target);
    }

    /** Asama arttikca renk guclenir; ilk asama belirgin bir tabandan baslar. */
    private static double strengthOf(int stage, int lastStage) {
        if (lastStage <= 0) return MAX_STRENGTH;
        double t = Math.min(1.0D, stage / (double) lastStage);
        return BASE_STRENGTH + (MAX_STRENGTH - BASE_STRENGTH) * t;
    }

    /** Maske varsa o karar verir; yoksa parlaklik araligindaki gri pikseller. */
    private static boolean tintable(BufferedImage mask, int x, int y, int r, int g, int b) {
        if (mask != null) {
            int pixel = mask.getRGB(x, y);
            if (((pixel >>> 24) & 0xFF) == 0) return false;
            return (((pixel >> 16) & 0xFF) + ((pixel >> 8) & 0xFF) + (pixel & 0xFF)) / 3 > 127;
        }
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min > GREY_TOLERANCE) return false;

        int tone = (r + g + b) / 3;
        return tone >= MIN_TONE && tone <= MAX_TONE;
    }

    /** "item/bakir_kilic.png" yaninda "item/bakir_kilic_maske.png" aranir. */
    static File maskFor(File source) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        return new File(source.getParentFile(),
                (dot < 0 ? name : name.substring(0, dot)) + "_maske.png");
    }

    /** Denetim icin: bu dokuda kac piksel boyanacak. */
    static int tintablePixels(File source) throws IOException {
        BufferedImage image = ImageIO.read(source);
        if (image == null) return 0;
        File mask = maskFor(source);
        BufferedImage maskImage = mask.isFile() ? ImageIO.read(mask) : null;

        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) continue;
                if (tintable(maskImage, x, y, (argb >> 16) & 0xFF,
                        (argb >> 8) & 0xFF, argb & 0xFF)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int tint(double tone, int channel) {
        return clamp((int) Math.round(tone * channel));
    }

    private static int blend(int original, int tinted, double strength) {
        return clamp((int) Math.round(original * (1 - strength) + tinted * strength));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
