package net.aethel.core.modules.travel.waypoint;

import net.aethel.core.api.WaypointMarker;
import net.aethel.core.api.WaypointService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.nms.VersionAdapter;
import net.aethel.core.nms.VersionAdapters;
import net.aethel.core.packet.HeadDisplayPacket;
import net.aethel.core.packet.PacketBridge;
import net.aethel.core.packet.TextDisplayPacket;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parti uyesi ve hedef takibi. Isaretler paket ile cizilir, sunucuda entity olusmaz;
 * oyuncuyu hicbir zaman isinlamaz — MMORPG akisinda mesafe gercek bir maliyettir.
 */
@ModuleInfo(id = "waypoint", name = "Waypoint")
public final class WaypointModule implements Module, WaypointService {

    /** Isaretler saniyede 4 kez guncellenir: akici gorunur, paket trafigi dusuk kalir. */
    private static final long UPDATE_PERIOD_TICKS = 5L;

    private final PacketBridge bridge;
    private final Map<UUID, List<TrackedMarker>> markers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerTargets = new ConcurrentHashMap<>();
    private VersionAdapter nms;
    private CoreContext ctx;

    public WaypointModule(PacketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.nms = VersionAdapters.detect().orElseThrow(() ->
                new IllegalStateException("Bu Minecraft surumu icin NMS adapteri yok"));
        ctx.services().register(WaypointService.class, this, "waypoint");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.scheduler().repeating("waypoint", UPDATE_PERIOD_TICKS, UPDATE_PERIOD_TICKS, this::tickAll);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        markers.values().forEach(list -> list.forEach(WaypointMarker::remove));
        markers.clear();
        playerTargets.clear();
    }

    @Override
    public WaypointMarker trackPlayer(Player viewer, Player target) {
        playerTargets.put(viewer.getUniqueId(), target.getUniqueId());
        return create(viewer, target.getLocation(), target.getName());
    }

    @Override
    public WaypointMarker trackLocation(Player viewer, Location target, String label) {
        return create(viewer, target, label);
    }

    @Override
    public List<WaypointMarker> markersOf(Player viewer) {
        return List.copyOf(markers.getOrDefault(viewer.getUniqueId(), List.of()));
    }

    @Override
    public void clear(Player viewer) {
        List<TrackedMarker> list = markers.remove(viewer.getUniqueId());
        if (list != null) list.forEach(WaypointMarker::remove);
        playerTargets.remove(viewer.getUniqueId());
    }

    private WaypointMarker create(Player viewer, Location target, String label) {
        TrackedMarker marker = new TrackedMarker(viewer,
                new HeadDisplayPacket(bridge, nms.nextEntityId(), target),
                new TextDisplayPacket(bridge, nms.nextEntityId(), target),
                target.clone(), label);
        markers.computeIfAbsent(viewer.getUniqueId(), key -> new ArrayList<>()).add(marker);
        return marker;
    }

    /**
     * Takip edilen oyuncunun konumu her turda tazelenir; oyuncu cikmissa isaret dusurulur.
     * Isaret sayisi oyunculara bagli oldugu icin (tipik 1-5) butceli goreve gerek yoktur.
     */
    private void tickAll() {
        for (Map.Entry<UUID, List<TrackedMarker>> entry : markers.entrySet()) {
            Player viewer = ctx.plugin().getServer().getPlayer(entry.getKey());
            if (viewer == null) continue;

            UUID targetId = playerTargets.get(entry.getKey());
            Player tracked = targetId == null ? null : ctx.plugin().getServer().getPlayer(targetId);
            for (TrackedMarker marker : entry.getValue()) {
                if (tracked != null) marker.target(tracked.getLocation());
                marker.tick(PlayerHeads.of(tracked));
            }
        }
    }
}
