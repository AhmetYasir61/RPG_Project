package net.aethel.core.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ayarlarin dogru dosyadan okundugunu dogrular. Ayni YAML yolunun iki dosyada
 * bulunmasi, kullanicinin config.yml'yi duzenleyip hicbir sey degismedigini
 * gormesine yol acan bir hata sinifiydi; burada derlemeden once yakalaniyor.
 */
class ConfigPathTest {

    private static final Pattern CONFIG_VALUE =
            Pattern.compile("@ConfigValue\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern BIND_CALL =
            Pattern.compile("config\\(\\)\\.(?:open|bind)\\(\\s*\"([^\"]+)\"");

    /**
     * config.yml'de tanimli bir kok bolum (admin, auth, resource-pack, language...)
     * baska bir dosyaya bagli holder tarafindan kullanilmamali.
     */
    @Test
    void configYmlBolumleriBaskaDosyayaBaglanmamali() throws IOException {
        Path resources = Path.of("src/main/resources/config.yml");
        if (!Files.exists(resources)) return;

        List<String> coreRoots = coreRootKeys(resources);
        Map<String, String> pathToFile = collectHolderPaths();
        List<String> conflicts = new ArrayList<>();

        pathToFile.forEach((path, file) -> {
            if (file.equals("config.yml")) return;
            String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
            if (coreRoots.contains(root)) {
                conflicts.add(path + " -> " + file + " (config.yml icinde '" + root + "' var)");
            }
        });

        assertTrue(conflicts.isEmpty(),
                "config.yml bolumleri baska dosyadan okunuyor:\n  " + String.join("\n  ", conflicts));
    }

    /** config.yml'nin ust duzey anahtarlari. */
    private List<String> coreRootKeys(Path configYml) throws IOException {
        List<String> roots = new ArrayList<>();
        for (String line : Files.readAllLines(configYml, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith(" ")) continue;
            int colon = line.indexOf(':');
            if (colon > 0) roots.add(line.substring(0, colon).trim());
        }
        return roots;
    }

    /**
     * Her Settings sinifinin @ConfigValue yollarini, o sinifi baglayan dosyayla
     * eslestirir. Kaynak taramasi kullaniliyor: calisma zamani baglantisini
     * kurmadan, derleme oncesi bir kontrol istiyoruz.
     */
    private Map<String, String> collectHolderPaths() throws IOException {
        Map<String, String> result = new HashMap<>();
        Path root = Path.of("src/main/java");
        if (!Files.exists(root)) return result;

        Map<String, String> settingsToFile = new HashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher bind = BIND_CALL.matcher(source);
                while (bind.find()) {
                    String target = bind.group(1);
                    // Ayni modulun Settings sinifi ile eslestir.
                    String moduleDir = file.getParent().getFileName().toString();
                    settingsToFile.put(moduleDir, target);
                }
            }
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.getFileName().toString().endsWith("Settings.java")).toList()) {
                String moduleDir = file.getParent().getFileName().toString();
                String target = settingsToFile.get(moduleDir);
                if (target == null) continue;

                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = CONFIG_VALUE.matcher(source);
                while (matcher.find()) {
                    result.put(matcher.group(1), target);
                }
            }
        }
        return result;
    }
}
