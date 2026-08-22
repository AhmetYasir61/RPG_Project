package net.aethel.core.modules.dialog;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Diyalog test komutu. Diyalog kutusu negatif bosluk fontuyla cizilir; satir
 * kaymasi ya da tasma ancak oyunda gorulur, YAML'e bakarak anlasilmaz.
 */
@Command(value = "dialog", aliases = {"diyalog"}, permission = "aethel.admin.dialogs",
        playerOnly = true, descriptionKey = "dialog.command-description")
public final class DialogCommand {

    private final CoreContext ctx;
    private final DialogModule dialogs;

    public DialogCommand(CoreContext ctx, DialogModule dialogs) {
        this.ctx = ctx;
        this.dialogs = dialogs;
    }

    /** Diyalogu kendi uzerinde baslatir. */
    @Command("test")
    public void start(CommandSender sender, @Arg(value = "id", suggests = "dialog", identifier = true) String id) {
        Player player = (Player) sender;
        if (dialogs.node(id).isEmpty()) {
            ctx.lang().send(player, "dialog.missing", LangService.of("id", id));
            return;
        }
        if (!dialogs.start(player, id)) {
            ctx.lang().send(player, "dialog.start-failed", LangService.of("id", id));
        }
    }

    /** Takilan bir diyalogu kapatir; test sirasinda ekranda kalirsa kurtarir. */
    @Command("dur")
    public void stop(CommandSender sender) {
        Player player = (Player) sender;
        dialogs.stop(player);
        ctx.lang().send(player, "dialog.stopped");
    }

    @Command("yenile")
    public void reload(CommandSender sender) {
        ctx.lang().send(sender, "dialog.reloaded", LangService.of("count", dialogs.reload()));
    }
}
