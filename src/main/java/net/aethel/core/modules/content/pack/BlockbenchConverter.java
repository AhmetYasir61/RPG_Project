package net.aethel.core.modules.content.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Blockbench .bbmodel dosyasini vanilla model JSON'una cevirir. Kaynak dosya silinmez:
 * modeli duzenlemek icin kimsenin uretilen JSON'a dokunmasi gerekmez.
 */
public final class BlockbenchConverter {

    /** Blockbench koordinatlari -16..32 araligindadir; vanilla ayni araligi kabul eder. */
    private static final float MIN_COORD = -16f;
    private static final float MAX_COORD = 32f;

    private final Logger log;

    public BlockbenchConverter(Logger log) {
        this.log = log;
    }

    /** Cevirinin sonucu: model JSON'u ve icinden cikan texture dosyalari. */
    public record Result(JsonObject model, List<Texture> textures, List<String> warnings) {}

    /** bbmodel icine gomulu base64 texture. */
    public record Texture(String name, byte[] data) {}

    public Result convert(Path bbmodel, String namespace) throws IOException {
        JsonObject source = JsonParser.parseString(
                Files.readString(bbmodel, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> warnings = new ArrayList<>();

        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", extractTextureRefs(source, namespace, warnings));
        model.add("elements", convertElements(source, warnings));

        JsonObject display = source.getAsJsonObject("display");
        if (display != null) model.add("display", display);

        return new Result(model, extractTextures(source, warnings), warnings);
    }

    /**
     * Blockbench "elements" dizisi vanilla ile buyuk olcude ortustugu icin dogrudan
     * aktarilir; yalnizca sinir disi koordinatlar kirpilir ve mesh gibi desteklenmeyen
     * tipler atlanip uyari verilir — uretim tek bir hatali model yuzunden durmaz.
     */
    private JsonArray convertElements(JsonObject source, List<String> warnings) {
        JsonArray result = new JsonArray();
        JsonArray elements = source.getAsJsonArray("elements");
        if (elements == null) return result;

        for (JsonElement element : elements) {
            JsonObject cube = element.getAsJsonObject();
            String type = cube.has("type") ? cube.get("type").getAsString() : "cube";
            if (!"cube".equals(type)) {
                warnings.add("Desteklenmeyen eleman tipi atlandi: " + type);
                continue;
            }
            JsonObject converted = new JsonObject();
            converted.add("from", clamp(cube.getAsJsonArray("from")));
            converted.add("to", clamp(cube.getAsJsonArray("to")));
            if (cube.has("rotation")) converted.add("rotation", cube.get("rotation"));
            if (cube.has("faces")) converted.add("faces", cube.get("faces"));
            result.add(converted);
        }
        return result;
    }

    private JsonArray clamp(JsonArray coords) {
        JsonArray result = new JsonArray();
        if (coords == null) return result;
        for (JsonElement value : coords) {
            result.add(Math.max(MIN_COORD, Math.min(MAX_COORD, value.getAsFloat())));
        }
        return result;
    }

    private JsonObject extractTextureRefs(JsonObject source, String namespace, List<String> warnings) {
        JsonObject refs = new JsonObject();
        JsonArray textures = source.getAsJsonArray("textures");
        if (textures == null) {
            warnings.add("Modelde texture tanimi yok");
            return refs;
        }
        for (int i = 0; i < textures.size(); i++) {
            JsonObject texture = textures.get(i).getAsJsonObject();
            String name = texture.has("name")
                    ? texture.get("name").getAsString().replace(".png", "")
                    : "texture" + i;
            refs.addProperty(String.valueOf(i), namespace + ":item/" + name);
        }
        if (refs.size() > 0) refs.add("particle", refs.get("0"));
        return refs;
    }

    /** Gomulu base64 texture'lari cikarir; harici dosyalar ayrica kopyalanir. */
    private List<Texture> extractTextures(JsonObject source, List<String> warnings) {
        List<Texture> result = new ArrayList<>();
        JsonArray textures = source.getAsJsonArray("textures");
        if (textures == null) return result;

        for (int i = 0; i < textures.size(); i++) {
            JsonObject texture = textures.get(i).getAsJsonObject();
            if (!texture.has("source")) continue;
            String data = texture.get("source").getAsString();
            int comma = data.indexOf(',');
            if (comma < 0) {
                warnings.add("Texture verisi okunamadi: " + i);
                continue;
            }
            String name = texture.has("name")
                    ? texture.get("name").getAsString().replace(".png", "")
                    : "texture" + i;
            result.add(new Texture(name, java.util.Base64.getDecoder()
                    .decode(data.substring(comma + 1))));
        }
        return result;
    }
}
