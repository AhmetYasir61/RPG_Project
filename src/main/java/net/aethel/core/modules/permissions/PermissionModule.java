package net.aethel.core.modules.permissions;

import net.aethel.core.api.PermissionService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kendi yetki motorumuz. Gruplar bellekte tutulur, oyuncu izinleri girise gore
 * hesaplanip Bukkit attachment'ina yazilir; her izin kontrolu O(1) hash aramasi olur.
 */
@ModuleInfo(id = "permissions", name = "Yetkiler", depends = {"profile"})
public final class PermissionModule implements Module, PermissionService, Listener {

    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> userGroups = new ConcurrentHashMap<>();
    private final Map<UUID, List<TimedNode>> userNodes = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    private PermissionRepository repository;
    private CoreContext ctx;
    private String defaultGroup = "oyuncu";

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.repository = new PermissionRepository(ctx.database());
        ctx.services().register(PermissionService.class, this, "permissions");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.schema().migrate("permissions", PermissionSchema.MIGRATIONS);
        this.defaultGroup = ctx.config().get("modules.yml").yaml()
                .getString("modules.permissions.default-group", "oyuncu");

        reloadGroups().thenRun(() -> ctx.scheduler().sync("permissions", () -> {
            ensureDefaultGroup();
            ctx.plugin().getServer().getOnlinePlayers().forEach(this::loadPlayer);
        }));
        ctx.listener(this);

