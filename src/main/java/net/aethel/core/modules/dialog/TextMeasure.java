package net.aethel.core.modules.dialog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft varsayilan fontunda metin genisligi olcer ve satira boler. Kutu sabit
 * genislikte oldugu icin sarmalamayi karakter sayisina gore yapmak yamuk sonuc verir.
 */
final class TextMeasure {

    /**
     * Vanilla fontta karakterler esit genislikte DEGILDIR: 'i' 2 piksel, 'W' 6 piksel
     * yer kaplar. Karakter sayarak sarmalamak dar harflerle dolu satirlari erken,
     * genis harflerle dolu satirlari gec keser ve kutu kenarindan tasar.
     */
    private static final int DEFAULT_WIDTH = 6;
    private static final String NARROW_CHARS = "!.,;:i|'`l";
    private static final String MEDIUM_CHARS = "ftIk()[]{}\"*";

    private TextMeasure() {}

    static int width(char character) {
        if (character == ' ') return 4;
        if (NARROW_CHARS.indexOf(character) >= 0) return 2;
        if (MEDIUM_CHARS.indexOf(character) >= 0) return 5;
        return DEFAULT_WIDTH;
    }

    static int width(String text) {
        int total = 0;
        for (char character : text.toCharArray()) total += width(character);
        return total;
    }

    /**
     * Metni verilen piksel genisligine gore satirlara boler. MiniMessage etiketleri
     * olcume katilmaz: etiket ekranda gorunmez, genislige de eklenmemelidir.
     */
    static List<String> wrap(String miniMessageText, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;

        for (String word : miniMessageText.split(" ")) {
            int wordWidth = width(strip(word));
            if (currentWidth > 0 && currentWidth + 4 + wordWidth > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }
            if (currentWidth > 0) {
                current.append(' ');
                currentWidth += 4;
            }
            current.append(word);
            currentWidth += wordWidth;
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    /** MiniMessage etiketlerini cikarip yalnizca gorunur metni birakir. */
    static String strip(String raw) {
        return PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(raw));
    }

    /** Gorunur karakter sayisi; typewriter ilerlemesi bunun uzerinden hesaplanir. */
    static int visibleLength(String raw) {
        return strip(raw).length();
    }

    /**
     * Gorunur metni ilk n karaktere kirpar ama MiniMessage etiketlerini KORUR.
     * Etiketleri sayarak kirpmak, akan metnin ortasinda etiketi yarida keser ve
     * ekranda ham "<gr" gibi parcalar gorunur.
     */
    static String truncateVisible(String raw, int visibleCount) {
        StringBuilder result = new StringBuilder();
        int visible = 0;
        boolean inTag = false;

        for (char character : raw.toCharArray()) {
            if (character == '<') inTag = true;
            if (!inTag) {
                if (visible >= visibleCount) break;
                visible++;
            }
            result.append(character);
            if (character == '>') inTag = false;
        }
        return result.toString();
    }

    static Component render(String raw) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw);
    }
}
