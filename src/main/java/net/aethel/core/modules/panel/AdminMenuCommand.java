package net.aethel.core.modules.panel;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sunucudaki TEK yonetim komutu. Geri kalan her sey panelden yapilir; yetkisi olan
 * herkes bu komutla aktif moda uygun paneli acar.
 */
@Command(value = "adminmenu", aliases = {"apanel", "yonetim"},
        permission = "aethel.admin.panel", playerOnly = true,
        descriptionKey = "panel.command-description")
public final class AdminMenuCommand {

    private final CoreContext ctx;
    private final AdminPanelModule panel;
    private final PanelSettings settings;

    public AdminMenuCommand(CoreContext ctx, AdminPanelModule panel, PanelSettings settings) {
        this.ctx = ctx;
        this.panel = panel;
        this.settings = settings;
    }

    /** Alt komut verilmezse panelin kokunu acar. */
    @Command("")
    public void root(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (!player.hasPermission(settings.permission)) {
            ctx.lang().send(player, "command.no-permission");
            return;
        }
        panel.open(player);
    }

    /** Dogrudan bir bolume gitmek icin: /adminmenu bolum items */
    @Command("bolum")
    public void section(CommandSender sender, @Arg("bolum") String section) {
        if (!(sender instanceof Player player)) return;
        if (!player.hasPermission(settings.permission)) {
            ctx.lang().send(player, "command.no-permission");
            return;
        }
        panel.openSection(player, section);
    }

    /** Aktif modu gosterir; hangi arayuzun kullanildigini tek bakista soyler. */
    @Command("mod")
    public void mode(CommandSender sender) {
        ctx.lang().send(sender, "panel.current-mode",
                net.aethel.core.i18n.LangService.of("mode", panel.mode().name()));
    }
}
