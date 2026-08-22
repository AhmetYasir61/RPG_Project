package net.aethel.core.content;

import net.aethel.core.api.CustomItem;
import net.aethel.core.modules.content.pack.PackGenerator;
import net.aethel.core.modules.content.pack.PackIndex;
import net.aethel.core.modules.content.pack.PackPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
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
                null, null, List.of(), null);
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

    /** Soket alabilen bir item; varyant uretimi testleri icin. */
    private static CustomItem socketed(String id, String texture, int stages) {
        List<Integer> thresholds = new java.util.ArrayList<>();
        for (int i = 0; i < stages; i++) thresholds.add(i * 25);
        return new CustomItem(id, "aethel", "<white>" + id, List.of(), "IRON_SWORD",
                texture, "COMMON", 0, Map.of(), Map.of(), null, false, false,
                null, null, List.of(),
                new CustomItem.Socketing(1, List.copyOf(thresholds), "alev_kilici"));
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

    /**
     * Soket varyantlari: her (tas x asama) icin boyanmis doku, model ve
     * item_model tanimi uretilmeli; asama 0 icin UYRETILMEMELI (taban gorunum
     * zaten var olan dokudur).
     */
    @Test
    void socketVariantsAreGeneratedForEveryStageButZero(@TempDir Path dataFolder) throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        writeStrip(textures.resolve("bakir_kilic.png"));

        PackPaths paths = new PackPaths(dataFolder.toFile());
        PackGenerator generator = new PackGenerator(paths, new PackIndex(paths.index()),
                Logger.getLogger("test"));
        generator.stones(List.of(new net.aethel.core.api.SocketStone("ates_tasi", "aethel",
                "Ates", List.of(), "FIREWORK_STAR", "item/ates_tasi.png", "#ff6a00",
                Map.of(), "FLAME", List.of())));

        // 4 asama: 0,1,2,3 -> yalnizca 1..3 icin varyant beklenir.
        generator.generate(List.of(socketed("bakir_kilic", "item/bakir_kilic.png", 4)));

        Set<String> entries = new HashSet<>();
        try (ZipFile zip = new ZipFile(paths.generatedZip())) {
            zip.stream().forEach(entry -> entries.add(entry.getName()));
        }
        for (int stage = 1; stage <= 3; stage++) {
            String variant = "bakir_kilic_ates_tasi_" + stage;
            assertTrue(entries.contains("assets/aethel/textures/item/" + variant + ".png"),
                    "varyant dokusu yok: " + variant);
            assertTrue(entries.contains("assets/aethel/items/" + variant + ".json"),
                    "varyant item_model tanimi yok: " + variant);
        }
        assertTrue(!entries.contains("assets/aethel/items/bakir_kilic_ates_tasi_0.json"),
                "asama 0 icin gereksiz varyant uretilmis");
    }

    /**
     * Boyamanin iki degismez kurali:
     *
     * 1. DIS HAT hicbir asamada boyanmaz. Saf siyah da teknik olarak "gri"dir;
     *    parlaklik alt siniri olmasaydi kilicin hatti da renge boyanir ve item
     *    dagilmis gorunurdu.
     * 2. Renk soket cekirdeginden YAYILIR: ilk asamada uzaktaki pikseller
     *    degismez, son asamada bicagin tamami rengi alir. "Basta az parliyor,
     *    gide gide her yani aleve donuyor" davranisi budur.
     */
    @Test
    void tintSpreadsFromTheSocketAndNeverTouchesTheOutline(@TempDir Path dataFolder)
            throws Exception {
        Path textures = dataFolder.resolve("contents/aethel/textures/item");
        Files.createDirectories(textures);
        writeStrip(textures.resolve("bakir_kilic.png"));

        var early = variant(dataFolder, 4, 1);   // 4 asamali itemin 1. asamasi
        var last = variant(dataFolder, 4, 3);    // son asama

        // 1) Dis hat her iki asamada da dokunulmamis.
        assertEquals(0xFF000000, early.getRGB(0, 0), "dis hat ilk asamada boyanmis");
        assertEquals(0xFF000000, last.getRGB(0, 0), "dis hat son asamada boyanmis");

        // 2) Cekirdek (gri) ilk asamada bile renklenmis.
        int core = early.getRGB(4, 0);
        assertTrue(((core >> 16) & 0xFF) > (core & 0xFF) + 30,
                "soket cekirdegi ilk asamada renklenmemis: " + Integer.toHexString(core));

        // 3) Uzaktaki bakir ilk asamada DEGISMEMIS, son asamada renk almis.
        assertEquals(0xFFC87C4A, early.getRGB(7, 0),
                "renk ilk asamada cok uzaga tasmis");
        assertTrue(last.getRGB(7, 0) != 0xFFC87C4A,
                "renk son asamada bicagin ucuna hic ulasmamis");
    }

    /** Verilen asama icin varyanti uretip okur. */
    private static java.awt.image.BufferedImage variant(Path dataFolder, int stages, int stage)
            throws IOException {
        PackPaths paths = new PackPaths(dataFolder.toFile());
        PackGenerator generator = new PackGenerator(paths, new PackIndex(paths.index()),
                Logger.getLogger("test"));
        generator.stones(List.of(new net.aethel.core.api.SocketStone("ates_tasi", "aethel",
                "Ates", List.of(), "FIREWORK_STAR", "item/ates_tasi.png", "#ff6a00",
                Map.of(), "FLAME", List.of())));
        generator.generate(List.of(socketed("bakir_kilic", "item/bakir_kilic.png", stages)));

        return javax.imageio.ImageIO.read(new File(paths.assets(),
                "aethel/textures/item/bakir_kilic_ates_tasi_" + stage + ".png"));
    }

    /** 9x1 serit: [dis hat][bakir x3][GRI cekirdek][bakir x3][dis hat] */
    private static void writeStrip(Path target) throws IOException {
        var image = new java.awt.image.BufferedImage(9, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 9; x++) image.setRGB(x, 0, 0xFFC87C4A);
        image.setRGB(0, 0, 0xFF000000);
        image.setRGB(8, 0, 0xFF000000);
        image.setRGB(4, 0, 0xFF7A7A7E);
        javax.imageio.ImageIO.write(image, "png", target.toFile());
    }

    /** 1x1 saydam PNG. */
    private static byte[] pngBytes() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    }
}
