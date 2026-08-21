package net.aethel.core.modules.rpg;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Yetenek agaci tanimi ve on kosul dogrulamasi. Dugumler YAML'den okunur, panelden
 * duzenlenir; ACTIVE dugumler daima bir particle skill'ine baglanir.
 */
final class SkillTree {

    /** Tek bir agac dugumu. */
    record Node(String id, String displayName, List<String> description, Type type,
                int cost, int requiredLevel, List<String> requires, String skillId,
                Map<String, Double> bonuses, int slot) {}

    /** Dugum tipi. */
    enum Type {
        /** Kalici stat bonusu. */
        PASSIVE,
        /** Kullanilabilir yetenek (particle skill). */
        ACTIVE,
        /** Baska bir dugumun etkisini degistirir. */
        MODIFIER
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Logger log;

    SkillTree(Logger log) {
        this.log = log;
    }

    void load(File file) {
        nodes.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("nodes");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            Map<String, Double> bonuses = new LinkedHashMap<>();
            ConfigurationSection bonusSection = section.getConfigurationSection("bonuses");
            if (bonusSection != null) {
                bonusSection.getKeys(false).forEach(key ->
                        bonuses.put(key, bonusSection.getDouble(key)));
            }
            nodes.put(id, new Node(id,
                    section.getString("display", id),
                    section.getStringList("description"),
                    type(section.getString("type", "PASSIVE")),
                    section.getInt("cost", 1),
                    section.getInt("required-level", 1),
                    section.getStringList("requires"),
                    section.getString("skill"),
                    bonuses,
                    section.getInt("slot", -1)));
        }
        log.info("Yetenek agaci dugumu: " + nodes.size());
    }

    private Type type(String raw) {
        try {
            return Type.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Type.PASSIVE;
        }
    }

    Node node(String id) {
        return nodes.get(id);
    }

    List<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    /**
     * Bir dugumun acilabilir olup olmadigi: seviye, puan ve on kosul dugumleri.
     * On kosul zinciri dogrulanmazsa oyuncular agacin ortasindan baslayabilirdi.
     */
    UnlockCheck canUnlock(String nodeId, int level, int availablePoints, Set<String> unlocked) {
        Node node = nodes.get(nodeId);
        if (node == null) return UnlockCheck.UNKNOWN_NODE;
        if (unlocked.contains(nodeId)) return UnlockCheck.ALREADY_UNLOCKED;
        if (level < node.requiredLevel()) return UnlockCheck.LEVEL_TOO_LOW;
        if (availablePoints < node.cost()) return UnlockCheck.NOT_ENOUGH_POINTS;
        for (String required : node.requires()) {
            if (!unlocked.contains(required)) return UnlockCheck.MISSING_PREREQUISITE;
        }
        return UnlockCheck.OK;
    }

    /** Acilan dugumlerin toplam stat bonuslari. */
    Map<String, Double> bonusesOf(Set<String> unlocked) {
        Map<String, Double> total = new LinkedHashMap<>();
        for (String id : unlocked) {
            Node node = nodes.get(id);
            if (node == null) continue;
            node.bonuses().forEach((key, value) -> total.merge(key, value, Double::sum));
        }
        return total;
    }

    /** Acilan ACTIVE dugumlerin verdigi yetenekler. */
    List<String> skillsOf(Set<String> unlocked) {
        List<String> skills = new ArrayList<>();
        for (String id : unlocked) {
            Node node = nodes.get(id);
            if (node != null && node.type() == Type.ACTIVE && node.skillId() != null) {
                skills.add(node.skillId());
            }
        }
        return skills;
    }

    /** Dugum acma denemesinin sonucu; kullaniciya dogru mesaji gostermek icin. */
    enum UnlockCheck {
        OK,
        UNKNOWN_NODE,
        ALREADY_UNLOCKED,
        LEVEL_TOO_LOW,
        NOT_ENOUGH_POINTS,
        MISSING_PREREQUISITE
    }
}
