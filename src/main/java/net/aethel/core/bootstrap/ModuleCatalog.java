package net.aethel.core.bootstrap;

import net.aethel.core.module.Module;
import net.aethel.core.modules.auth.AuthModule;
import net.aethel.core.modules.hologram.HologramModule;
import net.aethel.core.modules.content.ContentModule;
import net.aethel.core.modules.economy.EconomyModule;
import net.aethel.core.modules.menu.MenuModule;
import net.aethel.core.modules.pcoins.PCoinModule;
import net.aethel.core.modules.permissions.PermissionModule;
import net.aethel.core.modules.profile.ProfileModule;
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
                new ProfileModule(),
                new AuthModule(),
                new PermissionModule(),
                new EconomyModule(),
                new PCoinModule(),
                new MenuModule(),
                new ContentModule(),
                new HologramModule(bridge),
                new WaypointModule(bridge)
        };
    }
}
