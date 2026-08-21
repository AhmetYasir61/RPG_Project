package net.aethel.core.bootstrap;

import net.aethel.core.module.Module;
import net.aethel.core.modules.hologram.HologramModule;
import net.aethel.core.modules.travel.waypoint.WaypointModule;
import net.aethel.core.packet.PacketBridge;

/**
 * Derleme zamaninda bilinen modul listesi. Classpath taramasi yerine acik liste
 * kullaniyoruz: baslangic suresi sabit kalir ve eksik modul derlemede yakalanir.
 */
final class ModuleCatalog {

    private ModuleCatalog() {}

    /** Modul ornekleri; sira onemsizdir, ModuleManager bagimliliga gore siralar. */
    static Module[] all(PacketBridge bridge) {
        return new Module[] {
                new HologramModule(bridge),
                new WaypointModule(bridge)
        };
    }
}
