package net.aethel.core.modules.content.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * HUD, emoji ve GUI arka planlari icin font dosyasi uretir. Negatif bosluk
 * karakterleri sayesinde parcalar tek bir metin satirinda ust uste bindirilebilir.
 */
public final class FontGenerator {

    /**
     * Negatif bosluk teknigi: genisligi negatif olan bir karakter, istemcinin cizim
     * imlecini geriye iter. Boylece "ikon + negatif bosluk + ikon" yazarak parcalar
     * ayni piksel uzerinde ust uste gelir. Vanilla'da baska turlu ekran ustune serbest
     * konumlu grafik cizmek mumkun degildir; scoreboard sabit kose, boss bar tek satirdir.
     *
     * Ayni teknik chest GUI basliginda da calisir: basliga yerlestirilen bir font
     * karakteri, envanterin ustunde tam ekran bir arayuz texture'i gibi cizilir.
     */
    private static final int[] NEGATIVE_WIDTHS = {-1, -2, -3, -4, -5, -6, -7, -8, -16, -32, -64, -128};

    /** Font tanimini (default.json) uretir. */
    public JsonObject build(String namespace, Map<String, Integer> glyphs,
                            Map<String, GlyphSpec> specs) {
        JsonObject font = new JsonObject();
        JsonArray providers = new JsonArray();

        for (Map.Entry<String, Integer> entry : glyphs.entrySet()) {
            GlyphSpec spec = specs.get(entry.getKey());
            if (spec == null) continue;
            providers.add(bitmapProvider(namespace, spec, entry.getValue()));
        }
        providers.add(negativeSpaceProvider());
        font.add("providers", providers);
        return font;
    }

    /** Tek bir ikon/HUD parcasinin font tanimi. */
    public record GlyphSpec(String texturePath, int height, int ascent) {}

    private JsonObject bitmapProvider(String namespace, GlyphSpec spec, int codePoint) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", namespace + ":" + spec.texturePath());
        provider.addProperty("height", spec.height());
        // ascent, karakterin taban cizgisine gore dikey kaymasi; height'i asamaz.
        provider.addProperty("ascent", Math.min(spec.ascent(), spec.height()));
        JsonArray chars = new JsonArray();
        chars.add(new String(Character.toChars(codePoint)));
        provider.add("chars", chars);
        return provider;
    }

    /** Negatif bosluk saglayicisi; HUD hizalamasinin temeli. */
    private JsonObject negativeSpaceProvider() {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        int codePoint = 0xF800;
        for (int width : NEGATIVE_WIDTHS) {
            advances.addProperty(new String(Character.toChars(codePoint++)), width);
        }
        // Pozitif bosluklar da ayni yerden gelsin ki hizalama tek dosyada toplansin.
        for (int width : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 16, 32, 64, 128}) {
            advances.addProperty(new String(Character.toChars(codePoint++)), width);
        }
        provider.add("advances", advances);
        return provider;
    }
}
