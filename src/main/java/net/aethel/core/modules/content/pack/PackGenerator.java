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

    /** 1.21.x icin pack_format. Surum degisince tek yerden guncellenir. */
    private static final int PACK_FORMAT = 46;

    private final PackPaths paths;
    private final PackIndex index;
    private final PackWriter writer = new PackWriter();
    private final BlockbenchConverter blockbench;
    private final FontGenerator fonts = new FontGenerator();
    private final Logger log;

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
    public Result generate(Collection<CustomItem> items) {
        long start = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();
        try {
            writer.clean(paths.packTree());
            writer.mcmeta(paths.packTree(), PACK_FORMAT, "AethelCore uretilmis paket");

            int files = 0;
            files += writeItems(items, warnings);
            files += copyTextures(warnings);
            files += convertBlueprints(warnings);
            files += writeFonts(warnings);

            String hash = writer.zip(paths.packTree(), paths.generatedZip());
            Files.writeString(paths.hashFile().toPath(), hash);
            index.save(log);

            long millis = System.currentTimeMillis() - start;
            log.info("Pack uretildi: " + files + " dosya, " + millis + " ms, sha1=" + hash);
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
            textures.addProperty("layer0", item.namespace() + ":item/"
                    + item.texture().replace("item/", "").replace(".png", ""));
            model.add("textures", textures);
            writer.json(new File(paths.assets(),
                    item.namespace() + "/models/item/" + item.id() + ".json"), model);
            count++;

            // 1.21.4+ item_model tanimi: modeli kimlikle baglar, CMD numarasi gerekmez.
            JsonObject definition = new JsonObject();
            JsonObject modelRef = new JsonObject();
            modelRef.addProperty("type", "minecraft:model");
            modelRef.addProperty("model", item.namespace() + ":item/" + item.id());
            definition.add("model", modelRef);
            writer.json(new File(paths.assets(),
                    item.namespace() + "/items/" + item.id() + ".json"), definition);
            count++;
        }
        return count;
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
