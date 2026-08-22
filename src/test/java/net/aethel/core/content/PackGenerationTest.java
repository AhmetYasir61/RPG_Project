package net.aethel.core.content;

import net.aethel.core.api.CustomItem;
import net.aethel.core.modules.content.pack.PackGenerator;
import net.aethel.core.modules.content.pack.PackIndex;
import net.aethel.core.modules.content.pack.PackPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource pack uretiminin gercekten calisan bir pack urettigini dogrular.
 *
 * Bu test bir kez kacirilan bir hatayi bir daha kacirmamak icin var: item
 * tanimindaki texture yolu contents/<ns>/textures altinda YOKSA pack yine de
 * sessizce uretilir ve oyuncu oyunda mor-siyah kare gorur. Sunucu gunlugunde
 * hicbir sey yazmaz. Burada dokunun pack'e gercekten girdigi kontrol ediliyor.
 */
class PackGenerationTest {

    private static CustomItem item(String id, String texture) {
        return new CustomItem(id, "aethel", "<white>" + id, List.of(), "IRON_SWORD",
                texture, "COMMON", 0, Map.of(), Map.of(), null, false, false,
                null, null, List.of());
    }

    @Test
    void packContainsModelDefinitionAndTexture(@TempDir Path dataFolder) throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        Files.write(textures.resolve("alev_kilici.png"), pngBytes());

        PackPaths paths = new PackPaths(dataFolder.toFile());
        PackGenerator generator = new PackGenerator(paths, new PackIndex(paths.index()),
                Logger.getLogger("test"));

        PackGenerator.Result result = generator.generate(
                List.of(item("alev_kilici", "item/alev_kilici.png")));

        assertNotNull(result.sha1(), "pack uretilemedi: " + result.warnings());
        assertEquals(40, result.sha1().length(), "SHA-1 kirk karakter olmali");

        Set<String> entries = new HashSet<>();
        try (ZipFile zip = new ZipFile(paths.generatedZip())) {
            zip.stream().forEach(entry -> entries.add(entry.getName()));
        }
        assertTrue(entries.contains("pack.mcmeta"), "pack.mcmeta yok: " + entries);
        assertTrue(entries.contains("assets/aethel/models/item/alev_kilici.json"),
                "model JSON yok: " + entries);
        assertTrue(entries.contains("assets/aethel/items/alev_kilici.json"),
                "item_model tanimi yok: " + entries);
        assertTrue(entries.contains("assets/aethel/textures/item/alev_kilici.png"),
                "DOKU PACK'E GIRMEDI -- oyunda mor-siyah kare gorunur: " + entries);
    }

    /** Modelin isaret ettigi doku yolu, pack icindeki gercek dosyayla eslesmeli. */
    @Test
    void modelPointsAtTextureThatExists(@TempDir Path dataFolder) throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        Files.write(textures.resolve("sifa_iksiri.png"), pngBytes());

        PackPaths paths = new PackPaths(dataFolder.toFile());
        new PackGenerator(paths, new PackIndex(paths.index()), Logger.getLogger("test"))
                .generate(List.of(item("sifa_iksiri", "item/sifa_iksiri.png")));

        File model = new File(paths.assets(), "aethel/models/item/sifa_iksiri.json");
        String json = Files.readString(model.toPath());
        assertTrue(json.contains("aethel:item/sifa_iksiri"),
                "model yanlis dokuyu gosteriyor: " + json);

        assertTrue(new File(paths.assets(), "aethel/textures/item/sifa_iksiri.png").isFile(),
                "modelin gosterdigi doku pack agacinda yok");
    }

    /** 1x1 saydam PNG. */
    private static byte[] pngBytes() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    }
}
