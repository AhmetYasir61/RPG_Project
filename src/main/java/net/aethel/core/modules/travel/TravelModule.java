package net.aethel.core.modules.travel;

import net.aethel.core.api.ItemService;
import net.aethel.core.api.PlayerProfile;
import net.aethel.core.api.ProfileService;
import net.aethel.core.api.TravelService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seyahat modulu. Serbest teleport yoktur: parsomen harcanir ya da hearthstone
 * sogumasi beklenir, ve waypoint'e once yuruyerek varmis olmak gerekir.
 */
@ModuleInfo(id = "travel", name = "Seyahat", depends = {"profile"}, softDepends = {"content"})
public final class TravelModule implements Module, TravelService {

    private static final String HEARTH_KEY = "travel:hearthstone";
    private static final String HEARTH_COOLDOWN_KEY = "travel:hearth-cooldown";
    private static final String DISCOVERED_KEY = "travel:discovered";

    private final TravelSettings settings = new TravelSettings();
    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();
    private final Map<UUID, CastingSession> casting = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatLock = new ConcurrentHashMap<>();
    private ConfigFile config;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/travel.yml", 1, settings, ConfigMigration.NONE);
        this.config = ctx.config().open("waypoints.yml", 1, null, ConfigMigration.NONE);
        ctx.services().register(TravelService.class, this, "travel");
        ctx.commands().register("travel", new TravelCommand(ctx, this));
    }

    @Override
    public void onEnable(CoreContext ctx) {
        loadWaypoints();
        ctx.listener(new TravelListener(this, ctx, settings));
        // Okuma ilerlemesi ve kesif taramasi ayni turda yapilir.
        ctx.scheduler().repeating("travel", 10L, 10L, this::tick);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        casting.clear();
        combatLock.clear();
        waypoints.clear();
    }

    private void loadWaypoints() {
        waypoints.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("waypoints");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            Location location = section == null ? null : section.getLocation("location");
            if (location == null) continue;
            waypoints.put(id, new Waypoint(id,
                    section.getString("display", id), location,
                    section.getString("icon", ""),
                    section.getDouble("scroll-cost", 1),
                    section.getBoolean("requires-discovery", settings.requireDiscovery)));
        }
        ctx.logger().info("Waypoint: " + waypoints.size());
    }

    @Override
    public List<Waypoint> waypoints() {
        return List.copyOf(waypoints.values());
    }

    @Override
    public Optional<Waypoint> waypoint(String id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    @Override
    public CompletableFuture<List<String>> discovered(UUID player) {
        return ctx.services().get(ProfileService.class).load(player)
                .thenApply(profile -> profile
                        .flatMap(found -> found.attribute(DISCOVERED_KEY))
                        .map(raw -> List.of(raw.split(",")))
                        .orElse(List.of()));
    }

    @Override
    public CompletableFuture<Void> discover(UUID player, String waypointId) {
        return ctx.services().get(ProfileService.class).load(player).thenAccept(found ->
                found.ifPresent(profile -> {
                    List<String> current = new ArrayList<>(profile.attribute(DISCOVERED_KEY)
                            .map(raw -> List.of(raw.split(","))).orElse(List.of()));
                    if (current.contains(waypointId)) return;
                    current.add(waypointId);
                    profile.attribute(DISCOVERED_KEY, String.join(",", current));
                    Player online = ctx.plugin().getServer().getPlayer(player);
                    if (online != null) {
                        ctx.lang().send(online, "travel.discovered",
                                net.aethel.core.i18n.LangService.of("waypoint", waypointId));
                    }
                }));
    }

    /**
     * Parsomen ile isinlanma. Parsomen okuma BASLARKEN degil BITERKEN harcanir;
     * yarida kesilen bir okumada oyuncunun esyasini almak haksiz bir ceza olurdu.
     */
    @Override
    public CastResult beginScrollTeleport(Player player, String waypointId) {
        Waypoint target = waypoints.get(waypointId);
        if (target == null) return CastResult.NOT_DISCOVERED;
        if (inCombat(player.getUniqueId())) return CastResult.IN_COMBAT;
        if (!hasScroll(player)) return CastResult.NO_SCROLL;

        if (target.requiresDiscovery()) {
            List<String> known = discovered(player.getUniqueId()).join();
            if (!known.contains(waypointId)) return CastResult.NOT_DISCOVERED;
        }
        casting.put(player.getUniqueId(), new CastingSession(player, target.location(),
                waypointId, settings.scrollCastSeconds, true));
        return CastResult.STARTED;
    }

    @Override
    public CastResult useHearthstone(Player player) {
        if (inCombat(player.getUniqueId())) return CastResult.IN_COMBAT;
        if (hearthstoneCooldown(player.getUniqueId()) > 0) return CastResult.ON_COOLDOWN;

        Optional<Location> bind = profile(player)
                .flatMap(profile -> profile.attribute(HEARTH_KEY))
                .map(this::parseLocation);
        if (bind.isEmpty()) return CastResult.NOT_DISCOVERED;

        casting.put(player.getUniqueId(), new CastingSession(player, bind.get(), null,
                settings.hearthstoneCastSeconds, false));
        return CastResult.STARTED;
    }

    @Override
    public boolean bindHearthstone(Player player) {
        return profile(player).map(profile -> {
            Location location = player.getLocation();
            profile.attribute(HEARTH_KEY, location.getWorld().getName() + ","
                    + location.getX() + "," + location.getY() + "," + location.getZ());
            return true;
        }).orElse(false);
    }

    @Override
    public long hearthstoneCooldown(UUID player) {
        return profile(player)
                .map(profile -> profile.attributeDouble(HEARTH_COOLDOWN_KEY, 0))
                .map(until -> Math.max(0, (long) (until - System.currentTimeMillis())))
                .orElse(0L);
    }

    /** Okumalari ilerletir, iptalleri isler ve kesif taramasi yapar. */
    private void tick() {
        casting.values().removeIf(session -> {
            Player player = session.player();
            if (!player.isOnline() || session.cancelled() || session.moved()) {
                if (player.isOnline()) ctx.lang().send(player, "travel.cast-cancelled");
                return true;
            }
            if (!session.finished()) {
                ctx.lang().send(player, "travel.casting",
                        net.aethel.core.i18n.LangService.of("seconds",
                                String.format("%.1f", session.remainingSeconds())));
                return false;
            }
            complete(session);
            return true;
        });
        scanDiscovery();
    }

    private void complete(CastingSession session) {
        Player player = session.player();
        if (session.consumesScroll() && !consumeScroll(player)) {
            ctx.lang().send(player, "travel.no-scroll");
            return;
        }
        if (!session.consumesScroll()) {
            profile(player).ifPresent(profile -> profile.attribute(HEARTH_COOLDOWN_KEY,
                    System.currentTimeMillis() + settings.hearthstoneCooldownMinutes * 60_000L));
        }
        player.teleportAsync(session.destination());
        ctx.lang().send(player, "travel.arrived");
    }

    /** Oyuncu bir waypoint'in yakinina gelirse orayi kesfetmis sayilir. */
    private void scanDiscovery() {
        if (waypoints.isEmpty()) return;
        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
            for (Waypoint waypoint : waypoints.values()) {
                if (!waypoint.location().getWorld().equals(player.getWorld())) continue;
                if (player.getLocation().distanceSquared(waypoint.location())
                        <= settings.discoveryRadius * settings.discoveryRadius) {
                    discover(player.getUniqueId(), waypoint.id());
                }
            }
        }
    }

    void markCombat(UUID player) {
        combatLock.put(player, System.currentTimeMillis() + settings.combatLockSeconds * 1000L);
    }

    boolean inCombat(UUID player) {
        Long until = combatLock.get(player);
        return until != null && until > System.currentTimeMillis();
    }

    void cancelCast(UUID player) {
        CastingSession session = casting.get(player);
        if (session != null) session.cancel();
    }

    private boolean hasScroll(Player player) {
        return ctx.services().optional(ItemService.class)
                .map(items -> java.util.Arrays.stream(player.getInventory().getContents())
                        .anyMatch(stack -> items.resolve(stack)
                                .map(item -> item.fullId().equals(settings.scrollItem))
                                .orElse(false)))
                .orElse(false);
    }

    private boolean consumeScroll(Player player) {
        var items = ctx.services().optional(ItemService.class);
        if (items.isEmpty()) return false;
        for (var stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            boolean match = items.get().resolve(stack)
                    .map(item -> item.fullId().equals(settings.scrollItem)).orElse(false);
            if (match) {
                stack.setAmount(stack.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private Optional<PlayerProfile> profile(Player player) {
        return profile(player.getUniqueId());
    }

    private Optional<PlayerProfile> profile(UUID uuid) {
        return ctx.services().optional(ProfileService.class)
                .flatMap(profiles -> profiles.cached(uuid));
    }

    private Location parseLocation(String raw) {
        String[] parts = raw.split(",");
        return new Location(ctx.plugin().getServer().getWorld(parts[0]),
                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]));
    }

    TravelSettings settings() {
        return settings;
    }
}
