package net.aethel.core.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Komutlarin iki sessiz hatasini derleme zamaninda yakalar.
 *
 * 1. Bir komut plugin.yml'de TANIMSIZ bir izin isterse, izin varsayilan olarak
 *    kimsede olmaz: komut sunucuda gorunmez ve neden gorunmedigi hicbir yere
 *    yazilmaz.
 * 2. Kodda gecen bir dil anahtari lang dosyalarinda YOKSA oyuncuya bos ya da ham
 *    anahtar gider. Gunluge de bir sey dusmez.
 *
 * Ikisi de calisirken degil, ancak biri denk gelince fark edilir. Burada
 * kaynak taranarak bastan engelleniyor.
 */
class CommandContractTest {

    private static final Path SOURCE = Path.of("src/main/java/net/aethel/core");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void everyCommandPermissionIsDeclared() throws IOException {
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        Set<String> declared = matches(pluginYml, "(aethel\\.[a-z.]+|core\\.[a-z]+)");

        List<String> undeclared = new ArrayList<>();
        for (Path file : commandSources()) {
            String body = Files.readString(file);
            for (String permission : matches(body, "permission = \"([^\"]+)\"")) {
                if (!declared.contains(permission)) {
                    undeclared.add(file.getFileName() + " -> " + permission);
                }
            }
        }
        assertTrue(undeclared.isEmpty(),
                "plugin.yml'de tanimsiz izin isteyen komut(lar) var, bu komutlar "
                        + "sunucuda hic gorunmez: " + undeclared);
    }

    @Test
    void everyLanguageKeyUsedByCommandsExists() throws IOException {
        Set<String> turkish = keysOf(Files.readString(RESOURCES.resolve("lang/tr.yml")));
        Set<String> english = keysOf(Files.readString(RESOURCES.resolve("lang/en.yml")));

        List<String> missing = new ArrayList<>();
        for (Path file : allSources()) {
            String body = Files.readString(file);
            // Anahtar, lang().send/render cagrisinin ILK noktali dizesidir ve
            // hemen ardindan "," ya da ")" gelmelidir. Kalibi cagriya baglamak
            // sart: kodda gecen izin adlari, dosya adlari ve config yollari da
            // noktalidir. Kapanis kontrolu ise BIRLESTIRILEN anahtarlari eler
            // ("rpg.unlock-" + tip gibi); onlar calisma aninda olusur ve statik
            // olarak dogrulanamaz.
            for (String key : matches(body,
                    "lang\\(\\)\\.(?:send|render)\\([^;]{0,160}?\"([a-z0-9-]+\\.[a-z0-9.-]+)\"\\s*[,)]")) {
                if (!turkish.contains(key)) missing.add(file.getFileName() + " -> tr:" + key);
                if (!english.contains(key)) missing.add(file.getFileName() + " -> en:" + key);
            }
        }
        assertTrue(missing.isEmpty(),
                "Dil dosyasinda karsiligi olmayan anahtar(lar); oyuncuya bos mesaj "
                        + "gider: " + missing);
    }

    /** "bolum.anahtar" bicimindeki tum yollari toplar (iki seviye yeterli). */
    private static Set<String> keysOf(String yaml) {
        Set<String> keys = new HashSet<>();
        String section = "";
        for (String line : yaml.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!line.startsWith(" ") && line.contains(":")) {
                section = line.substring(0, line.indexOf(':')).trim();
            } else if (line.startsWith("  ") && !line.startsWith("   ") && line.contains(":")) {
                keys.add(section + "." + line.substring(0, line.indexOf(':')).trim());
            }
        }
        return keys;
    }

    private static Set<String> matches(String body, String pattern) {
        Set<String> found = new HashSet<>();
        Matcher matcher = Pattern.compile(pattern).matcher(body);
        while (matcher.find()) found.add(matcher.group(1));
        return found;
    }

    private static List<Path> commandSources() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE)) {
            return walk.filter(path -> path.getFileName().toString().endsWith("Command.java"))
                    .toList();
        }
    }

    /**
     * Dil anahtari yalnizca komutlarda kullanilmiyor. Menude gorunen ham
     * "menu.catalog-title-admin" yazisi ItemCatalog icindeydi ve yalnizca
     * *Command.java taradigi icin ilk surumde gozden kacti.
     */
    private static List<Path> allSources() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE)) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }
}
