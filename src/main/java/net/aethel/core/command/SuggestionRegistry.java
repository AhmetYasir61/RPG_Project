package net.aethel.core.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Adlandirilmis tab-complete kaynaklari. Modul kendi kimliklerini bir kez kaydeder
 * ({@code suggest("dialog", ...)}), komut da yalnizca adi soyler
 * ({@code @Arg(suggests = "dialog")}). Boylece komut sinifi hicbir servise
 * bagimli olmaz ve ayni kaynak birden cok komuttan kullanilabilir.
 *
 * Saglayicilar Brigadier'in oneri thread'inde CAGRILIR: yalnizca bellekteki
 * hazir koleksiyonlari dondurmeliler. Diskten okuyan ya da veritabanina giden
 * bir saglayici, her tus vurusunda oyuncuyu bekletir.
 */
public final class SuggestionRegistry {

    /** Tek seferde gonderilen azami oneri. Uzun listeler istemcide okunmaz hale gelir. */
    private static final int LIMIT = 75;

    private final Map<String, Supplier<Collection<String>>> sources = new ConcurrentHashMap<>();

    /** Kaynagi kaydeder; ayni ad ikinci kez kaydedilirse sonuncusu gecerlidir. */
    public void register(String name, Supplier<Collection<String>> source) {
        sources.put(name, source);
    }

    public void unregister(String name) {
        sources.remove(name);
    }

    public boolean has(String name) {
        return sources.containsKey(name);
    }

    /**
     * Yazilan on eke uyan onerileri dondurur.
     *
     * Eslesme buyuk/kucuk harf duyarsizdir ve namespace'li kimliklerde ("aethel:kilic")
     * ad kismindan da eslesir: oyuncu "kil" yazinca kimligi bastan yazmak zorunda
     * kalmamalidir.
     *
     * Bir saglayici patlarsa oneri listesi bos doner; tamamlama eksikligi komutu
     * kullanilamaz hale getirmemeli.
     */
    public List<String> suggest(String name, String input) {
        Supplier<Collection<String>> source = sources.get(name);
        if (source == null) return List.of();

        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        try {
            return source.get().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(value -> matches(value.toLowerCase(Locale.ROOT), prefix))
                    .distinct()
                    .sorted()
                    .limit(LIMIT)
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static boolean matches(String value, String prefix) {
        if (prefix.isEmpty() || value.startsWith(prefix)) return true;
        int colon = value.indexOf(':');
        return colon >= 0 && value.substring(colon + 1).startsWith(prefix);
    }
}
