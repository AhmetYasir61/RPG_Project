package net.aethel.core.modules.permissions;

import net.aethel.core.api.PermissionService.Group;
import net.aethel.core.api.PermissionService.TimedNode;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grup kalitimi ve izin cakismalarini cozer. Sonuc, Bukkit attachment'ina yazilacak
 * duz bir izin -> deger haritasidir.
 */
final class PermissionResolver {

    private PermissionResolver() {}

    /**
     * Cozum sirasi: once dusuk agirlikli gruplar (ve onlarin ebeveynleri), sonra yuksek
     * agirlikli gruplar, en son oyuncuya dogrudan verilen izinler. Boylece daha spesifik
     * olan daima kazanir ve bir grup, ust grubun iznini bilerek iptal edebilir.
     */
    static Map<String, Boolean> resolve(List<Group> playerGroups,
                                        Map<String, Group> allGroups,
                                        List<TimedNode> userNodes,
                                        String world) {
        Map<String, Boolean> result = new LinkedHashMap<>();

        List<Group> ordered = playerGroups.stream()
                .sorted(java.util.Comparator.comparingInt(Group::weight))
                .toList();

        for (Group group : ordered) {
            applyGroup(group, allGroups, result, new HashSet<>());
        }
        long now = System.currentTimeMillis();
        for (TimedNode node : userNodes) {
            if (node.expiresAt() != null && node.expiresAt() < now) continue;
            if (!node.world().isEmpty() && !node.world().equalsIgnoreCase(world)) continue;
            boolean negated = node.value().startsWith("-");
            result.put(negated ? node.value().substring(1) : node.value(), !negated);
        }
        return result;
    }

    /** Ebeveynler once uygulanir; cevrimlere karsi ziyaret edilen gruplar isaretlenir. */
    private static void applyGroup(Group group, Map<String, Group> allGroups,
                                   Map<String, Boolean> result, Set<String> visited) {
        if (group == null || !visited.add(group.name())) return;
        for (String parent : group.parents()) {
            applyGroup(allGroups.get(parent), allGroups, result, visited);
        }
        for (String permission : group.permissions()) {
            boolean negated = permission.startsWith("-");
            result.put(negated ? permission.substring(1) : permission, !negated);
        }
    }

    /** Sureli grup uyeliklerinden suresi dolmus olanlari ayiklar. */
    static boolean isActive(Long expiresAt) {
        return expiresAt == null || expiresAt > System.currentTimeMillis();
    }
}
