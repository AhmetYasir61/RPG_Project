package net.aethel.core.modules.content.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aethel.core.api.CustomItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resource pack uretim hatti: contents/ ve blueprints/ taranir, vanilla JSON'lari
 * yazilir, zip ve SHA-1 uretilir. Tamami sanal thread'de calisir, ana thread'i tutmaz.
 */
public final class PackGenerator {

    /**
     * pack_format 1.21.11 icin 75'tir. Yanlis bir deger sessiz bir hatadir:
     * istemci paketi "eski surum" sayar, item_model tanimlarini (assets/<ns>/items)
     * yeni kurallarla okumaz ve item mor-siyah kare gorunur -- sunucu gunluguene
     * hicbir sey yazilmadan. Bu yuzden deger ayardan gelir ve her uretimde loglanir.
     */
    public static final int DEFAULT_PACK_FORMAT = 75;

    private int packFormat = DEFAULT_PACK_FORMAT;

    private final PackPaths paths;
    private final PackIndex index;
    private final PackWriter writer = new PackWriter();
    private final BlockbenchConverter blockbench;
    private final FontGenerator fonts = new FontGenerator();
    private final Logger log;

    /** Ayardan gelen pack_format; 0 ya da negatif deger varsayilana doner. */
    public void packFormat(int format) {
        this.packFormat = format > 0 ? format : DEFAULT_PACK_FORMAT;
    }

    public int packFormat() {
        return packFormat;
    }

    public PackGenerator(PackPaths paths, PackIndex index, Logger log) {
        this.paths = paths;
        this.index = index;
        this.log = log;
        this.blockbench = new BlockbenchConverter(log);
    }

    /** Uretim sonucu: hash, dosya sayisi ve toplanan uyarilar. */
    public record Result(String sha1, int fileCount, List<String> warnings, long millis) {}

    /**
     * Tam uretim. Once eski agac temizlenir: artik dosyalarin pack'te kalmasi,
     * silinmis bir icerigin oyuncularda gorunmeye devam etmesine yol acar.
     */
    /** Soket taslari; varyant uretimi icin uretim oncesi verilir. */
    private Collection<net.aethel.core.api.SocketStone> stones = java.util.List.of();

    public void stones(Collection<net.aethel.core.api.SocketStone> stones) {
        this.stones = stones == null ? java.util.List.of() : stones;
    }

    public Result generate(Collection<CustomItem> items) {
        long start = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();
        try {
            writer.clean(paths.packTree());
            writer.mcmeta(paths.packTree(), packFormat, "AethelCore uretilmis paket");

            int files = 0;
            files += writeItems(items, warnings);
            files += writeSocketVariants(items, warnings);
            files += copyTextures(warnings);
            files += convertBlueprints(warnings);
            files += writeFonts(warnings);

            String hash = writer.zip(paths.packTree(), paths.generatedZip());
            Files.writeString(paths.hashFile().toPath(), hash);
            index.save(log);

            long millis = System.currentTimeMillis() - start;
            log.info("Pack uretildi: " + files + " dosya, " + millis + " ms, "
                    + "pack_format=" + packFormat + ", sha1=" + hash);
            return new Result(hash, files, warnings, millis);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Pack uretimi basarisiz", e);
            return new Result(null, 0, List.of("Uretim basarisiz: " + e.getMessage()),
                    System.currentTimeMillis() - start);
        }
    }

