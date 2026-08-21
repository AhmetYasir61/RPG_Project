package net.aethel.core.modules.hologram;

import net.aethel.core.api.Hologram;
import net.aethel.core.nms.VersionAdapter;
import net.aethel.core.packet.PacketBridge;
import net.aethel.core.packet.TextDisplayPacket;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Hologram arayuzunun paket tabanli implementasyonu. Her satir ayri bir text_display
 * paketidir; per-player satirlar sayesinde ayni hologram herkese farkli gorunebilir.
 */
final class PacketHologram implements Hologram {

    /** Satirlar arasi dikey bosluk; text_display'in varsayilan yuksekligine yakin. */
    private static final double LINE_SPACING = 0.28D;

    private final String id;
    private final PacketBridge bridge;
    private final VersionAdapter nms;
    private final Map<UUID, List<TextDisplayPacket>> shown = new HashMap<>();
    private final Map<UUID, List<Component>> personalLines = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();

    private Location location;
    private List<Component> lines = new ArrayList<>();
    private Predicate<Player> visibility = player -> true;

    PacketHologram(String id, Location location, PacketBridge bridge, VersionAdapter nms) {
        this.id = id;
        this.location = location;
        this.bridge = bridge;
        this.nms = nms;
    }

    @Override public String id() { return id; }
    @Override public Location location() { return location; }

    @Override
    public void teleport(Location target) {
        this.location = target;
        for (UUID viewerId : Set.copyOf(viewers)) {
            Player viewer = org.bukkit.Bukkit.getPlayer(viewerId);
            if (viewer != null) { hide(viewer); show(viewer); }
        }
    }

    @Override
    public void lines(List<Component> lines) {
        this.lines = List.copyOf(lines);
        refreshAll();
    }

    @Override
    public void lines(Player viewer, List<Component> lines) {
        personalLines.put(viewer.getUniqueId(), List.copyOf(lines));
        if (viewers.contains(viewer.getUniqueId())) { hide(viewer); show(viewer); }
    }

    @Override
    public void show(Player player) {
        if (!visibility.test(player)) return;
        List<Component> content = personalLines.getOrDefault(player.getUniqueId(), lines);
        List<TextDisplayPacket> displays = new ArrayList<>(content.size());

        // Satirlari yukaridan asagi diziyoruz: ilk satir en ustte gorunsun.
        double top = location.getY() + (content.size() - 1) * LINE_SPACING;
        for (int i = 0; i < content.size(); i++) {
            Location lineLocation = location.clone();
            lineLocation.setY(top - i * LINE_SPACING);
            TextDisplayPacket display = new TextDisplayPacket(bridge, nms.nextEntityId(), lineLocation);
            display.spawn(player, content.get(i));
            displays.add(display);
        }
        shown.put(player.getUniqueId(), displays);
        viewers.add(player.getUniqueId());
    }

    @Override
    public void hide(Player player) {
        List<TextDisplayPacket> displays = shown.remove(player.getUniqueId());
        if (displays != null) displays.forEach(display -> display.destroy(player));
        viewers.remove(player.getUniqueId());
    }

    @Override
    public void visibilityRule(Predicate<Player> rule) {
        this.visibility = rule;
        refreshAll();
    }

    @Override
    public void remove() {
        for (UUID viewerId : Set.copyOf(viewers)) {
            Player viewer = org.bukkit.Bukkit.getPlayer(viewerId);
            if (viewer != null) hide(viewer);
        }
        shown.clear();
        personalLines.clear();
    }

    private void refreshAll() {
        for (UUID viewerId : Set.copyOf(viewers)) {
            Player viewer = org.bukkit.Bukkit.getPlayer(viewerId);
            if (viewer != null) { hide(viewer); show(viewer); }
        }
    }
}
