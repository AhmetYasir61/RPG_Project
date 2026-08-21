package net.aethel.core.bootstrap;

import net.aethel.core.command.CommandRegistrar;
import net.aethel.core.config.ConfigService;
import net.aethel.core.event.EventBus;
import net.aethel.core.feature.FeatureRegistry;
import net.aethel.core.i18n.LangService;
import net.aethel.core.service.ServiceRegistry;
import net.aethel.core.storage.Database;
import net.aethel.core.storage.SchemaManager;
import net.aethel.core.util.CoreScheduler;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Modullere gecirilen cekirdek erisim noktasi. Modul kodu CorePlugin'i degil bunu
 * gorur; test icinde sahte bir context kurmak boylece mumkun olur.
 */
public record CoreContext(Plugin plugin,
                          Logger logger,
                          ServiceRegistry services,
                          EventBus events,
                          ConfigService config,
                          LangService lang,
                          CommandRegistrar commands,
                          CoreScheduler scheduler,
                          Database database,
                          SchemaManager schema,
                          FeatureRegistry features) {

    /** Bukkit listener'ini kaydetmek icin kisayol. */
    public void listener(org.bukkit.event.Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Ozellige bagli listener. Ozellik kapaliysa listener Bukkit'e HIC verilmez ve
     * acildiginda otomatik baglanir; modul kodunda bayrak kontrolu gerekmez.
     */
    public void listener(String featureKey, org.bukkit.event.Listener listener) {
        features.listener(featureKey, listener);
    }

    /** Kisayol: ctx.feature("travel.scroll") */
    public boolean feature(String key) {
        return features.enabled(key);
    }
}
