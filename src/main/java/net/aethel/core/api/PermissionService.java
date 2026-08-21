package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kendi yetki motorumuz (LuckPerms karsiligi). Grup kalitimi, sureli izinler,
 * dunya bazli izinler ve prefix/suffix destegi tek servis altinda toplanir.
 */
public interface PermissionService {

    /** Bir grup tanimi; agirlik cakismalarda hangi prefix'in kazanacagini belirler. */
    record Group(String name, String displayName, String prefix, String suffix,
                 int weight, List<String> parents, List<String> permissions) {}

    /** Sureli izin; expiresAt null ise kalicidir. */
    record TimedNode(String value, String world, Long expiresAt) {}

    Optional<Group> group(String name);

    List<Group> groups();

    CompletableFuture<Void> saveGroup(Group group);

    CompletableFuture<Void> deleteGroup(String name);

    /** Oyuncunun ait oldugu gruplar, agirliga gore sirali. */
    List<String> groupsOf(UUID uuid);

    CompletableFuture<Void> addGroup(UUID uuid, String group, Long expiresAt);

    CompletableFuture<Void> removeGroup(UUID uuid, String group);

    /** Oyuncuya dogrudan verilen izinler (grup disi). */
    List<TimedNode> nodesOf(UUID uuid);

    CompletableFuture<Void> setPermission(UUID uuid, String permission, boolean value,
                                          String world, Long expiresAt);

    CompletableFuture<Void> unsetPermission(UUID uuid, String permission, String world);

    /** Hesaplanmis prefix (en yuksek agirlikli gruptan). */
    String prefix(UUID uuid);

    String suffix(UUID uuid);

    /** Oyuncunun izinlerini yeniden hesaplar ve Bukkit'e uygular. */
    void recalculate(Player player);
}