        // Sureli rank/izinler dakikada bir taranir; suresi dolan otomatik dusuru.
        ctx.scheduler().repeating("permissions", 20L * 60, 20L * 60, this::expireTimed);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        attachments.values().forEach(PermissionAttachment::remove);
        attachments.clear();
        userGroups.clear();
        userNodes.clear();
        groups.clear();
    }

    /** Sunucuda hicbir grup yoksa varsayilan grup olusturulur; oyuncular gruppsuz kalmaz. */
    private void ensureDefaultGroup() {
        if (!groups.isEmpty()) return;
        Group group = new Group(defaultGroup, "<gray>Oyuncu</gray>", "<gray>", "", 0,
                List.of(), List.of("core.command.parti", "core.command.profil"));
        groups.put(group.name(), group);
        repository.saveGroup(group);
        ctx.logger().info("Varsayilan grup olusturuldu: " + defaultGroup);
    }

    private CompletableFuture<Void> reloadGroups() {
        return repository.loadGroups().thenAccept(loaded -> {
            groups.clear();
            loaded.forEach(group -> groups.put(group.name(), group));
            ctx.logger().info("Yuklenen grup: " + groups.size());
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) attachment.remove();
        userGroups.remove(uuid);
        userNodes.remove(uuid);
    }

    /** Dunya degisiminde izinler yeniden hesaplanir: dunya bazli izinler icin sart. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        recalculate(event.getPlayer());
    }

    private void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        repository.userGroups(uuid).thenCompose(rows -> {
            List<String> names = new ArrayList<>();
            rows.forEach(entry -> {
                Long expires = entry.getValue() == Long.MAX_VALUE ? null : entry.getValue();
                if (PermissionResolver.isActive(expires)) names.add(entry.getKey());
            });
            if (names.isEmpty()) {
                names.add(defaultGroup);
                repository.addUserGroup(uuid, defaultGroup, null);
            }
            userGroups.put(uuid, names);
            return repository.userNodes(uuid);
        }).thenAccept(nodes -> {
            userNodes.put(uuid, nodes);
            ctx.scheduler().sync("permissions", () -> recalculate(player));
        });
    }

    @Override
    public void recalculate(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.computeIfAbsent(uuid,
                key -> player.addAttachment(ctx.plugin()));

        // Onceki izinleri temizleyip bastan yaziyoruz: aksi halde kaldirilan bir izin
        // oyuncuda kalmaya devam eder ve yetki dusurmek imkansiz hale gelir.
        Map<String, Boolean> previous = new LinkedHashMap<>(attachment.getPermissions());
        previous.keySet().forEach(attachment::unsetPermission);

        List<Group> playerGroups = groupsOf(uuid).stream()
                .map(groups::get).filter(java.util.Objects::nonNull).toList();
        Map<String, Boolean> resolved = PermissionResolver.resolve(playerGroups, groups,
                userNodes.getOrDefault(uuid, List.of()), player.getWorld().getName());
        resolved.forEach(attachment::setPermission);
        player.recalculatePermissions();
    }

    private void expireTimed() {
        long now = System.currentTimeMillis();
        userNodes.forEach((uuid, nodes) -> {
            boolean changed = nodes.removeIf(node ->
                    node.expiresAt() != null && node.expiresAt() < now);
            Player player = ctx.plugin().getServer().getPlayer(uuid);
            if (changed && player != null) recalculate(player);
        });
    }

    @Override public Optional<Group> group(String name) { return Optional.ofNullable(groups.get(name)); }

    @Override
    public List<Group> groups() {
        return groups.values().stream()
                .sorted(Comparator.comparingInt(Group::weight).reversed()).toList();
    }

    @Override
    public CompletableFuture<Void> saveGroup(Group group) {
        groups.put(group.name(), group);
        return repository.saveGroup(group).thenRun(this::recalculateAll);
    }

    @Override
    public CompletableFuture<Void> deleteGroup(String name) {
        groups.remove(name);
        return repository.deleteGroup(name).thenRun(this::recalculateAll);
    }

    @Override
    public List<String> groupsOf(UUID uuid) {
        return userGroups.getOrDefault(uuid, List.of(defaultGroup));
    }

    @Override
    public CompletableFuture<Void> addGroup(UUID uuid, String group, Long expiresAt) {
        userGroups.computeIfAbsent(uuid, key -> new ArrayList<>()).add(group);
        return repository.addUserGroup(uuid, group, expiresAt).thenRun(() -> refresh(uuid));
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID uuid, String group) {
        userGroups.getOrDefault(uuid, new ArrayList<>()).remove(group);
        return repository.removeUserGroup(uuid, group).thenRun(() -> refresh(uuid));
    }

    @Override
    public List<TimedNode> nodesOf(UUID uuid) {
        return List.copyOf(userNodes.getOrDefault(uuid, List.of()));
    }

    @Override
    public CompletableFuture<Void> setPermission(UUID uuid, String permission, boolean value,
                                                 String world, Long expiresAt) {
        return repository.setUserPermission(uuid, permission, value, world, expiresAt)
                .thenRun(() -> reloadNodes(uuid));
    }

    @Override
    public CompletableFuture<Void> unsetPermission(UUID uuid, String permission, String world) {
        return repository.unsetUserPermission(uuid, permission, world)
                .thenRun(() -> reloadNodes(uuid));
    }

    /** En yuksek agirlikli grubun prefix'i kazanir; bos ise bir alttakine bakilir. */
    @Override
    public String prefix(UUID uuid) {
        return groupsOf(uuid).stream().map(groups::get).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Group::weight).reversed())
                .map(Group::prefix).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
    }

    @Override
    public String suffix(UUID uuid) {
        return groupsOf(uuid).stream().map(groups::get).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Group::weight).reversed())
                .map(Group::suffix).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
    }

    private void reloadNodes(UUID uuid) {
        repository.userNodes(uuid).thenAccept(nodes -> {
            userNodes.put(uuid, nodes);
            refresh(uuid);
        });
    }

    private void refresh(UUID uuid) {
        Player player = ctx.plugin().getServer().getPlayer(uuid);
        if (player != null) ctx.scheduler().sync("permissions", () -> recalculate(player));
    }

    private void recalculateAll() {
        ctx.scheduler().sync("permissions", () ->
                ctx.plugin().getServer().getOnlinePlayers().forEach(this::recalculate));
    }
}
