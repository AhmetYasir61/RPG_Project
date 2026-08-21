package net.aethel.core.modules.travel.waypoint;

import net.aethel.core.api.WaypointMarker;
import net.aethel.core.packet.HeadDisplayPacket;
import net.aethel.core.packet.TextDisplayPacket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Tek bir waypoint isareti: saydam kafa + altinda mesafe etiketi. Hedefe isinlama
 * saglamaz; yalnizca yonu ve uzakligi gosterir (bkz. docs/DESIGN.md, serbest teleport yok).
 */
public final class TrackedMarker implements WaypointMarker {

    /**
     * Isaret oyuncunun ustunde durur; hedef cok uzaktaysa gorus alanina girmesi icin
     * izleyiciye dogru bu mesafeye kadar yaklastirilir (isaret uzakta kaybolmasin).
     */
    private static final double HEAD_OFFSET_Y = 2.4D;
    private static final double MAX_RENDER_DISTANCE = 56.0D;

    private final Player viewer;
    private final HeadDisplayPacket head;
    private final TextDisplayPacket label;
    private final MiniMessage mini = MiniMessage.miniMessage();

    private Location target;
    private String labelText;
    private boolean glow = true;
    private boolean spawned;

    public TrackedMarker(Player viewer, HeadDisplayPacket head, TextDisplayPacket label,
                         Location target, String labelText) {
        this.viewer = viewer;
        this.head = head;
        this.label = label;
        this.target = target;
        this.labelText = labelText;
    }

    @Override public Player viewer() { return viewer; }
    @Override public Location target() { return target; }

    @Override
    public void target(Location location) {
        this.target = location;
    }

    @Override
    public void label(String label) {
        this.labelText = label;
    }

    @Override
    public void glowThroughWalls(boolean glow) {
        this.glow = glow;
    }

    /**
     * Isareti guncel konuma tasir ve mesafeyi yazar. Hedef gorus mesafesinin disindaysa
     * isaret, izleyiciden hedefe giden dogru uzerinde sinir noktasina yerlestirilir —
     * boylece oyuncu yonu her zaman gorur, isaret hicbir zaman kaybolmaz.
     */
    public void tick(org.bukkit.inventory.ItemStack headItem) {
        Location viewerLocation = viewer.getLocation();
        if (!viewerLocation.getWorld().equals(target.getWorld())) { destroy(); return; }

        double distance = viewerLocation.distance(target);
        Location render = target.clone();
        if (distance > MAX_RENDER_DISTANCE) {
            var direction = target.toVector().subtract(viewerLocation.toVector()).normalize();
            render = viewerLocation.clone().add(direction.multiply(MAX_RENDER_DISTANCE));
        }
        render.add(0, HEAD_OFFSET_Y, 0);

        if (!spawned) {
            head.spawn(viewer, headItem, glow);
            label.spawn(viewer, text(distance));
            spawned = true;
            return;
        }
        head.move(viewer, render);
        head.update(viewer, headItem, glow);
        label.move(viewer, render.clone().subtract(0, 0.45D, 0));
        label.update(viewer, text(distance));
    }

    private Component text(double distance) {
        return mini.deserialize("<white>" + labelText + "</white> <gray>· "
                + Math.round(distance) + "m</gray>");
    }

    private void destroy() {
        if (!spawned) return;
        head.destroy(viewer);
        label.destroy(viewer);
        spawned = false;
    }

    @Override
    public void remove() {
        destroy();
    }
}
