package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Meslek servisi. Ayni anda tutulabilecek meslek sayisi sinirlidir; amac oyuncularin
 * her seyi tek basina yapmasini engelleyip ekonomide uzmanlasma ve ticaret olusturmak.
 */
public interface JobService {

    /** Bir meslek tanimi. */
    record Job(String id, String displayName, int maxLevel,
               Map<String, Map<String, Reward>> actions, Map<Integer, List<String>> perks) {}

    /** Bir eylemin verdigi kazanc. */
    record Reward(double experience, double money) {}

    List<Job> jobs();

    java.util.Optional<Job> job(String id);

    /** Oyuncunun sahip oldugu meslekler ve seviyeleri. */
    Map<String, Integer> jobsOf(UUID player);

    JoinResult join(Player player, String jobId);

    boolean leave(Player player, String jobId);

    double experience(UUID player, String jobId);

    int level(UUID player, String jobId);

    /** Bir eylemi isler: XP ve para verir, seviye atlarsa bildirir. */
    void handleAction(Player player, String actionType, String target);

    /** Meslek birakmanin soguma bitisine kalan sure (ms). */
    long leaveCooldown(UUID player);

    /** Katilma denemesinin sonucu. */
    enum JoinResult {
        OK,
        ALREADY_JOINED,
        LIMIT_REACHED,
        ON_COOLDOWN,
        UNKNOWN_JOB
    }
}
