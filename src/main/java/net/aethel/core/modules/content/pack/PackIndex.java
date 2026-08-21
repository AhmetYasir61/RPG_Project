package net.aethel.core.modules.content.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Tahsis edilen kimliklerin kalici kaydi. Bir bloga atanan note block state'i ya da
 * bir font parcasina atanan karakter ASLA degismemelidir; degisirse dunyada duran
 * bloklar baska bloga, menuler bozuk karakterlere doner.
 */
public final class PackIndex {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File file;

    /** Tahsis tablolari ve kaynak dosya hash'leri. */
    private Data data = new Data();

    /** Diske yazilan indeks yapisi. */
    static final class Data {
        Map<String, String> blockStates = new LinkedHashMap<>();
        Map<String, Integer> fontChars = new LinkedHashMap<>();
        Map<String, Integer> modelData = new LinkedHashMap<>();
        Map<String, String> sourceHashes = new HashMap<>();
        /** Silinen iceriklerin slotlari; hemen yeniden kullanilmaz. */
        Map<String, String> graveyard = new LinkedHashMap<>();
        int nextFontChar = 0xE000;
        int nextModelData = 1000;
    }

    public PackIndex(File file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Data loaded = gson.fromJson(json, new TypeToken<Data>() {}.getType());
            if (loaded != null) this.data = loaded;
        } catch (IOException | RuntimeException e) {
            // Bozuk indeks, tahsisleri kaybetmemek icin yedeklenir ve sifirdan baslanir.
            file.renameTo(new File(file.getParentFile(), file.getName() + ".broken"));
        }
    }

    public void save(Logger log) {
        try {
            Files.writeString(file.toPath(), gson.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warning("Pack indeksi yazilamadi: " + e.getMessage());
        }
    }

    /** Blok icin (instrument, note, powered) uclusu; bir kez tahsis edilir ve korunur. */
    public String blockState(String blockId, java.util.function.Supplier<String> allocator) {
        return data.blockStates.computeIfAbsent(blockId, key -> allocator.get());
    }

    public java.util.Collection<String> usedBlockStates() {
        return data.blockStates.values();
    }

    /** Font parcasi icin ozel kullanim alani karakteri (U+E000...). */
    public int fontChar(String key) {
        return data.fontChars.computeIfAbsent(key, ignored -> data.nextFontChar++);
    }

    /** Geriye donuk CustomModelData numarasi. */
    public int modelData(String key) {
        return data.modelData.computeIfAbsent(key, ignored -> data.nextModelData++);
    }

    /** Artimli uretim: dosya degismediyse yeniden islenmez. */
    public boolean unchanged(String path, String hash) {
        return hash.equals(data.sourceHashes.get(path));
    }

    public void remember(String path, String hash) {
        data.sourceHashes.put(path, hash);
    }

    public void forget(String path) {
        data.sourceHashes.remove(path);
    }

    /** Silinen icerigin slotu mezarliga tasinir; eski dunyalar yanlis eslesmesin diye. */
    public void bury(String contentId) {
        String state = data.blockStates.remove(contentId);
        if (state != null) data.graveyard.put(contentId, state);
    }

    public Map<String, Integer> fontChars() {
        return Map.copyOf(data.fontChars);
    }
}
