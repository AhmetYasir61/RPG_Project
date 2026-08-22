package net.aethel.core.modules.content.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Soket bolgesinden baslayip asama ilerledikce DISARI YAYILAN renk boyamasi.
 *
 * Ilk asamada yalnizca soket yuvasi renklenir; asama yukseldikce renk bicagin
 * geri kalanina dogru yayilir ve son asamada silahin tamami tasin rengini alir.
 * "Basta az parliyor, gide gide her yani aleve donuyor" istegi tam olarak budur:
 * ayri bir isik efekti degil, dokunun kendisinin asama asama devralinmasi.
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
        // Cekirdek: rengin YAYILMAYA BASLADIGI pikseller (soket yuvasi).
        boolean[][] core = coreOf(image, maskImage);
        double[][] distance = distanceFrom(core, image.getWidth(), image.getHeight());
        double reach = reachOf(stage, lastStage, image.getWidth(), image.getHeight());

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

                // Dis hat asla boyanmaz: saf siyah da teknik olarak "gri"dir ve
                // sinir olmasaydi kilicin hatti dagilirdi.
                int tone255 = (r + g + b) / 3;
                double weight = tone255 < MIN_TONE ? 0.0D
                        : weightOf(core[y][x], distance[y][x], reach);

                if (weight > 0.0D) {
                    double strengthHere = strength * weight;
                    // Gri deger tonu belirler, renk onu carpar: dokunun kendi
                    // golge/isik detayi korunur, uzerine yalnizca renk gelir.
                    // Ton 0.45 tabanina cekilir, yoksa koyu griler renksiz kalir
                    // ve alev yalnizca en acik piksellerde gorunur.
                    double tone = 0.45D + 0.55D * (tone255 / 255.0D);
                    r = blend(r, tint(tone, tintR), strengthHere);
                    g = blend(g, tint(tone, tintG), strengthHere);
                    b = blend(b, tint(tone, tintB), strengthHere);

                    // Cekirdege yakin yerler ayrica AYDINLANIR: parilti merkezde
                    // toplanir, uclara dogru soner.
                    double lift = strengthHere * strengthHere * 0.45D;
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

    /**
     * Rengin yayilmaya BASLADIGI pikseller: maske varsa maskenin beyaz bolgesi,
     * yoksa parlaklik araligindaki gri pikseller (soket yuvasi).
     */
    private static boolean[][] coreOf(BufferedImage image, BufferedImage mask) {
        boolean[][] core = new boolean[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) continue;
                core[y][x] = tintable(mask, x, y,
                        (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
            }
        }
        return core;
    }

    /**
     * Her pikselin cekirdege uzakligi (Chebyshev, cok gecisli yayilma).
     * Doku 16x16 civari oldugu icin basit bir dalga yeterli; ayri bir kutuphane
     * ya da oncelik kuyrugu gerektirmez.
     */
    private static double[][] distanceFrom(boolean[][] core, int width, int height) {
        double[][] distance = new double[height][width];
        for (double[] row : distance) java.util.Arrays.fill(row, Double.MAX_VALUE);

        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (core[y][x]) {
                    distance[y][x] = 0;
                    queue.add(new int[] {x, y});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = point[0] + dx;
                    int ny = point[1] + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    if (distance[ny][nx] > distance[point[1]][point[0]] + 1) {
                        distance[ny][nx] = distance[point[1]][point[0]] + 1;
                        queue.add(new int[] {nx, ny});
                    }
                }
            }
        }
        return distance;
    }

    /**
     * Rengin cekirdekten ne kadar uzaga tastigi. Ilk asamada neredeyse hic
     * tasmaz, son asamada dokunun tamamini kapsar.
     */
    private static double reachOf(int stage, int lastStage, int width, int height) {
        double full = Math.max(width, height);
        if (lastStage <= 0) return full;
        double t = Math.min(1.0D, stage / (double) lastStage);
        return full * t * t;              // basta yavas, sonda hizli yayilir
    }

    /** Cekirdek 1.0; disari dogru uzaklikla soner, menzil disi 0. */
    private static double weightOf(boolean core, double distance, double reach) {
        if (core) return 1.0D;
        if (reach <= 0 || distance > reach) return 0.0D;
        double falloff = 1.0D - (distance / reach);
        return falloff * falloff;         // kenarda yumusak biter
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
