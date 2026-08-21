package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * RPG servisi: seviye, XP, statlar ve yetenek agaci. Hasar formulu bu servisten
 * beslenir; moduller stat degerlerini dogrudan profilden okumaz.
 */
public interface RpgService {

    int level(UUID player);

    double experience(UUID player);

    /** Bir sonraki seviyeye gereken toplam XP. */
    double experienceForLevel(int level);

    /** XP ekler; seviye atlarsa olay yayinlanir ve puan verilir. */
    void addExperience(Player player, double amount, String source);

    int stat(UUID player, StatType type);

    void setStat(UUID player, StatType type, int value);

    /** Dagitilmamis yetenek puani. */
    int availablePoints(UUID player);

    /** Bir stata puan harcar; puan yetersizse false doner. */
    boolean spendPoint(Player player, StatType type);

    /** Yetenek agacindaki bir dugumu acar. */
    boolean unlockNode(Player player, String nodeId);

    java.util.Set<String> unlockedNodes(UUID player);

    /** Hesaplanmis toplam degerler (ekipman ve dugum bonuslari dahil). */
    Map<StatType, Integer> effectiveStats(UUID player);

    double maxMana(UUID player);

    double mana(UUID player);

    boolean consumeMana(UUID player, double amount);
}
