package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gorev servisi. Gorevler zincirlenebilir; ilerleme profil niteliklerinde tutulur
 * ve diyalog sistemi uzerinden verilip teslim edilir.
 */
public interface QuestService {

    /** Bir gorev tanimi. */
    record Quest(String id, String displayName, List<String> description,
                 List<Objective> objectives, List<String> rewards,
                 String nextQuest, int requiredLevel, boolean repeatable) {}

    /** Tek bir hedef. */
    record Objective(ObjectiveType type, String target, int amount, String description) {}

    /** Hedef tipleri. */
    enum ObjectiveType {
        KILL,
        COLLECT,
        CRAFT,
        TRAVEL,
        TALK,
        REACH_LEVEL
    }

    Optional<Quest> quest(String id);

    List<Quest> quests();

    boolean start(Player player, String questId);

    /** Oyuncunun aktif gorevleri ve hedef ilerlemeleri. */
    Map<String, Map<Integer, Integer>> activeQuests(UUID player);

    List<String> completedQuests(UUID player);

    /** Bir olay gorev ilerlemesine islenir; tamamlananlar otomatik teslim edilir. */
    void progress(Player player, ObjectiveType type, String target, int amount);

    boolean abandon(Player player, String questId);

    int reload();
}
