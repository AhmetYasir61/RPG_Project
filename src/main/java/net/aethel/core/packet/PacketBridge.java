package net.aethel.core.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * PacketEvents'in tek sarmalayicisi. Kutuphane shade edilip relocate edildigi icin
 * baska bir plugin ayni kutuphaneyi kursa bile cakisma olmaz; modul kodu bu sinifi gorur.
 */
public final class PacketBridge {

    private final Logger log;
    private boolean initialized;

    public PacketBridge(Logger log) {
        this.log = log;
    }

    /**
     * onLoad icinde cagrilmalidir: PacketEvents kanal enjeksiyonunu sunucu ag katmani
     * kurulmadan once yapar, onEnable'da cagrilirsa ilk giren oyuncular kacirilir.
     */
    public void load(Plugin plugin) {
        if (initialized) return;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
        initialized = true;
        log.info("Paket katmani yuklendi.");
    }

    public void enable() {
        if (initialized) PacketEvents.getAPI().init();
    }

    public void disable() {
        if (initialized) PacketEvents.getAPI().terminate();
    }

    /** Dinleyiciyi kaydeder; oncelik varsayilan olarak NORMAL'dir. */
    public void listener(PacketListenerAbstract listener) {
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    public void listener(PacketListenerAbstract listener, PacketListenerPriority priority) {
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    /** Tek oyuncuya paket gonderir. */
    public void send(Player player, Object packetWrapper) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, (
                com.github.retrooper.packetevents.wrapper.PacketWrapper<?>) packetWrapper);
    }

    public boolean isReady() {
        return initialized;
    }
}
