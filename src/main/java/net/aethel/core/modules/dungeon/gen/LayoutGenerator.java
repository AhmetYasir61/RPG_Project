package net.aethel.core.modules.dungeon.gen;

import net.aethel.core.api.DungeonService.DungeonDefinition;
import net.aethel.core.modules.dungeon.blueprint.Direction4;
import net.aethel.core.modules.dungeon.blueprint.RoomTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chunk izgarasi uzerinde dungeon yerlesimi uretir. Once giristen cekirdege bir
 * ana yol kazilir, sonra bu yola dallar eklenir; boylece her dungeon cozulebilir olur.
 */
public final class LayoutGenerator {

    /**
     * Ana yol once kaziliyor cunku tamamen rastgele buyuyen bir labirentte cekirdek
     * erisilemez bir kosede kalabilir ve dungeon oynanamaz hale gelir. Once
     * giris-cekirdek yolunu garanti edip dallari sonra eklemek bu riski bitirir.
     */
    private static final int MAX_PLACEMENT_ATTEMPTS = 200;

    private final Map<String, RoomTemplate> rooms;

    public LayoutGenerator(Map<String, RoomTemplate> rooms) {
        this.rooms = rooms;
    }

    /** Uretim sonucu: hucre -> yerlesim haritasi ve onemli konumlar. */
    public record Layout(Map<Long, RoomPlacement> placements,
                         RoomPlacement entrance,
                         RoomPlacement core) {}

    public Layout generate(DungeonDefinition definition, long seed) {
        Random random = new Random(seed);
        Map<Long, RoomPlacement> placements = new LinkedHashMap<>();
        Map<Long, Integer> requiredDoors = new HashMap<>();

        int targetRooms = definition.minRooms()
                + random.nextInt(Math.max(1, definition.maxRooms() - definition.minRooms() + 1));

        List<int[]> mainPath = carveMainPath(definition, random, targetRooms);
        int[] entranceCell = mainPath.get(0);
        int[] coreCell = mainPath.get(mainPath.size() - 1);

        // Ana yoldaki her hucrenin komsulariyla kapi paylasmasi gerekiyor.
        for (int i = 0; i < mainPath.size(); i++) {
            int[] cell = mainPath.get(i);
            int mask = 0;
            if (i > 0) mask |= directionBetween(cell, mainPath.get(i - 1));
            if (i < mainPath.size() - 1) mask |= directionBetween(cell, mainPath.get(i + 1));
            requiredDoors.merge(RoomPlacement.cellKey(cell[0], cell[1]), mask, (a, b) -> a | b);
        }

        addBranches(definition, random, mainPath, requiredDoors, targetRooms);
        assignRooms(definition, random, requiredDoors, placements, entranceCell, coreCell);

        return new Layout(placements,
                placements.get(RoomPlacement.cellKey(entranceCell[0], entranceCell[1])),
                placements.get(RoomPlacement.cellKey(coreCell[0], coreCell[1])));
    }

    /** Giristen baslayip rastgele fakat kendini kesmeyen bir ana yol kazar. */
    private List<int[]> carveMainPath(DungeonDefinition definition, Random random, int targetRooms) {
        List<int[]> path = new ArrayList<>();
        Map<Long, Boolean> used = new HashMap<>();
        int[] current = {0, 0};
        path.add(current);
        used.put(RoomPlacement.cellKey(0, 0), true);

        int length = Math.max(3, targetRooms / 2);
        Deque<Direction4> lastDirections = new ArrayDeque<>();

        for (int step = 0; step < length; step++) {
            List<Direction4> options = new ArrayList<>(List.of(Direction4.values()));
            java.util.Collections.shuffle(options, random);
            boolean moved = false;

            for (Direction4 direction : options) {
                int nextX = current[0] + direction.deltaX();
                int nextZ = current[1] + direction.deltaZ();
                if (Math.abs(nextX) > definition.gridRadius()
                        || Math.abs(nextZ) > definition.gridRadius()) continue;
                if (used.containsKey(RoomPlacement.cellKey(nextX, nextZ))) continue;

                current = new int[] {nextX, nextZ};
                used.put(RoomPlacement.cellKey(nextX, nextZ), true);
                path.add(current);
                lastDirections.push(direction);
                moved = true;
                break;
            }
            // Kose kapandiysa yol burada biter; kisa bir dungeon, bozuk bir dungeondan iyidir.
            if (!moved) break;
        }
        return path;
    }

