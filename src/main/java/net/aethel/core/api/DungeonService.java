package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dungeon servisi. Her dungeon ayri bir boyutta (dunyada) canlanir; cekirdegi
 * kirilinca cokme baslar ve sure dolunca dunya, icindekilerle birlikte silinir.
 */
public interface DungeonService {

    /** Bir dungeon tanimi (tema, oda havuzu, zorluk, boyut kurallari). */
    record DungeonDefinition(String id, String displayName, String difficultyId,
                             int minRooms, int maxRooms, int gridRadius,
                             List<String> roomPool, String coreRoom, String entranceRoom,
                             int collapseSeconds, int maxPlayers) {}

    /** Calisan bir dungeon ornegi. */
    record Instance(UUID id, String definitionId, String worldName, State state,
                    long createdAt, List<UUID> players) {}

    /** Ornegin yasam dongusu. */
    enum State {
        /** Dunya olusturuluyor, harita yaziliyor. */
        GENERATING,
        /** Oyuncular icerde, dungeon aktif. */
        ACTIVE,
        /** Cekirdek kirildi, geri sayim isliyor. */
        COLLAPSING,
        /** Dunya bosaltildi ve silindi. */
        DESTROYED
    }

    List<DungeonDefinition> definitions();

    Optional<DungeonDefinition> definition(String id);

    /** Oyuncu (ve varsa partisi) icin yeni bir ornek acar ve iceri alir. */
    Optional<Instance> enter(Player player, String definitionId);

    /** Oyuncuyu cikisa gonderir; cikis tek yonludur, geri donulemez. */
    void exit(Player player);

    Optional<Instance> instanceOf(UUID player);

    List<Instance> instances();

    /** Cekirdek kirildiginda cagrilir; cokme geri sayimini baslatir. */
    void breakCore(Instance instance, Player breaker);

    /** Cokme bitince ornegi ve dunyasini yok eder. */
    void destroy(UUID instanceId);

    /** Tasarim dunyasindaki secili chunk'lari oda sablonu olarak disari verir. */
    boolean exportRoom(Player designer, String roomId);

    int reload();
}
