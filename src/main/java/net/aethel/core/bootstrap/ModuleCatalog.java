package net.aethel.core.bootstrap;

import net.aethel.core.module.Module;
import net.aethel.core.modules.auth.AuthModule;
import net.aethel.core.modules.hologram.HologramModule;
import net.aethel.core.modules.content.ContentModule;
import net.aethel.core.modules.chat.ChatModule;
import net.aethel.core.modules.dialog.DialogModule;
import net.aethel.core.modules.dungeon.DungeonModule;
import net.aethel.core.modules.economy.EconomyModule;
import net.aethel.core.modules.npc.NpcModule;
import net.aethel.core.modules.quest.QuestModule;
import net.aethel.core.modules.hud.HudModule;
import net.aethel.core.modules.jobs.JobModule;
import net.aethel.core.modules.mob.MobModule;
import net.aethel.core.modules.loot.LootModule;
import net.aethel.core.modules.rpg.RpgModule;
import net.aethel.core.modules.placeholder.PlaceholderModule;
import net.aethel.core.modules.region.RegionModule;
import net.aethel.core.modules.skill.SkillModule;
import net.aethel.core.modules.menu.MenuModule;
import net.aethel.core.modules.panel.AdminPanelModule;
import net.aethel.core.modules.party.PartyModule;
import net.aethel.core.modules.pcoins.PCoinModule;
import net.aethel.core.modules.travel.TravelModule;
import net.aethel.core.modules.web.WebModule;
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
                new RegionModule(),
                new LootModule(),
                new SkillModule(),
                new MobModule(),
                new RpgModule(),
                new JobModule(),
                new PlaceholderModule(),
                new HudModule(),
                new PartyModule(),
                new TravelModule(),
                new AdminPanelModule(),
                new WebModule(),
                new ChatModule(),
                new DialogModule(),
                new QuestModule(),
                new NpcModule(bridge),
                new DungeonModule(),
                new HologramModule(bridge),
                new WaypointModule(bridge)
        };
    }
}
