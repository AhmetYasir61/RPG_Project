package net.aethel.core.modules.party;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Oyuncu parti komutlari. Yonetim panelden yapilir ama oyuncu komutlari normal
 * sekilde chat uzerinden kullanilir (bkz. docs/DESIGN.md).
 */
@Command(value = "parti", aliases = {"party"}, playerOnly = true,
        descriptionKey = "party.command-description")
public final class PartyCommand {

    private final CoreContext ctx;
    private final PartyModule party;

    public PartyCommand(CoreContext ctx, PartyModule party) {
        this.ctx = ctx;
        this.party = party;
    }

    @Command("")
    public void info(CommandSender sender) {
        Player player = (Player) sender;
        party.partyOf(player.getUniqueId()).ifPresentOrElse(found -> {
            ctx.lang().send(player, "party.header",
                    LangService.of("count", found.members().size()));
            found.members().forEach(member -> {
                var online = ctx.plugin().getServer().getPlayer(member);
                ctx.lang().send(player, "party.member",
                        LangService.of("player", online == null ? "?" : online.getName()),
                        LangService.of("status", online == null ? "cevrimdisi" : "cevrimici"));
            });
        }, () -> ctx.lang().send(player, "party.none"));
    }

    @Command("davet")
    public void invite(CommandSender sender, @Arg("oyuncu") Player target) {
        Player player = (Player) sender;
        boolean ok = party.invite(player, target);
        ctx.lang().send(player, ok ? "party.invite-sent" : "party.invite-failed",
                LangService.of("player", target.getName()));
    }

    @Command("kabul")
    public void accept(CommandSender sender) {
        Player player = (Player) sender;
        boolean ok = party.accept(player, null);
        if (!ok) ctx.lang().send(player, "party.no-invite");
    }

    @Command("ayril")
    public void leave(CommandSender sender) {
        party.leave((Player) sender);
    }

    /** Uye takibini (saydam kafa isaretleri) acar/kapatir. */
    @Command("takip")
    public void track(CommandSender sender) {
        Player player = (Player) sender;
        boolean enabled = !party.isTracking(player.getUniqueId());
        party.trackMembers(player, enabled);
        ctx.lang().send(player, enabled ? "party.tracking-on" : "party.tracking-off");
    }
}
