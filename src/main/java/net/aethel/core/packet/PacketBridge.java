package net.aethel.core.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PacketEvents'in tek sarmalayicisi. Kutuphane sunucu surumunu desteklemiyorsa
 * cekirdek CALISMAYA DEVAM EDER: yalnizca paket tabanli moduller devre disi kalir.
 */
public final class PacketBridge {

    private final Logger log;
    private boolean loaded;
    private boolean available;

    public PacketBridge(Logger log) {
        this.log = log;
    }

    /**
     * onLoad icinde cagrilmalidir: PacketEvents kanal enjeksiyonunu sunucu ag katmani
     * kurulmadan once yapar, onEnable'da cagrilirsa ilk giren oyuncular kacirilir.
     *
     * Hata yakalaniyor cunku kutuphane yeni bir Minecraft surumunde patlayabilir ve
     * bu, tum sunucunun acilmamasi icin yeterli bir sebep degildir.
     */
    public void load(Plugin plugin) {
        if (loaded) return;
        loaded = true;
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
            PacketEvents.getAPI().getSettings().checkForUpdates(false);
            PacketEvents.getAPI().load();
            available = true;
            log.info("Paket katmani yuklendi.");
        } catch (Throwable error) {
            available = false;
            log.log(Level.WARNING, "Paket katmani yuklenemedi; hologram, NPC ve waypoint"
                    + " modulleri devre disi kalacak (" + error.getClass().getSimpleName() + ")");
        }
    }

    public void enable() {
        if (!available) return;
        try {
            PacketEvents.getAPI().init();
        } catch (Throwable error) {
            available = false;
            log.log(Level.WARNING, "Paket katmani baslatilamadi; paket tabanli moduller kapali.", error);
        }
    }

    public void disable() {
        if (!available) return;
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable error) {
            log.log(Level.WARNING, "Paket katmani kapatilirken hata", error);
        }
    }

    /** Dinleyiciyi kaydeder; paket katmani yoksa sessizce yoksayilir. */
    public void listener(PacketListenerAbstract listener) {
        if (!available) return;
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    /** Tek oyuncuya paket gonderir; katman kapaliysa hicbir sey yapmaz. */
    public void send(Player player, Object packetWrapper) {
        if (!available) return;
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    (com.github.retrooper.packetevents.wrapper.PacketWrapper<?>) packetWrapper);
        } catch (Throwable error) {
            log.log(Level.WARNING, "Paket gonderilemedi: " + packetWrapper.getClass().getSimpleName());
        }
    }

    /** Paket tabanli moduller acilmadan once bunu kontrol eder. */
    public boolean isAvailable() {
        return available;
    }
}
