package net.aethel.core.modules.dungeon.design;

import net.aethel.core.modules.dungeon.blueprint.Direction4;
import net.aethel.core.modules.dungeon.blueprint.RoomTemplate;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tasarim dunyasindaki bir chunk'i oda sablonuna cevirir. Isaret bloklari (mob, loot,
 * cekirdek) taranip sablonun marker listesine gecer ve sablondan silinir.
 */
public final class RoomCapture {

    /**
     * Isaret bloklari: tasarimci bunlari koyar, export sirasinda okunup marker'a
     * cevrilir ve blok olarak KAYDEDILMEZ. Boylece dungeon icinde isaret blogu
     * gorunmez ve icerik tamamen modullerimizden gelir.
     */
    private static final Map<Material, RoomTemplate.MarkerType> MARKER_BLOCKS = Map.of(
            Material.RED_WOOL, RoomTemplate.MarkerType.MOB,
            Material.YELLOW_WOOL, RoomTemplate.MarkerType.LOOT,
            Material.PURPLE_WOOL, RoomTemplate.MarkerType.CORE,
            Material.LIME_WOOL, RoomTemplate.MarkerType.ENTRANCE,
            Material.LIGHT_BLUE_WOOL, RoomTemplate.MarkerType.EXIT,
            Material.ORANGE_WOOL, RoomTemplate.MarkerType.NPC,
            Material.WHITE_WOOL, RoomTemplate.MarkerType.SPAWN,
            Material.MAGENTA_WOOL, RoomTemplate.MarkerType.HOLOGRAM);

    /** Kapi tespiti icin kullanilan blok; chunk kenarinda arandigi yer yonu belirler. */
    private static final Material DOOR_MARKER = Material.BLUE_WOOL;

    private RoomCapture() {}

    /**
     * Chunk'i tarar. minY ve height, odanin dikey araligidir; tasarim dunyasi duz
     * oldugu icin bu aralik disi bloklarla ugrasilmaz ve dosya kucuk kalir.
     */
    public static RoomTemplate capture(String roomId, Chunk chunk, int minY, int height,
                                       List<String> tags) {
        World world = chunk.getWorld();
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        List<String> palette = new ArrayList<>();
        int[] blocks = new int[16 * 16 * height];
        List<RoomTemplate.Marker> markers = new ArrayList<>();
        int doorMask = 0;

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Block block = world.getBlockAt(baseX + x, minY + y, baseZ + z);
                    Material material = block.getType();

                    RoomTemplate.MarkerType markerType = MARKER_BLOCKS.get(material);
                    if (markerType != null) {
                        markers.add(new RoomTemplate.Marker(markerType, x, y, z, ""));
                        blocks[(y * 16 + z) * 16 + x] = index(paletteIndex, palette, "minecraft:air");
                        continue;
                    }
                    if (material == DOOR_MARKER) {
                        doorMask |= doorDirection(x, z).bit();
                        blocks[(y * 16 + z) * 16 + x] = index(paletteIndex, palette, "minecraft:air");
                        continue;
                    }
                    blocks[(y * 16 + z) * 16 + x] =
                            index(paletteIndex, palette, block.getBlockData().getAsString());
                }
            }
        }
        return new RoomTemplate(roomId, height, minY, palette, blocks, doorMask,
                markers, tags, Map.of());
    }

    /** Kapi isareti chunk'in hangi kenarina yakinsa o yonu verir. */
    private static Direction4 doorDirection(int x, int z) {
        int distanceNorth = z;
        int distanceSouth = 15 - z;
        int distanceWest = x;
        int distanceEast = 15 - x;
        int minimum = Math.min(Math.min(distanceNorth, distanceSouth),
                Math.min(distanceWest, distanceEast));

        if (minimum == distanceNorth) return Direction4.NORTH;
        if (minimum == distanceSouth) return Direction4.SOUTH;
        if (minimum == distanceWest) return Direction4.WEST;
        return Direction4.EAST;
    }

    /** Palet tekrari onler: ayni blok metni bir kez saklanir. */
    private static int index(Map<String, Integer> paletteIndex, List<String> palette, String data) {
        Integer existing = paletteIndex.get(data);
        if (existing != null) return existing;
        paletteIndex.put(data, palette.size());
        palette.add(data);
        return palette.size() - 1;
    }
}
