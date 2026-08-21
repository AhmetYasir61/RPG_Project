package net.aethel.core.modules.dungeon.blueprint;

/**
 * Izgara uzerindeki dort yon ve kapi maskesi bitleri. Odalar bu maske ile
 * eslestirilir: bir odanin kapisi, komsu odanin kapisina denk gelmek zorundadir.
 */
public enum Direction4 {

    NORTH(0, -1, 1),
    EAST(1, 0, 2),
    SOUTH(0, 1, 4),
    WEST(-1, 0, 8);

    private final int deltaX;
    private final int deltaZ;
    private final int bit;

    Direction4(int deltaX, int deltaZ, int bit) {
        this.deltaX = deltaX;
        this.deltaZ = deltaZ;
        this.bit = bit;
    }

    public int deltaX() { return deltaX; }
    public int deltaZ() { return deltaZ; }
    public int bit() { return bit; }

    public Direction4 opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    /** 90° adimlarla saat yonunde dondurur. */
    public Direction4 rotate(int quarterTurns) {
        Direction4[] order = {NORTH, EAST, SOUTH, WEST};
        int index = (ordinal() + quarterTurns) % 4;
        return order[index];
    }

    /**
     * Bir kapi maskesini dondurur. Oda blogunu dondurdugumuzde kapilarin da
     * donmesi gerekir; aksi halde dondurulmus oda komsulariyla eslesmez.
     */
    public static int rotateMask(int mask, int quarterTurns) {
        int rotated = 0;
        for (Direction4 direction : values()) {
            if ((mask & direction.bit()) != 0) {
                rotated |= direction.rotate(quarterTurns).bit();
            }
        }
        return rotated;
    }

    public static Direction4 fromBit(int bit) {
        for (Direction4 direction : values()) {
            if (direction.bit() == bit) return direction;
        }
        return NORTH;
    }
}
