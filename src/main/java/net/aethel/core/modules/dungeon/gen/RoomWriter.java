package net.aethel.core.modules.dungeon.gen;

import net.aethel.core.modules.dungeon.blueprint.RoomTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Oda sablonunu dunyaya yazar. Blok verisi bir kez cozulup onbelleklenir; ayni
 * palet girdisi icin her blokta yeniden ayristirmak uretimi kat kat yavaslatir.
 */
public final class RoomWriter {

    private final Map<String, BlockData> blockCache = new HashMap<>();

    /** Yazim sonucu: dunyaya dusen isaretlerin gercek konumlari. */
    public record Written(List<PlacedMarker> markers) {}

    /** Dunya koordinatina oturmus bir isaret. */
    public record PlacedMarker(RoomTemplate.MarkerType type, Location location, String value) {}

    /**
     * Odayi verilen chunk hucresine yazar. quarterTurns 0-3 arasidir; donus
     * hem blok koordinatlarina hem de isaretlere uygulanir, aksi halde dondurulmus
     * odada mob ve sandik yanlis yerde belirir.
     */
    public Written write(World world, RoomTemplate template, int cellX, int cellZ, int quarterTurns) {
        int baseX = cellX << 4;
        int baseZ = cellZ << 4;
        List<PlacedMarker> placed = new ArrayList<>(template.markers().size());

        for (int y = 0; y < template.height(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int[] rotated = rotate(x, z, quarterTurns);
                    String data = template.blockAt(x, y, z);
                    if (data.startsWith("minecraft:air")) continue;

                    world.getBlockAt(baseX + rotated[0], template.minY() + y, baseZ + rotated[1])
                            .setBlockData(blockData(data), false);
                }
            }
        }
        for (RoomTemplate.Marker marker : template.markers()) {
            int[] rotated = rotate(marker.x(), marker.z(), quarterTurns);
            placed.add(new PlacedMarker(marker.type(),
                    new Location(world, baseX + rotated[0] + 0.5,
                            template.minY() + marker.y(), baseZ + rotated[1] + 0.5),
                    marker.value()));
        }
        return new Written(placed);
    }

    /** 16x16 karede saat yonunde 90° donus. */
    private int[] rotate(int x, int z, int quarterTurns) {
        return switch (quarterTurns & 3) {
            case 1 -> new int[] {15 - z, x};
            case 2 -> new int[] {15 - x, 15 - z};
            case 3 -> new int[] {z, 15 - x};
            default -> new int[] {x, z};
        };
    }

    private BlockData blockData(String raw) {
        return blockCache.computeIfAbsent(raw, key -> {
            try {
                return Bukkit.createBlockData(key);
            } catch (IllegalArgumentException e) {
                // Bilinmeyen blok (surum farki, silinmis blok) tas olarak yazilir:
                // odanin tamamini kaybetmektense tek blogu degistirmek yeglenir.
                return Bukkit.createBlockData("minecraft:stone");
            }
        });
    }
}
