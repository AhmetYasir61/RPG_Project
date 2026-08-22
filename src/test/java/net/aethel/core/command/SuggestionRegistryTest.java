package net.aethel.core.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tab-complete kaynaklarinin davranisi ve komutlarla eslesmesi. */
class SuggestionRegistryTest {

    @Test
    void filtersByPrefixAndMatchesAfterNamespace() {
        SuggestionRegistry registry = new SuggestionRegistry();
        registry.register("item", () -> List.of(
                "aethel:alev_kilici", "aethel:sifa_iksiri", "baska:alev_topu"));

        assertEquals(List.of("aethel:alev_kilici", "aethel:sifa_iksiri", "baska:alev_topu"),
                registry.suggest("item", ""));

        // Namespace yazmadan ad kismindan eslesme: oyuncu kimligi bastan yazmak
        // zorunda kalmamali.
        assertEquals(List.of("aethel:alev_kilici", "baska:alev_topu"),
                registry.suggest("item", "alev"));

        assertEquals(List.of("aethel:alev_kilici"), registry.suggest("item", "aethel:alev_k"));
        assertEquals(List.of(), registry.suggest("item", "yok"));
    }

    /** Tanimsiz kaynak sessizce bos doner; komut yine calisir. */
    @Test
    void unknownSourceIsEmptyNotAnError() {
        assertEquals(List.of(), new SuggestionRegistry().suggest("boyle-bir-sey-yok", ""));
    }

    /** Patlayan bir saglayici tamamlamayi bozar ama komutu kullanilamaz yapmaz. */
    @Test
    void failingSourceDoesNotPropagate() {
        SuggestionRegistry registry = new SuggestionRegistry();
        registry.register("patlak", () -> {
            throw new IllegalStateException("modul kapali");
        });
        assertEquals(List.of(), registry.suggest("patlak", ""));
    }

    /**
     * Komutlarda gecen her suggests adi bir yerde KAYITLI olmalidir.
     *
     * Bu sessiz bir hatadir: yazim yanlisi olan bir kaynak adi ("difficulity")
     * hicbir hata vermez, yalnizca hicbir oneri gelmez -- ve tam olarak bu ozelligi
     * eklememizin nedeni, oneri gelmemesiydi.
     */
    @Test
    void everySuggestSourceUsedByCommandsIsRegistered() throws IOException {
        Path source = Path.of("src/main/java/net/aethel/core");
        Set<String> registered = new HashSet<>();
        Set<String> used = new HashSet<>();

        try (Stream<Path> walk = Files.walk(source)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String body = Files.readString(file);
                collect(body, "suggest\\(\"([a-z-]+)\"", registered);
                collect(body, "suggests = \"([a-z-]+)\"", used);
            }
        }
        List<String> missing = new ArrayList<>(used);
        missing.removeAll(registered);
        assertTrue(missing.isEmpty(),
                "Komutlarda kullanilan ama hicbir yerde kaydedilmemis oneri kaynagi "
                        + "(hic oneri gelmez, hata da vermez): " + missing);
    }

    /**
     * Kimlik argumanlari YALNIZCA kimlik kaynaklarina baglanmali.
     *
     * "player" ve "world" kaynaklari kimlik DEGILDIR: kimlik tipi degeri kucuk
     * harfe cevirir ve oyuncu adlari buyuk/kucuk harf duyarlidir. Yanlis
     * isaretlenmis bir oyuncu argumani, adi kucuk harfe cevirip "oyuncu
     * bulunamadi" der -- calisiyor gorunen, sessiz bir hata.
     */
    @Test
    void playerAndWorldArgumentsAreNotMarkedAsIdentifiers() throws IOException {
        List<String> wrong = new ArrayList<>();
        Pattern pattern = Pattern.compile("@Arg\\(([^)]*)\\)");

        try (Stream<Path> walk = Files.walk(Path.of("src/main/java/net/aethel/core"))) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher found = pattern.matcher(Files.readString(file));
                while (found.find()) {
                    String inner = found.group(1);
                    if (!inner.contains("identifier = true")) continue;
                    if (inner.contains("\"player\"") || inner.contains("\"world\"")) {
                        wrong.add(file.getFileName() + " -> " + inner);
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(),
                "Oyuncu/dunya argumani kimlik olarak isaretlenmis; ad kucuk harfe "
                        + "cevrilir ve eslesmez: " + wrong);
    }

    private static void collect(String body, String pattern, Set<String> target) {
        Matcher matcher = Pattern.compile(pattern).matcher(body);
        while (matcher.find()) target.add(matcher.group(1));
    }
}