    /** Ana yola yan odalar ekler: hazine, mob odasi, cikmaz koridor. */
    private void addBranches(DungeonDefinition definition, Random random, List<int[]> mainPath,
                             Map<Long, Integer> requiredDoors, int targetRooms) {
        int remaining = targetRooms - mainPath.size();
        int attempts = 0;

        while (remaining > 0 && attempts++ < MAX_PLACEMENT_ATTEMPTS) {
            int[] anchor = mainPath.get(random.nextInt(mainPath.size()));
            Direction4 direction = Direction4.values()[random.nextInt(4)];
            int nextX = anchor[0] + direction.deltaX();
            int nextZ = anchor[1] + direction.deltaZ();

            if (Math.abs(nextX) > definition.gridRadius()
                    || Math.abs(nextZ) > definition.gridRadius()) continue;
            long key = RoomPlacement.cellKey(nextX, nextZ);
            if (requiredDoors.containsKey(key)) continue;

            requiredDoors.merge(RoomPlacement.cellKey(anchor[0], anchor[1]),
                    direction.bit(), (a, b) -> a | b);
            requiredDoors.put(key, direction.opposite().bit());
            remaining--;
        }
    }

    /** Her hucreye, kapi maskesi tutan bir oda sablonu secer. */
    private void assignRooms(DungeonDefinition definition, Random random,
                             Map<Long, Integer> requiredDoors,
                             Map<Long, RoomPlacement> placements,
                             int[] entranceCell, int[] coreCell) {
        long entranceKey = RoomPlacement.cellKey(entranceCell[0], entranceCell[1]);
        long coreKey = RoomPlacement.cellKey(coreCell[0], coreCell[1]);

        requiredDoors.forEach((key, mask) -> {
            int cellX = (int) (key >> 32);
            int cellZ = (int) (key & 0xFFFFFFFFL);

            RoomPlacement.Role role = key == entranceKey ? RoomPlacement.Role.ENTRANCE
                    : key == coreKey ? RoomPlacement.Role.CORE
                    : Integer.bitCount(mask) == 1 ? RoomPlacement.Role.TREASURE
                    : Integer.bitCount(mask) == 2 ? RoomPlacement.Role.CORRIDOR
                    : RoomPlacement.Role.CHAMBER;

            String forced = role == RoomPlacement.Role.ENTRANCE ? definition.entranceRoom()
                    : role == RoomPlacement.Role.CORE ? definition.coreRoom() : null;

            Match match = pick(definition, mask, forced, random);
            placements.put(key, new RoomPlacement(cellX, cellZ,
                    match.roomId(), match.quarterTurns(), role));
        });
    }

    /** Secilen oda ve gereken donus. */
    private record Match(String roomId, int quarterTurns) {}

    /**
     * Kapi maskesini tutan bir oda arar; dondurulmus haller de denenir. Hicbiri
     * tutmazsa havuzdaki ilk oda donussuz kullanilir: eksik bir oda yuzunden tum
     * uretimi durdurmak, sunucuyu bir tanim hatasiyla kilitlemek demektir.
     */
    private Match pick(DungeonDefinition definition, int requiredMask, String forced, Random random) {
        if (forced != null && rooms.containsKey(forced)) {
            RoomTemplate template = rooms.get(forced);
            for (int turns = 0; turns < 4; turns++) {
                if ((template.rotatedMask(turns) & requiredMask) == requiredMask) {
                    return new Match(forced, turns);
                }
            }
            return new Match(forced, 0);
        }
        List<String> pool = new ArrayList<>(definition.roomPool());
        java.util.Collections.shuffle(pool, random);

        for (String roomId : pool) {
            RoomTemplate template = rooms.get(roomId);
            if (template == null) continue;
            for (int turns = 0; turns < 4; turns++) {
                if ((template.rotatedMask(turns) & requiredMask) == requiredMask) {
                    return new Match(roomId, turns);
                }
            }
        }
        return new Match(pool.isEmpty() ? "bos" : pool.get(0), 0);
    }

    private int directionBetween(int[] from, int[] to) {
        for (Direction4 direction : Direction4.values()) {
            if (from[0] + direction.deltaX() == to[0] && from[1] + direction.deltaZ() == to[1]) {
                return direction.bit();
            }
        }
        return 0;
    }
}
