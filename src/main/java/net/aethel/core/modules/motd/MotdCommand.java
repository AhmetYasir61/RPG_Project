package net.aethel.core.modules.motd;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;

/** MOTD yonetimi: tanimlari yeniden yukler ve durumu gosterir. */
@Command(value = "motd", permission = "aethel.admin.motd",
        feature = "motd.enabled", descriptionKey = "motd.command-description")
public final class MotdCommand {

    private final CoreContext ctx;
    private final MotdModule motd;

    public MotdCommand(CoreContext ctx, MotdModule motd) {
        this.ctx = ctx;
        this.motd = motd;
    }

    @Command("")
    public void status(CommandSender sender) {
        ctx.lang().send(sender, "motd.status", LangService.of("count", motd.count()));
    }

    /** motd.yml'yi diskten tazeler; sunucuyu yeniden baslatmaya gerek yok. */
    @Command("yenile")
    public void reload(CommandSender sender) {
        ctx.lang().send(sender, "motd.reloaded", LangService.of("count", motd.reload()));
    }
}
