package net.aethel.core.modules.dungeon.blueprint;

import java.util.List;
import java.util.Map;

/**
 * Tek bir chunk'lik oda sablonu. Bloklar palet + indeks dizisi olarak saklanir:
 * 16x16xH icin ham BlockData metni tutmak dosyayi onlarca kat buyutur.
 */
public record RoomTemplate(String id,
                           int height,
                           int minY,
                           List<String> palette,
                           int[] blocks,
                           int doorMask,
                           List<Marker> markers,
                           List<String> tags,
                           Map<String, String> options) {

    /**
     * Odaya yerlestirilen isaretler. Tip, odanin icine ne konacagini soyler ve
     * diger modullerimize baglanir: MOB -> MobService, LOOT -> LootService,
     * CORE -> dungeon cekirdegi, ENTRANCE/EXIT -> gecis noktalari.
     */
    public record Marker(MarkerType type, int x, int y, int z, String value) {}

    /** Isaret tipleri; hepsi mevcut modullerimizin servislerine baglanir. */
    public enum MarkerType {
        SPAWN,
        MOB,
        LOOT,
        CORE,
        ENTRANCE,
        EXIT,
        NPC,
        HOLOGRAM
    }

    /** Blok dizisindeki indeks hesabi: y en yavas degisen eksen. */
    public int index(int x, int y, int z) {
        return (y * 16 + z) * 16 + x;
    }

    public String blockAt(int x, int y, int z) {
        int paletteIndex = blocks[index(x, y, z)];
        return palette.get(paletteIndex);
    }

    public boolean hasDoor(Direction4 direction) {
        return (doorMask & direction.bit()) != 0;
    }

    /** Bu odanin verilen donusle hangi kapi maskesine sahip olacagi. */
    public int rotatedMask(int quarterTurns) {
        return Direction4.rotateMask(doorMask, quarterTurns);
    }

    public boolean hasMarker(MarkerType type) {
        return markers.stream().anyMatch(marker -> marker.type() == type);
    }
}
