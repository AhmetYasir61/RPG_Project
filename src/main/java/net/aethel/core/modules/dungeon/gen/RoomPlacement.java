package net.aethel.core.modules.dungeon.gen;

/**
 * Izgaranin bir hucresine yerlestirilmis oda: sablon kimligi, donus ve rolu.
 * Chunk koordinati hucre koordinatiyla birebir eslesir (1 oda = 1 chunk).
 */
public record RoomPlacement(int cellX, int cellZ, String roomId, int quarterTurns, Role role) {

    /** Odanin dungeon icindeki islevi. */
    public enum Role {
        ENTRANCE,
        CORRIDOR,
        CHAMBER,
        TREASURE,
        CORE
    }

    /** Hucre anahtarini tek bir long'a paketler; harita aramasi ucuzlar. */
    public long key() {
        return cellKey(cellX, cellZ);
    }

    public static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }
}
