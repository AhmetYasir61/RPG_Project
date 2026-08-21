package net.aethel.core.modules.dungeon.design;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aethel.core.modules.dungeon.blueprint.RoomTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Oda sablonlarinin diske yazilmasi ve okunmasi. JSON kullaniliyor: panelden ve
 * web arayuzunden ayni dosyayi okuyup duzenleyebilmek icin insan tarafindan
 * okunabilir bir bicim gerekiyor.
 */
public final class RoomStore {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File folder;
    private final Logger log;

    public RoomStore(File dataFolder, Logger log) {
        this.folder = new File(dataFolder, "dungeons/rooms");
        this.log = log;
        if (!folder.exists()) folder.mkdirs();
    }

    public void save(RoomTemplate template) {
        File file = new File(folder, template.id() + ".json");
        try {
            Files.writeString(file.toPath(), gson.toJson(template), StandardCharsets.UTF_8);
            log.info("Oda sablonu kaydedildi: " + template.id()
                    + " (kapi maskesi " + template.doorMask()
                    + ", " + template.markers().size() + " isaret)");
        } catch (IOException e) {
            log.log(Level.SEVERE, "Oda sablonu yazilamadi: " + template.id(), e);
        }
    }

    /** Tum sablonlari yukler; bozuk bir dosya digerlerini engellemez. */
    public Map<String, RoomTemplate> loadAll() {
        Map<String, RoomTemplate> result = new LinkedHashMap<>();
        File[] files = folder.listFiles(file -> file.getName().endsWith(".json"));
        if (files == null) return result;

        for (File file : files) {
            try {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                RoomTemplate template = gson.fromJson(json, RoomTemplate.class);
                if (template != null) result.put(template.id(), template);
            } catch (IOException | RuntimeException e) {
                log.warning("Oda sablonu okunamadi: " + file.getName() + " (" + e.getMessage() + ")");
            }
        }
        return result;
    }

    public boolean delete(String roomId) {
        return new File(folder, roomId + ".json").delete();
    }

    public File folder() {
        return folder;
    }
}
