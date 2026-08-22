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

    /**
     * Yeniden uretimin "degisti mi" cevabi SHA-1 karsilastirmasina dayanir.
     * Ayni icerik ayni hash'i, degisen icerik farkli hash'i vermezse panel ve
     * komut "degisti" / "degismedi" derken yalan soyler.
     */
    @Test
    void hashTracksContentChanges(@TempDir Path dataFolder) throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        Files.write(textures.resolve("kilic.png"), pngBytes());

        PackPaths paths = new PackPaths(dataFolder.toFile());
        PackGenerator generator = new PackGenerator(paths, new PackIndex(paths.index()),
                Logger.getLogger("test"));

        String first = generator.generate(List.of(item("kilic", "item/kilic.png"))).sha1();
        String again = generator.generate(List.of(item("kilic", "item/kilic.png"))).sha1();
        assertEquals(first, again, "icerik ayniyken hash degismemeli");

        String withExtra = generator.generate(List.of(
                item("kilic", "item/kilic.png"),
                item("kalkan", "item/kilic.png"))).sha1();
        assertTrue(!first.equals(withExtra), "yeni item eklendiginde hash degismeli");
    }

    /**
     * item_model bileseninin degeri, uretilen tanim dosyasinin GERCEK yoluna
     * cozulmelidir. Anahtarda fazladan bir "item/" oneki vardi: tanim
     * assets/aethel/items/<id>.json'a yaziliyor, istemci ise
     * assets/aethel/items/item/<id>.json ariyordu. Tanim bulunamayinca butun
     * custom itemlar mor-siyah kare goruntu veriyordu -- ve pack denetimi bile
     * "sorun yok" diyordu, cunku dosyanin VARLIGINA bakiyordu, anahtarin oraya
     * cozuldugune degil.
     */
    @Test
    void itemModelKeyResolvesToTheGeneratedDefinition(@TempDir Path dataFolder) throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        Files.write(textures.resolve("kilic.png"), pngBytes());

        PackPaths paths = new PackPaths(dataFolder.toFile());
        CustomItem definition = item("kilic", "item/kilic.png");
        new PackGenerator(paths, new PackIndex(paths.index()), Logger.getLogger("test"))
                .generate(List.of(definition));

        Set<String> entries = new HashSet<>();
        try (ZipFile zip = new ZipFile(paths.generatedZip())) {
            zip.stream().forEach(entry -> entries.add(entry.getName()));
        }
        // Istemcinin item_model'i cozdugu yol.
        String resolved = "assets/" + definition.modelKey().replace(":", "/items/") + ".json";
        assertTrue(entries.contains(resolved),
                "item_model '" + definition.modelKey() + "' -> " + resolved
                        + " uretilmemis; item mor-siyah kare gorunur. Zip: " + entries);

        // Tanimin gosterdigi model dosyasi da yerinde olmali.
        String model = "assets/" + definition.modelPath().replace(":", "/models/") + ".json";
        assertTrue(entries.contains(model), "model dosyasi yok: " + model);

        // Ve tanim gercekten o modeli gostermeli.
        String json = Files.readString(new File(paths.assets(),
                "aethel/items/kilic.json").toPath());
        assertTrue(json.contains(definition.modelPath()),
                "tanim yanlis modeli gosteriyor: " + json);
    }

    /**
     * pack.mcmeta'daki pack_format sunucu surumuyle uyusmazsa istemci paketi
     * "eski surum" sayar: item_model tanimlari yeni kurallarla okunmaz ve butun
     * itemlar mor-siyah kare gorunur. Sunucu gunlugune hicbir sey yazilmaz.
     * Deger bir kez 46'da (1.21.4) kalmis ve bu yasanmisti.
     */
    @Test
    void mcmetaCarriesTheConfiguredPackFormat(@TempDir Path dataFolder) throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        Files.write(textures.resolve("kilic.png"), pngBytes());

        PackPaths paths = new PackPaths(dataFolder.toFile());
        PackGenerator generator = new PackGenerator(paths, new PackIndex(paths.index()),
                Logger.getLogger("test"));
        generator.generate(List.of(item("kilic", "item/kilic.png")));

        String mcmeta = Files.readString(new File(paths.packTree(), "pack.mcmeta").toPath());
        assertTrue(mcmeta.contains("\"pack_format\": " + PackGenerator.DEFAULT_PACK_FORMAT)
                        || mcmeta.contains("\"pack_format\":" + PackGenerator.DEFAULT_PACK_FORMAT),
                "pack_format beklenen deger degil: " + mcmeta);
        assertTrue(mcmeta.contains("supported_formats"),
                "supported_formats araligi yazilmamis: " + mcmeta);
    }

    /**
     * Zincirin TAMAMINI ucdan uca dogrular: item_model -> tanim -> model -> doku.
     * Her halka digerini dogru gostermeli ve gosterilen her dosya zip'te olmali.
     *
     * "item/" disinda bir klasordeki doku ozellikle deneniyor: layer0 uretimi bir
     * zamanlar replace("item/", "") yapip basa yeniden "item/" ekliyordu, bu da
     * boyle bir dokuyu yanlis adresliyor ve "myitem/kilic.png" gibi bir yolu
     * "mykilic" olarak bozuyordu.
     */
    @Test
    void wholeChainResolvesForAnyTextureFolder(@TempDir Path dataFolder) throws Exception {
        Files.createDirectories(dataFolder.resolve("contents/aethel/textures/item"));
        Files.createDirectories(dataFolder.resolve("contents/aethel/textures/silahlar"));
        Files.write(dataFolder.resolve("contents/aethel/textures/item/a.png"), pngBytes());
        Files.write(dataFolder.resolve("contents/aethel/textures/silahlar/b.png"), pngBytes());

        PackPaths paths = new PackPaths(dataFolder.toFile());
        List<CustomItem> items = List.of(
                item("a", "item/a.png"), item("b", "silahlar/b.png"));
        new PackGenerator(paths, new PackIndex(paths.index()), Logger.getLogger("test"))
                .generate(items);

        Set<String> entries = new HashSet<>();
        try (ZipFile zip = new ZipFile(paths.generatedZip())) {
            zip.stream().forEach(entry -> entries.add(entry.getName()));
        }
        for (CustomItem definition : items) {
            // 1. item_model -> tanim dosyasi
            String defPath = "assets/" + definition.modelKey().replace(":", "/items/") + ".json";
            assertTrue(entries.contains(defPath), "tanim yok: " + defPath + " / " + entries);

            // 2. tanim -> model dosyasi
            String defJson = Files.readString(new File(paths.assets(),
                    "aethel/items/" + definition.id() + ".json").toPath());
            assertTrue(defJson.contains(definition.modelPath()),
                    "tanim yanlis modeli gosteriyor: " + defJson);
            String modelPath = "assets/" + definition.modelPath().replace(":", "/models/") + ".json";
            assertTrue(entries.contains(modelPath), "model yok: " + modelPath);

            // 3. model -> doku kimligi, ve o kimligin gosterdigi dosya
            String modelJson = Files.readString(new File(paths.assets(),
                    "aethel/models/item/" + definition.id() + ".json").toPath());
            String expectedTexture = "aethel:"
                    + definition.texture().substring(0, definition.texture().length() - 4);
            assertTrue(modelJson.contains(expectedTexture),
                    "model yanlis dokuyu gosteriyor: " + modelJson
                            + " (beklenen " + expectedTexture + ")");
            String texturePath = "assets/aethel/textures/" + definition.texture();
            assertTrue(entries.contains(texturePath), "doku yok: " + texturePath);
        }
    }

    /** 1x1 saydam PNG. */
    private static byte[] pngBytes() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    }
}
