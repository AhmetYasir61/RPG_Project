package net.aethel.core.bootstrap;

import net.aethel.core.api.FeatureService;
import net.aethel.core.command.Command;
import net.aethel.core.command.Arg;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.ModuleContainer;
import net.aethel.core.module.ModuleManager;
import org.bukkit.command.CommandSender;

/**
 * /core yonetim komutu: modul listesi, hot-enable/disable ve config reload.
 * ModuleManager ve ConfigService uzerinden calisir, kendi durumu yoktur.
 */
@Command(value = "core", permission = "core.admin", descriptionKey = "command.core.description")
public final class CoreCommand {

    private final CoreContext ctx;
    private final ModuleManager modules;

    public CoreCommand(CoreContext ctx, ModuleManager modules) {
        this.ctx = ctx;
        this.modules = modules;
    }

    @Command("modules")
    public void modules(CommandSender sender) {
        ctx.lang().send(sender, "core.modules-header");
        for (ModuleContainer container : modules.all()) {
            ctx.lang().send(sender, "core.modules-line",
                    LangService.of("id", container.id()),
                    LangService.of("name", container.info().name()),
                    LangService.of("state", container.state().name()));
        }
    }

    @Command("enable")
    public void enable(CommandSender sender, @Arg("modul") String moduleId) {
        boolean ok = modules.enable(moduleId);
        ctx.lang().send(sender, ok ? "core.module-enabled" : "core.module-action-failed",
                LangService.of("id", moduleId));
    }

    @Command("disable")
    public void disable(CommandSender sender, @Arg("modul") String moduleId) {
        boolean ok = modules.disable(moduleId);
        ctx.lang().send(sender, ok ? "core.module-disabled" : "core.module-action-failed",
                LangService.of("id", moduleId));
    }

    /** Ozellik listesi: acik/kapali durumu ve aciklamasi. */
    @Command("ozellikler")
    public void features(CommandSender sender) {
        FeatureService features = ctx.services().get(FeatureService.class);
        ctx.lang().send(sender, "core.features-header");
        features.snapshot().forEach((key, value) -> ctx.lang().send(sender,
                value ? "core.feature-on" : "core.feature-off",
                LangService.of("key", key),
                LangService.of("description", features.description(key))));
    }

    /**
     * Ozelligi acar/kapatir. Kapatilan ozellik ANINDA yok olur: listener'lari dusurulur
     * ve komutu agactan cikar; sunucu yeniden baslatilmaz.
     */
    @Command("ozellik")
    public void feature(CommandSender sender, @Arg("anahtar") String key,
                        @Arg("deger") boolean value) {
        FeatureService features = ctx.services().get(FeatureService.class);
        if (!features.keys().contains(key)) {
            ctx.lang().send(sender, "core.feature-unknown", LangService.of("key", key));
            return;
        }
        features.set(key, value);
        ctx.lang().send(sender, value ? "core.feature-enabled" : "core.feature-disabled",
                LangService.of("key", key));
    }

    @Command("reload")
    public void reload(CommandSender sender) {
        ctx.config().reloadAll();
        ctx.services().optional(FeatureService.class)
                .filter(net.aethel.core.feature.FeatureRegistry.class::isInstance)
                .map(net.aethel.core.feature.FeatureRegistry.class::cast)
                .ifPresent(net.aethel.core.feature.FeatureRegistry::reload);
        ctx.lang().load("tr", "tr", "en");
        modules.reloadAll();
        ctx.lang().send(sender, "core.reloaded");
    }

    @Command("version")
    public void version(CommandSender sender) {
        ctx.lang().send(sender, "core.version",
                LangService.of("version", ctx.plugin().getPluginMeta().getVersion()));
    }
}