    /** Her item icin model JSON'u ve item_model tanimi yazilir. */
    private int writeItems(Collection<CustomItem> items, List<String> warnings) throws IOException {
        int count = 0;
        for (CustomItem item : items) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            // Doku kimligi, dokunun contents/<ns>/textures/ ALTINDAKI yolunun
            // aynisidir; pack'e de oraya kopyalaniyor. Burada bir zamanlar
            // replace("item/", "") + yeniden "item/" ekleme vardi: "item/" disinda
            // bir klasordeki doku yanlis adreslenip mor-siyah kare veriyor,
            // "myitem/kilic.png" gibi bir yol ise "mykilic" olarak bozuluyordu.
            textures.addProperty("layer0", textureKey(item));
            model.add("textures", textures);
            writer.json(new File(paths.assets(),
                    item.namespace() + "/models/item/" + item.id() + ".json"), model);
            count++;

            // 1.21.4+ item_model tanimi: modeli kimlikle baglar, CMD numarasi gerekmez.
            JsonObject definition = new JsonObject();
            JsonObject modelRef = new JsonObject();
            modelRef.addProperty("type", "minecraft:model");
            // Tanim MODEL dosyasini gosterir; tanimin kendi adresi item_model'dir.
            // Ikisi ayri yollardir ve karistirilmasi paketi sessizce bozar.
            modelRef.addProperty("model", item.modelPath());
            definition.add("model", modelRef);
            writer.json(new File(paths.assets(),
                    item.namespace() + "/items/" + item.id() + ".json"), definition);
            count++;
        }
        return count;
    }

    /**
     * Her (soket alabilen item x tas x asama) icin boyanmis bir doku, model ve
     * item_model tanimi uretir.
     *
     * Asama 0 ATLANIR: taban gorunum zaten var olan dokudur, kopyasini uretmek
     * pack'i buyutur ve iki ayni dosyayi bakima birakir.
     */
    private int writeSocketVariants(Collection<CustomItem> items, List<String> warnings)
            throws IOException {
        if (stones.isEmpty()) return 0;
        int count = 0;

        for (CustomItem item : items) {
            if (!item.socketable()) continue;
            File source = new File(paths.contents(),
                    item.namespace() + "/textures/" + item.texture());
            if (!source.isFile()) continue;   // eksik doku zaten ayri uyariliyor

            int lastStage = item.socketing().lastStage();
            for (net.aethel.core.api.SocketStone stone : stones) {
                for (int stage = 1; stage <= lastStage; stage++) {
                    String variant = CustomItem.bare(item.id()) + "_"
                            + CustomItem.bare(stone.id()) + "_" + stage;
                    try {
                        TextureTinter.write(source, new File(paths.assets(),
                                        item.namespace() + "/textures/item/" + variant + ".png"),
                                stone.tintRgb(), stage, lastStage);
                        count++;
                    } catch (IOException e) {
                        warnings.add("Varyant uretilemedi (" + variant + "): " + e.getMessage());
                        continue;
                    }
                    count += writeVariantModel(item, variant);
                }
            }
        }
        return count;
    }

    /** Varyantin model dosyasi ve item_model tanimi. */
    private int writeVariantModel(CustomItem item, String variant) throws IOException {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", item.namespace() + ":item/" + variant);
        model.add("textures", textures);
        writer.json(new File(paths.assets(),
                item.namespace() + "/models/item/" + variant + ".json"), model);

        JsonObject definition = new JsonObject();
        JsonObject modelRef = new JsonObject();
        modelRef.addProperty("type", "minecraft:model");
        modelRef.addProperty("model", item.namespace() + ":item/" + variant);
        definition.add("model", modelRef);
        writer.json(new File(paths.assets(),
                item.namespace() + "/items/" + variant + ".json"), definition);
        return 2;
    }

    /** "aethel:item/alev_kilici" — dosya yolunun birebir karsiligi. */
    private static String textureKey(CustomItem item) {
        String path = item.texture().replace('\\', '/');
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        return item.namespace() + ":" + path;
    }

    /** contents/<ns>/textures altindaki her sey pack'e aynen kopyalanir. */
    private int copyTextures(List<String> warnings) throws IOException {
        int count = 0;
        File[] namespaces = paths.contents().listFiles(File::isDirectory);
        if (namespaces == null) return 0;

        for (File namespace : namespaces) {
            File textures = new File(namespace, "textures");
            if (!textures.isDirectory()) continue;
            try (var walk = Files.walk(textures.toPath())) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    if (!path.toString().endsWith(".png")) continue;
                    String relative = textures.toPath().relativize(path).toString().replace('\\', '/');
                    writer.copy(path, new File(paths.assets(),
                            namespace.getName() + "/textures/" + relative));
                    count++;
                }
            }
        }
        return count;
    }

    /** blueprints/<ns>/*.bbmodel dosyalari vanilla model JSON'una cevrilir. */
    private int convertBlueprints(List<String> warnings) throws IOException {
        int count = 0;
        File[] namespaces = paths.blueprints().listFiles(File::isDirectory);
        if (namespaces == null) return 0;

        for (File namespace : namespaces) {
            File[] files = namespace.listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (file.getName().endsWith(".json")) {
                    writer.copy(file.toPath(), new File(paths.assets(),
                            namespace.getName() + "/models/item/" + file.getName()));
                    count++;
                    continue;
                }
                if (!file.getName().endsWith(".bbmodel")) continue;
                count += convertSingle(namespace.getName(), file, warnings);
            }
        }
        return count;
    }

    private int convertSingle(String namespace, File file, List<String> warnings) {
        try {
            String name = file.getName().replace(".bbmodel", "");
            BlockbenchConverter.Result result = blockbench.convert(file.toPath(), namespace);
            warnings.addAll(result.warnings());

            writer.json(new File(paths.assets(),
                    namespace + "/models/item/" + name + ".json"), result.model());
            int count = 1;
            for (BlockbenchConverter.Texture texture : result.textures()) {
                writer.bytes(new File(paths.assets(),
                        namespace + "/textures/item/" + texture.name() + ".png"), texture.data());
                count++;
            }
            return count;
        } catch (IOException | RuntimeException e) {
            // Tek bir bozuk model tum uretimi durdurmaz; uyari verilip devam edilir.
            warnings.add("bbmodel cevrilemedi: " + file.getName() + " (" + e.getMessage() + ")");
            return 0;
        }
    }

    /** HUD ve GUI parcalari icin font dosyasi. */
    private int writeFonts(List<String> warnings) throws IOException {
        Map<String, FontGenerator.GlyphSpec> specs = FontScanner.scan(paths.contents(), log);
        if (specs.isEmpty()) return 0;

        Map<String, Integer> glyphs = new java.util.LinkedHashMap<>();
        specs.keySet().forEach(key -> glyphs.put(key, index.fontChar(key)));

        JsonObject font = fonts.build(defaultNamespace(), glyphs, specs);
        writer.json(new File(paths.assets(), defaultNamespace() + "/font/default.json"), font);
        return 1;
    }

    private String defaultNamespace() {
        File[] namespaces = paths.contents().listFiles(File::isDirectory);
        return namespaces == null || namespaces.length == 0 ? "aethel" : namespaces[0].getName();
    }

    /** Blok state tahsisi disaridan gorunsun diye: panel bunu gosterir. */
    public PackIndex index() {
        return index;
    }

    public PackPaths paths() {
        return paths;
    }
}
