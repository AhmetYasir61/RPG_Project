package net.aethel.core.bootstrap;

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

    @Command("reload")
    public void reload(CommandSender sender) {
        ctx.config().reloadAll();
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
